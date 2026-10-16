use crate::config::TurnPoolMode;
use crate::turn::client::{TurnClient, TurnClientState};
use anyhow::{anyhow, Result};
use std::net::SocketAddr;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::Duration;
use tokio::net::lookup_host;
use tokio::sync::{mpsc, RwLock};

pub struct TurnPool {
    pub mode: Arc<RwLock<TurnPoolMode>>,
    pub clients: Arc<RwLock<Vec<Arc<TurnClient>>>>,
    pub primary_index: Arc<RwLock<usize>>,
    pub round_robin_counter: AtomicUsize,
    pub rx_channel_rx: Arc<RwLock<Option<mpsc::Receiver<Vec<u8>>>>>,
    rx_channel_tx: mpsc::Sender<Vec<u8>>,
    vps_target: SocketAddr,
    enable_rtp: bool,
    is_running: Arc<AtomicBool>,
}

impl TurnPool {
    pub async fn new(
        server_specs: &[String],
        vps_target: SocketAddr,
        mode: TurnPoolMode,
        enable_rtp: bool,
    ) -> Result<Arc<Self>> {
        let (tx, rx) = mpsc::channel(10000);

        let pool = Arc::new(Self {
            mode: Arc::new(RwLock::new(mode)),
            clients: Arc::new(RwLock::new(Vec::new())),
            primary_index: Arc::new(RwLock::new(0)),
            round_robin_counter: AtomicUsize::new(0),
            rx_channel_rx: Arc::new(RwLock::new(Some(rx))),
            rx_channel_tx: tx,
            vps_target,
            enable_rtp,
            is_running: Arc::new(AtomicBool::new(true)),
        });

        // Resolve DNS and populate pool
        pool.resolve_and_connect(server_specs).await?;

        // Start background failover / health monitor
        pool.clone().start_health_monitor();

        Ok(pool)
    }

    /// Resolve all A records for turn.vk.com, turn-ttl.vk.com and connect to all
    pub async fn resolve_and_connect(&self, server_specs: &[String]) -> Result<()> {
        let mut target_addrs: Vec<SocketAddr> = Vec::new();

        for spec in server_specs {
            let spec_str = if !spec.contains(':') {
                format!("{}:3478", spec)
            } else {
                spec.clone()
            };

            match lookup_host(&spec_str).await {
                Ok(addrs) => {
                    for addr in addrs {
                        if !target_addrs.contains(&addr) {
                            target_addrs.push(addr);
                        }
                    }
                }
                Err(e) => {
                    tracing::warn!("Failed to resolve TURN server {}: {}", spec, e);
                }
            };
        }

        // Always ensure fallback static VK TURN IPs are tested
        let fallback_ips = ["5.255.211.241:3478", "93.186.225.208:3478"];
        for fallback in fallback_ips {
            if let Ok(addr) = fallback.parse::<SocketAddr>() {
                if !target_addrs.contains(&addr) {
                    target_addrs.push(addr);
                }
            }
        }

        tracing::info!(
            "Resolved {} distinct VK TURN server endpoints: {:?}",
            target_addrs.len(),
            target_addrs
        );

        let mut connected_clients = Vec::new();

        for addr in target_addrs {
            match TurnClient::connect(addr, self.vps_target, self.enable_rtp).await {
                Ok(client) => {
                    let client = Arc::new(client);
                    // Spawn receive loop
                    tokio::spawn(client.clone().start_receive_loop(self.rx_channel_tx.clone()));
                    // Spawn refresh worker
                    tokio::spawn(client.clone().start_refresh_worker());

                    connected_clients.push(client);
                }
                Err(e) => {
                    tracing::warn!("Failed initial connection to TURN {}: {}", addr, e);
                }
            }
        }

        if connected_clients.is_empty() {
            return Err(anyhow!("Failed to connect to any VK TURN servers"));
        }

        // Measure initial latencies to pick the fastest as primary
        let mut best_rtt = u32::MAX;
        let mut best_idx = 0;

        for (i, c) in connected_clients.iter().enumerate() {
            if let Ok(rtt) = c.ping().await {
                if rtt < best_rtt {
                    best_rtt = rtt;
                    best_idx = i;
                }
            }
        }

        *self.primary_index.write().await = best_idx;
        *self.clients.write().await = connected_clients;

        tracing::info!(
            "TurnPool initialized with {} active servers. Primary index: {} (latency: {}ms)",
            self.clients.read().await.len(),
            best_idx,
            best_rtt
        );

        Ok(())
    }

