use crate::crypto::DtlsSession;
use crate::stats::TrafficStats;
use crate::turn::TurnPool;
use anyhow::Result;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

pub const DEFAULT_MTU: usize = 1280;

pub struct TunEngine {
    turn_pool: Arc<TurnPool>,
    dtls: Arc<DtlsSession>,
    stats: Arc<TrafficStats>,
    is_running: Arc<AtomicBool>,
}

impl TunEngine {
    pub fn new(
        turn_pool: Arc<TurnPool>,
        dtls: Arc<DtlsSession>,
        stats: Arc<TrafficStats>,
    ) -> Self {
        Self {
            turn_pool,
            dtls,
            stats,
            is_running: Arc::new(AtomicBool::new(true)),
        }
    }

    /// Android-specific runner that takes the Android VpnService detached file descriptor
    #[cfg(target_os = "android")]
    pub fn start_android_loop(
        self: Arc<Self>,
        tun_fd: i32,
    ) -> Result<()> {
        use std::os::unix::io::FromRawFd;
        let file = unsafe { std::fs::File::from_raw_fd(tun_fd) };
        let mut read_file = file.try_clone()?;
        let mut write_file = file;

        let engine = self.clone();

        // 1. Thread reading from TUN -> TURN
        std::thread::spawn(move || {
            use std::io::Read;
            let mut buf = [0u8; 4096];
            let rt = tokio::runtime::Handle::current();

            while engine.is_running.load(Ordering::Relaxed) {
                match read_file.read(&mut buf) {
                    Ok(n) if n > 0 => {
                        let packet = &buf[..n];
                        engine.stats.record_tx(n as u64);

                        // Encrypt packet with DTLS
                        if let Ok(encrypted) = engine.dtls.encrypt_record(packet) {
                            let pool = engine.turn_pool.clone();
                            rt.spawn(async move {
                                let _ = pool.send_packet(&encrypted).await;
                            });
                        }
                    }
                    _ => break,
                }
            }
        });

        // 2. Async task receiving from TURN -> writing to TUN
        let engine_rx = self.clone();
        tokio::spawn(async move {
            use std::io::Write;
            let mut rx_guard = engine_rx.turn_pool.rx_channel_rx.write().await;
            if let Some(mut rx) = rx_guard.take() {
                while engine_rx.is_running.load(Ordering::Relaxed) {
                    if let Some(turn_data) = rx.recv().await {
                        // Decrypt DTLS
                        if let Ok(plain_ip) = engine_rx.dtls.decrypt_record(&turn_data) {
                            engine_rx.stats.record_rx(plain_ip.len() as u64);
                            let _ = write_file.write_all(&plain_ip);
                        }
                    }
                }
            }
        });

        Ok(())
    }

    /// Generic packet pump (for testing or simulated desktop/server environments)
    pub async fn start_generic_loop(
        self: Arc<Self>,
        mut outbound_rx: tokio::sync::mpsc::Receiver<Vec<u8>>,
        inbound_tx: tokio::sync::mpsc::Sender<Vec<u8>>,
    ) -> Result<()> {
        let engine_out = self.clone();

        // Outbound pump: plain IP -> DTLS encrypt -> TURN send
        tokio::spawn(async move {
            while engine_out.is_running.load(Ordering::Relaxed) {
                if let Some(ip_pkt) = outbound_rx.recv().await {
                    engine_out.stats.record_tx(ip_pkt.len() as u64);
                    if let Ok(encrypted) = engine_out.dtls.encrypt_record(&ip_pkt) {
                        let _ = engine_out.turn_pool.send_packet(&encrypted).await;
                    }
                }
            }
        });

        // Inbound pump: TURN receive -> DTLS decrypt -> plain IP
        let engine_in = self.clone();
        tokio::spawn(async move {
            let mut rx_guard = engine_in.turn_pool.rx_channel_rx.write().await;
            if let Some(mut rx) = rx_guard.take() {
                while engine_in.is_running.load(Ordering::Relaxed) {
                    if let Some(turn_data) = rx.recv().await {
                        if let Ok(plain_ip) = engine_in.dtls.decrypt_record(&turn_data) {
                            engine_in.stats.record_rx(plain_ip.len() as u64);
                            let _ = inbound_tx.send(plain_ip).await;
                        }
                    }
                }
            }
        });

        Ok(())
    }

    pub fn stop(&self) {
        self.is_running.store(false, Ordering::Relaxed);
    }
}
