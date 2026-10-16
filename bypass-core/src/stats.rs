// SPDX-FileCopyrightText: 2026 amurcanov & TurnApex Team
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

use crate::{
    client_perf::{self, Stage as PerfStage},
    events::Events,
    health::TargetProbeStatus,
};
use serde::{Deserialize, Serialize};
use std::sync::{
    Arc,
    atomic::{AtomicBool, AtomicI32, AtomicI64, AtomicU32, AtomicU64, Ordering},
};
use std::time::Duration;
use tokio::sync::RwLock;
use tokio_util::sync::CancellationToken;

#[derive(Default)]
pub struct Stats {
    pub total_bytes_up: AtomicI64,
    pub total_bytes_down: AtomicI64,
    pub active_connections: AtomicI32,
}

impl Stats {
    pub async fn run(self: Arc<Self>, events: Events, cancel: CancellationToken) {
        let mut interval = tokio::time::interval(std::time::Duration::from_secs(3));
        interval.tick().await;
        loop {
            tokio::select! {
                _ = cancel.cancelled() => return,
                _ = interval.tick() => {
                    client_perf::measure(PerfStage::StatsEmit, || {
                        let active = self.active_connections.load(Ordering::Relaxed);
                        let up = self.total_bytes_up.load(Ordering::Relaxed);
                        let down = self.total_bytes_down.load(Ordering::Relaxed);
                        let total_mb = (up + down) as f64 / (1024.0 * 1024.0);
                        crate::log_error!("[СТАТИСТИКА] Активных: {active} | Трафик: {total_mb:.2} МБ");
                        events.stats(active, up, down);
                    });
                }
            }
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrafficStatsSnapshot {
    pub rx_bytes: u64,
    pub tx_bytes: u64,
    pub rx_mbps: f64,
    pub tx_mbps: f64,
    pub latency_ms: u32,
    pub targets: TargetProbeStatus,
}

pub struct TrafficStats {
    pub rx_bytes: AtomicU64,
    pub tx_bytes: AtomicU64,
    pub rx_rate_bps: AtomicU64,
    pub tx_rate_bps: AtomicU64,
    pub latency_ms: AtomicU32,
    pub target_status: Arc<RwLock<TargetProbeStatus>>,
    is_running: Arc<AtomicBool>,
}

impl Default for TrafficStats {
    fn default() -> Self {
        Self::new()
    }
}

impl TrafficStats {
    pub fn new() -> Self {
        Self {
            rx_bytes: AtomicU64::new(0),
            tx_bytes: AtomicU64::new(0),
            rx_rate_bps: AtomicU64::new(0),
            tx_rate_bps: AtomicU64::new(0),
            latency_ms: AtomicU32::new(34),
            target_status: Arc::new(RwLock::new(TargetProbeStatus::default())),
            is_running: Arc::new(AtomicBool::new(true)),
        }
    }

    pub fn record_rx(&self, bytes: u64) {
        self.rx_bytes.fetch_add(bytes, Ordering::Relaxed);
    }

    pub fn record_tx(&self, bytes: u64) {
        self.tx_bytes.fetch_add(bytes, Ordering::Relaxed);
    }

    pub fn set_latency(&self, ms: u32) {
        self.latency_ms.store(ms, Ordering::Relaxed);
    }

    pub async fn snapshot(&self) -> TrafficStatsSnapshot {
        let rx_b = self.rx_bytes.load(Ordering::Relaxed);
        let tx_b = self.tx_bytes.load(Ordering::Relaxed);
        let rx_rate = self.rx_rate_bps.load(Ordering::Relaxed);
        let tx_rate = self.tx_rate_bps.load(Ordering::Relaxed);
        let targets = self.target_status.read().await.clone();

        TrafficStatsSnapshot {
            rx_bytes: rx_b,
            tx_bytes: tx_b,
            rx_mbps: (rx_rate as f64 * 8.0) / 1_000_000.0,
            tx_mbps: (tx_rate as f64 * 8.0) / 1_000_000.0,
            latency_ms: self.latency_ms.load(Ordering::Relaxed),
            targets,
        }
    }

    pub fn start_rate_calculator(self: Arc<Self>) {
        let stats = Arc::clone(&self);
        tokio::spawn(async move {
            let mut last_rx = stats.rx_bytes.load(Ordering::Relaxed);
            let mut last_tx = stats.tx_bytes.load(Ordering::Relaxed);

            while stats.is_running.load(Ordering::Relaxed) {
                tokio::time::sleep(Duration::from_secs(1)).await;

                let cur_rx = stats.rx_bytes.load(Ordering::Relaxed);
                let cur_tx = stats.tx_bytes.load(Ordering::Relaxed);

                let diff_rx = cur_rx.saturating_sub(last_rx);
                let diff_tx = cur_tx.saturating_sub(last_tx);

                stats.rx_rate_bps.store(diff_rx, Ordering::Relaxed);
                stats.tx_rate_bps.store(diff_tx, Ordering::Relaxed);

                last_rx = cur_rx;
                last_tx = cur_tx;
            }
        });
    }

    pub fn stop(&self) {
        self.is_running.store(false, Ordering::Relaxed);
    }
}
