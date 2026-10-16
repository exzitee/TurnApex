// SPDX-FileCopyrightText: 2026 TurnApex Team
// SPDX-License-Identifier: MIT OR Apache-2.0

use crate::{
    dispatcher::Dispatcher,
    obfs::{ObfsCipher, ObfsMode},
    packet::{PACKET_POOL_PER_WORKER, PacketPool},
    turn::{
        allocation::{TurnAllocation, TurnConnectTarget},
        core::NativeCore,
        endpoint::TurnTransportMode,
    },
    vk::captcha::{CaptchaSolver, VkCaptchaError},
    vk::profiles::Profile,
    worker::Worker,
};
use anyhow::{Result, bail};
use primp::Client;
use std::{
    net::SocketAddr,
    sync::{Arc, RwLock},
    time::Instant,
};
use tokio::net::lookup_host;
use tokio::sync::RwLock as TokioRwLock;
use tokio_util::sync::CancellationToken;

pub const DEFAULT_TURN_SERVERS: &[&str] = &[
    "turn.vk.com:3478",
    "turn-ttl.vk.com:3478",
    "5.255.211.241:3478",
];

pub const WORKERS_PER_SERVER: usize = 6;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TurnPoolMode {
    FAILOVER,  // 1 primary active, N-1 in hot standby
    AGGREGATE, // all N active concurrently, traffic split round-robin
}

impl From<crate::config::TurnPoolMode> for TurnPoolMode {
    fn from(c: crate::config::TurnPoolMode) -> Self {
        match c {
            crate::config::TurnPoolMode::Failover => TurnPoolMode::FAILOVER,
            crate::config::TurnPoolMode::Aggregate => TurnPoolMode::AGGREGATE,
        }
    }
}

pub struct TurnPoolEntry {
    pub server: String,
    pub core: Arc<NativeCore>,
    pub allocation: Arc<TurnAllocation>,
    pub workers: Vec<Worker>,
    pub alive: bool,
    pub latency: u32,
}

pub struct TurnPool {
    pub servers: Arc<RwLock<Vec<TurnPoolEntry>>>,
    pub mode: Arc<RwLock<TurnPoolMode>>,
    pub dispatcher: Dispatcher,
    pub primary_index: Arc<TokioRwLock<usize>>,
    pub rx_channel_rx: Arc<TokioRwLock<tokio::sync::mpsc::Receiver<Vec<u8>>>>,
    pub rx_channel_tx: tokio::sync::mpsc::Sender<Vec<u8>>,
    pub cipher: Arc<ObfsCipher>,
    pub obfs_mode: ObfsMode,
    pub cancel: CancellationToken,
    pub packet_pool: Arc<PacketPool>,
}

impl TurnPool {
    pub async fn new(
        servers: Vec<String>,
        mode: TurnPoolMode,
        obfs_cipher: Arc<ObfsCipher>,
        obfs_mode: ObfsMode,
        username: &str,
        password: &str,
        peer_addr: SocketAddr,
        cancel: CancellationToken,
    ) -> Result<Self> {
        Self::create(
            servers,
            mode,
            obfs_cipher,
            obfs_mode,
            username,
            password,
            peer_addr,
            cancel,
        )
        .await
    }

    pub async fn create(
        servers: Vec<String>,
        mode: TurnPoolMode,
        obfs_cipher: Arc<ObfsCipher>,
        obfs_mode: ObfsMode,
        username: &str,
        password: &str,
        peer_addr: SocketAddr,
        cancel: CancellationToken,
    ) -> Result<Self> {
        let dispatcher = Dispatcher::new();
        let mut entries = Vec::new();
        let target_servers = if servers.is_empty() {
            DEFAULT_TURN_SERVERS.iter().map(|s| s.to_string()).collect()
        } else {
            servers
        };

        let mut resolved_targets = Vec::new();
        for s in &target_servers {
            resolved_targets.push(s.clone());
            if s.starts_with("turn.vk.com") {
                if let Ok(iter) = lookup_host(s).await {
                    for addr in iter {
                        let ip_spec = addr.to_string();
                        if !resolved_targets.contains(&ip_spec) {
                            resolved_targets.push(ip_spec);
                        }
                    }
                }
            }
        }

        let mut worker_id = 0;
        let mut all_active_senders = Vec::new();

        for server_str in resolved_targets {
            let socket_addr = match lookup_host(&server_str).await {
                Ok(mut iter) => match iter.next() {
                    Some(sa) => sa,
                    None => continue,
                },
                Err(_) => continue,
            };

            let core = match NativeCore::create(socket_addr, username, password, peer_addr) {
                Ok(c) => c,
                Err(err) => {
                    crate::log_error!("[TURN POOL] NativeCore create failed for {server_str}: {err}");
                    continue;
                }
            };

            let target = TurnConnectTarget {
                address: &server_str,
                override_host: None,
                override_port: None,
                transport_mode: TurnTransportMode::Udp,
            };

            let pool = PacketPool::new(PACKET_POOL_PER_WORKER * WORKERS_PER_SERVER);
            let allocation = match TurnAllocation::connect(
                target,
                Arc::from(username),
                Arc::from(password),
                peer_addr,
                pool,
            )
            .await
            {
                Ok(alloc) => {
                    let _ = alloc.prepare_channel().await;
                    alloc
                }
                Err(err) => {
                    crate::log_error!("[TURN POOL] Allocation failed for {server_str}: {err}");
                    continue;
                }
            };

            let mut workers = Vec::new();
            for _ in 0..WORKERS_PER_SERVER {
                let (w, tx) = Worker::new(
                    worker_id,
                    allocation.clone(),
                    obfs_cipher.clone(),
                    obfs_mode,
                    cancel.clone(),
                );
                workers.push(w);
                all_active_senders.push(tx);
                worker_id += 1;
            }

            entries.push(TurnPoolEntry {
                server: server_str,
                core,
                allocation,
                workers,
                alive: true,
                latency: 20,
            });
        }

        if entries.is_empty() {
            bail!("Failed to allocate any TURN servers in pool");
        }

        match mode {
            TurnPoolMode::AGGREGATE => {
                dispatcher.set_workers(all_active_senders);
            }
            TurnPoolMode::FAILOVER => {
                let primary_senders = entries[0].workers.iter().map(|w| w.sender.clone()).collect();
                dispatcher.set_workers(primary_senders);
            }
        }

        let (rx_channel_tx, rx_channel_rx) = tokio::sync::mpsc::channel(1024);
        let packet_pool = PacketPool::new(2048);

        Ok(Self {
            servers: Arc::new(RwLock::new(entries)),
            mode: Arc::new(RwLock::new(mode)),
            dispatcher,
            primary_index: Arc::new(TokioRwLock::new(0)),
            rx_channel_rx: Arc::new(TokioRwLock::new(rx_channel_rx)),
            rx_channel_tx,
            cipher: obfs_cipher,
            obfs_mode,
            cancel,
            packet_pool,
        })
    }