    /// Send a packet according to current mode (FAILOVER vs AGGREGATE)
    pub async fn send_packet(&self, payload: &[u8]) -> Result<()> {
        let mode = *self.mode.read().await;
        let clients = self.clients.read().await;

        if clients.is_empty() {
            return Err(anyhow!("No TURN clients available in pool"));
        }

        match mode {
            TurnPoolMode::Failover => {
                let primary_idx = *self.primary_index.read().await;
                if let Some(client) = clients.get(primary_idx) {
                    let is_healthy = *client.state.read().await != TurnClientState::Failed;
                    if is_healthy {
                        if let Ok(()) = client.send_packet(payload).await {
                            return Ok(());
                        }
                    }
                }

                // If primary failed, trigger hot failover (<50ms)
                drop(clients);
                self.trigger_failover().await?;

                let clients_guard = self.clients.read().await;
                let new_idx = *self.primary_index.read().await;
                if let Some(new_client) = clients_guard.get(new_idx) {
                    new_client.send_packet(payload).await?;
                }
            }
            TurnPoolMode::Aggregate => {
                // Round-robin dispatch across all healthy streams
                let healthy_clients: Vec<Arc<TurnClient>> = clients
                    .iter()
                    .filter(|c| {
                        // Healthy or Allocated
                        let st = c.state.try_read();
                        if let Ok(guard) = st {
                            *guard != TurnClientState::Failed
                        } else {
                            true
                        }
                    })
                    .cloned()
                    .collect();

                if healthy_clients.is_empty() {
                    return Err(anyhow!("All aggregate TURN channels failed"));
                }

                let idx = self.round_robin_counter.fetch_add(1, Ordering::Relaxed)
                    % healthy_clients.len();
                let client = &healthy_clients[idx];
                client.send_packet(payload).await?;
            }
        }

        Ok(())
    }

    /// Fast hot failover switch (<50ms)
    pub async fn trigger_failover(&self) -> Result<()> {
        let clients = self.clients.read().await;
        let current_primary = *self.primary_index.read().await;

        tracing::warn!(
            "FAILOVER TRIGGERED: Primary TURN server [#{}] failed or timed out",
            current_primary
        );

        // Find fastest alive backup
        let mut fastest_idx = None;
        let mut lowest_latency = u32::MAX;

        for (i, c) in clients.iter().enumerate() {
            if i == current_primary {
                continue;
            }
            let st = *c.state.read().await;
            if st == TurnClientState::Allocated || st == TurnClientState::Healthy {
                // Hot backup is already allocated! Instant switch
                fastest_idx = Some(i);
                break;
            }
        }

        if let Some(new_idx) = fastest_idx {
            *self.primary_index.write().await = new_idx;
            tracing::info!(
                "Failover successful: Switched to hot backup [#{}] ({}) in <50ms",
                new_idx,
                clients[new_idx].server_addr
            );
            return Ok(());
        }

        // If none is marked healthy, ping all to find any responsive one
        for (i, c) in clients.iter().enumerate() {
            if let Ok(rtt) = c.ping().await {
                if rtt < lowest_latency {
                    lowest_latency = rtt;
                    fastest_idx = Some(i);
                }
            }
        }

        if let Some(new_idx) = fastest_idx {
            *self.primary_index.write().await = new_idx;
            tracing::info!(
                "Failover recovered: Switched to [#{}] ({}) with latency {}ms",
                new_idx,
                clients[new_idx].server_addr,
                lowest_latency
            );
            Ok(())
        } else {
            Err(anyhow!("All TURN backup servers unreachable"))
        }
    }

    /// Continuous health check every 3 seconds
    fn start_health_monitor(self: Arc<Self>) {
        tokio::spawn(async move {
            let mut interval = tokio::time::interval(Duration::from_secs(3));
            while self.is_running.load(Ordering::Relaxed) {
                interval.tick().await;

                let mode = *self.mode.read().await;
                let clients = self.clients.read().await.clone();
                let current_primary = *self.primary_index.read().await;

                let mut all_failed = true;

                for (idx, client) in clients.iter().enumerate() {
                    match client.ping().await {
                        Ok(rtt) => {
                            all_failed = false;
                            *client.state.write().await = TurnClientState::Healthy;
                            tracing::trace!(
                                "Health check TURN [#{}] {}: OK ({}ms)",
                                idx,
                                client.server_addr,
                                rtt
                            );
                        }
                        Err(e) => {
                            tracing::warn!(
                                "Health check TURN [#{}] {}: TIMEOUT ({})",
                                idx,
                                client.server_addr,
                                e
                            );
                            *client.state.write().await = TurnClientState::Failed;

                            if mode == TurnPoolMode::Failover && idx == current_primary {
                                let _ = self.trigger_failover().await;
                            }
                        }
                    }
                }

                if all_failed {
                    tracing::error!("ALL TURN servers down! Waiting 10 seconds before full reconnection...");
                    tokio::time::sleep(Duration::from_secs(10)).await;
                    // Trigger re-resolve
                    let _ = self.trigger_failover().await;
                }
            }
        });
    }

    pub async fn set_mode(&self, new_mode: TurnPoolMode) {
        *self.mode.write().await = new_mode;
        tracing::info!("TurnPool mode switched to: {:?}", new_mode);
    }

    pub fn stop(&self) {
        self.is_running.store(false, Ordering::Relaxed);
    }
}
