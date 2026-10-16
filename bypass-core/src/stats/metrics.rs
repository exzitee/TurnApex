use crate::health::TargetProbeStatus;
use serde::{Deserialize, Serialize};
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::RwLock;

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
        let lat = self.latency_ms.load(Ordering::Relaxed);
        let targets = self.target_status.read().await.clone();

        // Convert bits per second to Mbps (1 Mbps = 1,000,000 bits)
        let rx_mbps = (rx_rate as f64 * 8.0) / 1_000_000.0;
        let tx_mbps = (tx_rate as f64 * 8.0) / 1_000_000.0;

        TrafficStatsSnapshot {
            rx_bytes: rx_b,
            tx_bytes: tx_b,
            rx_mbps: (rx_mbps * 10.0).round() / 10.0,
            tx_mbps: (tx_mbps * 10.0).round() / 10.0,
            latency_ms: lat,
            targets,
        }
    }

    /// Background task that computes 1-second rolling rates
    pub fn start_rate_calculator(self: Arc<Self>) {
        tokio::spawn(async move {
            let mut interval = tokio::time::interval(Duration::from_secs(1));
            let mut last_rx = self.rx_bytes.load(Ordering::Relaxed);
            let mut last_tx = self.tx_bytes.load(Ordering::Relaxed);

            while self.is_running.load(Ordering::Relaxed) {
                interval.tick().await;

                let cur_rx = self.rx_bytes.load(Ordering::Relaxed);
                let cur_tx = self.tx_bytes.load(Ordering::Relaxed);

                let delta_rx = cur_rx.saturating_sub(last_rx);
                let delta_tx = cur_tx.saturating_sub(last_tx);

                self.rx_rate_bps.store(delta_rx, Ordering::Relaxed);
                self.tx_rate_bps.store(delta_tx, Ordering::Relaxed);

                last_rx = cur_rx;
                last_tx = cur_tx;
            }
        });
    }

    pub fn stop(&self) {
        self.is_running.store(false, Ordering::Relaxed);
    }
}