    pub fn set_mode(&self, mode: impl Into<TurnPoolMode>) {
        let m = mode.into();
        {
            let mut guard = self.mode.write().unwrap();
            *guard = m;
        }
        self.refresh_dispatcher_workers();
    }

    pub fn stop(&self) {
        self.cancel.cancel();
    }

    pub async fn send_packet(&self, data: &[u8]) -> Result<()> {
        let Some(mut packet) = self.packet_pool.try_acquire() else {
            bail!("Packet pool depleted in send_packet");
        };
        packet.set_read_len(data.len())?;
        packet.as_mut_slice().copy_from_slice(data);
        self.dispatcher.dispatch(packet).await
    }

    pub fn refresh_dispatcher_workers(&self) {
        let mode = *self.mode.read().unwrap();
        let servers = self.servers.read().unwrap();

        match mode {
            TurnPoolMode::AGGREGATE => {
                let mut senders = Vec::new();
                for entry in servers.iter() {
                    if entry.alive {
                        for w in &entry.workers {
                            senders.push(w.sender.clone());
                        }
                    }
                }
                self.dispatcher.set_workers(senders);
            }
            TurnPoolMode::FAILOVER => {
                let primary_idx = *self.primary_index.blocking_read();
                if let Some(primary) = servers.get(primary_idx) {
                    if primary.alive {
                        let senders = primary.workers.iter().map(|w| w.sender.clone()).collect();
                        self.dispatcher.set_workers(senders);
                        return;
                    }
                }
                drop(servers);
                let _ = self.failover_to_fastest_backup();
            }
        }
    }

    pub fn failover_to_fastest_backup(&self) -> bool {
        let mut best_idx = None;
        let mut best_latency = u32::MAX;

        let servers = self.servers.read().unwrap();
        for (idx, entry) in servers.iter().enumerate() {
            if entry.alive && entry.latency < best_latency {
                best_latency = entry.latency;
                best_idx = Some(idx);
            }
        }

        if let Some(idx) = best_idx {
            {
                let mut p = self.primary_index.blocking_write();
                *p = idx;
            }
            let senders = servers[idx].workers.iter().map(|w| w.sender.clone()).collect();
            self.dispatcher.set_workers(senders);
            crate::log_output!(
                "[TURN POOL] Failover switched to {} (latency: {}ms)",
                servers[idx].server,
                best_latency
            );
            true
        } else {
            crate::log_error!("[TURN POOL] Failover failed: no alive backup TURN servers!");
            false
        }
    }

    pub async fn trigger_failover(&self) -> bool {
        self.failover_to_fastest_backup()
    }

    pub async fn check_health_and_failover(&self) {
        let entries = {
            let guard = self.servers.read().unwrap();
            guard
                .iter()
                .map(|e| (e.server.clone(), e.allocation.clone()))
                .collect::<Vec<_>>()
        };

        for (idx, (_, alloc)) in entries.into_iter().enumerate() {
            let start = Instant::now();
            let ping_ok = alloc.ping().await.is_ok();
            let latency = start.elapsed().as_millis().min(u32::MAX as u128) as u32;

            let mut guard = self.servers.write().unwrap();
            if let Some(entry) = guard.get_mut(idx) {
                entry.latency = latency;
                entry.alive = ping_ok;
            }
        }

        self.refresh_dispatcher_workers();
    }

    pub async fn handle_captcha(
        solver: &CaptchaSolver,
        client: &Client,
        profile: &Profile,
        stream_id: usize,
        error: &VkCaptchaError,
    ) -> Result<String> {
        solver.solve(client, profile, stream_id, error).await
    }
}
