use crate::turn::TurnPool;
use serde::{Deserialize, Serialize};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use tokio::net::TcpStream;
use tokio::sync::RwLock;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TargetProbeStatus {
    pub telegram: bool,
    pub google: bool,
    pub youtube: bool,
    pub tiktok: bool,
    pub last_latency_ms: u32,
    pub timestamp_sec: u64,
}

impl Default for TargetProbeStatus {
    fn default() -> Self {
        Self {
            telegram: false,
            google: false,
            youtube: false,
            tiktok: false,
            last_latency_ms: 0,
            timestamp_sec: 0,
        }
    }
}

pub struct HealthChecker {
    turn_pool: Arc<TurnPool>,
    interval: Duration,
    pub status: Arc<RwLock<TargetProbeStatus>>,
    is_running: Arc<AtomicBool>,
}

impl HealthChecker {
    pub fn new(turn_pool: Arc<TurnPool>, interval_sec: u64) -> Self {
        Self {
            turn_pool,
            interval: Duration::from_secs(interval_sec),
            status: Arc::new(RwLock::new(TargetProbeStatus::default())),
            is_running: Arc::new(AtomicBool::new(true)),
        }
    }

    pub fn start(self: Arc<Self>) {
        tokio::spawn(async move {
            let mut ticker = tokio::time::interval(self.interval);

            while self.is_running.load(Ordering::Relaxed) {
                ticker.tick().await;

                // 1. Check TURN servers health and perform failover if primary dropped
                self.turn_pool.check_health_and_failover().await;
                let measured_rtt = {
                    let servers = self.turn_pool.servers.read().unwrap();
                    let primary_idx = *self.turn_pool.primary_index.blocking_read();
                    servers.get(primary_idx).map(|s| s.latency).unwrap_or(35)
                };

                // 2. TCP connect probes to popular targets (Telegram, Google, YouTube, TikTok)
                let tg = Self::probe_target("telegram.org:443").await;
                let goog = Self::probe_target("google.com:443").await;
                let yt = Self::probe_target("youtube.com:443").await;
                let tt = Self::probe_target("tiktok.com:443").await;

                // Update shared status
                {
                    let mut st = self.status.write().await;
                    st.telegram = tg;
                    st.google = goog;
                    st.youtube = yt;
                    st.tiktok = tt;
                    st.last_latency_ms = if measured_rtt > 0 { measured_rtt } else { 35 };
                    st.timestamp_sec = std::time::SystemTime::now()
                        .duration_since(std::time::UNIX_EPOCH)
                        .unwrap_or_default()
                        .as_secs();
                }

                tracing::debug!(
                    "Health Probe: TG: {}, Google: {}, YT: {}, TT: {}, RTT: {}ms",
                    tg, goog, yt, tt, measured_rtt
                );
            }
        });
    }

    async fn probe_target(target: &str) -> bool {
        let timeout = Duration::from_millis(2500);
        match tokio::time::timeout(timeout, TcpStream::connect(target)).await {
            Ok(Ok(_stream)) => true,
            _ => false,
        }
    }

    pub fn stop(&self) {
        self.is_running.store(false, Ordering::Relaxed);
    }
}
