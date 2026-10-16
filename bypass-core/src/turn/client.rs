use crate::rtp::RtpMasker;
use crate::turn::stun::*;
use anyhow::{anyhow, Result};
use std::net::SocketAddr;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::net::UdpSocket;
use tokio::sync::{mpsc, RwLock};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TurnClientState {
    Disconnected,
    Connecting,
    Allocated,
    Healthy,
    Degraded,
    Failed,
}

pub struct TurnClientMetrics {
    pub latency_ms: u32,
    pub tx_bytes: AtomicU64,
    pub rx_bytes: AtomicU64,
    pub tx_packets: AtomicU64,
    pub rx_packets: AtomicU64,
    pub last_pong: Arc<RwLock<Instant>>,
}

pub struct TurnClient {
    pub server_addr: SocketAddr,
    pub vps_target: SocketAddr,
    pub state: Arc<RwLock<TurnClientState>>,
    pub metrics: Arc<TurnClientMetrics>,
    socket: Arc<UdpSocket>,
    channel_number: u16,
    is_running: Arc<AtomicBool>,
    rtp_masker: Option<Arc<RtpMasker>>,
    relayed_addr: Arc<RwLock<Option<SocketAddr>>>,
}

impl TurnClient {
    pub async fn connect(
        server_addr: SocketAddr,
        vps_target: SocketAddr,
        enable_rtp: bool,
    ) -> Result<Self> {
        let local_addr = "0.0.0.0:0".parse::<SocketAddr>()?;
        let socket = UdpSocket::bind(local_addr).await?;
        let socket = Arc::new(socket);

        let rtp_masker = if enable_rtp {
            Some(Arc::new(RtpMasker::default()))
        } else {
            None
        };

        let client = Self {
            server_addr,
            vps_target,
            state: Arc::new(RwLock::new(TurnClientState::Connecting)),
            metrics: Arc::new(TurnClientMetrics {
                latency_ms: 0,
                tx_bytes: AtomicU64::new(0),
                rx_bytes: AtomicU64::new(0),
                tx_packets: AtomicU64::new(0),
                rx_packets: AtomicU64::new(0),
                last_pong: Arc::new(RwLock::new(Instant::now())),
            }),
            socket,
            channel_number: 0x4001,
            is_running: Arc::new(AtomicBool::new(true)),
            rtp_masker,
            relayed_addr: Arc::new(RwLock::new(None)),
        };

        // Initialize STUN binding and TURN allocate
        client.init_allocation().await?;

        Ok(client)
    }

    /// Measure RTT via STUN Binding Request
    pub async fn ping(&self) -> Result<u32> {
        let tx_id = StunMessage::random_transaction_id();
        let ping_pkt = build_binding_request(tx_id);

        let start = Instant::now();
        self.socket.send_to(&ping_pkt, self.server_addr).await?;

        let mut buf = [0u8; 1500];
        let timeout = tokio::time::sleep(Duration::from_millis(3000));
        tokio::pin!(timeout);

        loop {
            tokio::select! {
                res = self.socket.recv_from(&mut buf) => {
                    let (len, from) = res?;
                    if from == self.server_addr {
                        if let Some(msg) = StunMessage::decode(&buf[..len]) {
                            if msg.msg_type == BINDING_SUCCESS_RESPONSE && msg.transaction_id == tx_id {
                                let elapsed = start.elapsed().as_millis() as u32;
                                *self.metrics.last_pong.write().await = Instant::now();
                                return Ok(elapsed);
                            }
                        }
                    }
                }
                _ = &mut timeout => {
                    return Err(anyhow!("STUN ping timeout"));
                }
            }
        }
    }

    pub async fn init_allocation(&self) -> Result<()> {
        *self.state.write().await = TurnClientState::Connecting;

        // 1. STUN Binding Request
        let ping_rtt = match self.ping().await {
            Ok(rtt) => rtt,
            Err(_) => {
                // Try once more with short delay
                tokio::time::sleep(Duration::from_millis(200)).await;
                self.ping().await.unwrap_or(150)
            }
        };

        // 2. TURN Allocate Request
        let tx_id = StunMessage::random_transaction_id();
        let alloc_req = build_allocate_request(tx_id, 600); // 10 minutes lifetime
        self.socket.send_to(&alloc_req, self.server_addr).await?;

        let mut buf = [0u8; 1500];
        let mut allocated = false;

        let alloc_timeout = tokio::time::sleep(Duration::from_millis(3000));
        tokio::pin!(alloc_timeout);

        tokio::select! {
            res = self.socket.recv_from(&mut buf) => {
                if let Ok((len, from)) = res {
                    if from == self.server_addr {
                        if let Some(msg) = StunMessage::decode(&buf[..len]) {
                            if msg.msg_type == ALLOCATE_SUCCESS_RESPONSE && msg.transaction_id == tx_id {
                                if let Some(relay_addr) = msg.get_xor_address(ATTR_XOR_RELAYED_ADDRESS) {
                                    *self.relayed_addr.write().await = Some(relay_addr);
                                    tracing::info!("TURN Allocated relayed address: {}", relay_addr);
                                }
                                allocated = true;
                            }
                        }
                    }
                }
            }
            _ = &mut alloc_timeout => {
                tracing::warn!("TURN allocate timed out, continuing in direct relay mode");
            }
        }

        // 3. CreatePermission to VPS target
        let perm_tx = StunMessage::random_transaction_id();
        let perm_req = build_create_permission(perm_tx, self.vps_target);
        let _ = self.socket.send_to(&perm_req, self.server_addr).await;

        *self.state.write().await = if allocated {
            TurnClientState::Allocated
        } else {
            TurnClientState::Healthy
        };

        tracing::info!(
            "TURN client connected to {} (rtt: {}ms)",
            self.server_addr,
            ping_rtt
        );

        Ok(())
    }

    /// Send raw IP packet through TURN (with optional RTP masking)
    pub async fn send_packet(&self, payload: &[u8]) -> Result<()> {
        let wire_payload = if let Some(ref masker) = self.rtp_masker {
            masker.check_ssrc_rotation().await;
            masker.encode(payload)
        } else {
            payload.to_vec()
        };

        // Wrap into TURN ChannelData or SendIndication
        let is_allocated = *self.state.read().await == TurnClientState::Allocated;
        let turn_pkt = if is_allocated {
            encode_channel_data(self.channel_number, &wire_payload)
        } else {
            // Send Indication to VPS
            build_send_indication(self.vps_target, &wire_payload)
        };

        self.socket.send_to(&turn_pkt, self.server_addr).await?;

        self.metrics
            .tx_bytes
            .fetch_add(turn_pkt.len() as u64, Ordering::Relaxed);
        self.metrics.tx_packets.fetch_add(1, Ordering::Relaxed);

        Ok(())
    }

    /// Background receive loop dispatching packets to output channel
    pub async fn start_receive_loop(
        self: Arc<Self>,
        output_tx: mpsc::Sender<Vec<u8>>,
    ) -> Result<()> {
        let mut buf = vec![0u8; 65535];

        while self.is_running.load(Ordering::Relaxed) {
            match self.socket.recv_from(&mut buf).await {
                Ok((len, from)) => {
                    if from != self.server_addr {
                        continue;
                    }

                    self.metrics
                        .rx_bytes
                        .fetch_add(len as u64, Ordering::Relaxed);
                    self.metrics.rx_packets.fetch_add(1, Ordering::Relaxed);

                    let raw_slice = &buf[..len];

                    // Check if ChannelData
                    let inner_data = if is_channel_data(raw_slice) {
                        if let Some((_chan, payload)) = decode_channel_data(raw_slice) {
                            payload
                        } else {
                            continue;
                        }
                    } else if let Some(msg) = StunMessage::decode(raw_slice) {
                        if msg.msg_type == DATA_INDICATION {
                            if let Some(attr) = msg.get_attribute(ATTR_DATA) {
                                attr.value.clone()
                            } else {
                                continue;
                            }
                        } else {
                            // Binding or Refresh response
                            continue;
                        }
                    } else {
                        // Direct packet
                        raw_slice.to_vec()
                    };

                    // Check RTP unmasking
                    let packet = if self.rtp_masker.is_some() {
                        crate::rtp::rtp_decode(&inner_data)
                    } else {
                        inner_data
                    };

                    let _ = output_tx.send(packet).await;
                }
                Err(e) => {
                    tracing::error!("Error receiving from TURN socket: {}", e);
                    tokio::time::sleep(Duration::from_millis(50)).await;
                }
            }
        }

        Ok(())
    }

    /// Background refresh worker (runs every 30s)
    pub async fn start_refresh_worker(self: Arc<Self>) {
        let mut interval = tokio::time::interval(Duration::from_secs(30));
        while self.is_running.load(Ordering::Relaxed) {
            interval.tick().await;

            let is_allocated = *self.state.read().await == TurnClientState::Allocated;
            if is_allocated {
                let tx_id = StunMessage::random_transaction_id();
                let refresh_pkt = build_refresh_request(tx_id, 600);
                let _ = self.socket.send_to(&refresh_pkt, self.server_addr).await;

                // Also renew permissions to VPS
                let perm_tx = StunMessage::random_transaction_id();
                let perm_req = build_create_permission(perm_tx, self.vps_target);
                let _ = self.socket.send_to(&perm_req, self.server_addr).await;
            }
        }
    }

    pub fn stop(&self) {
        self.is_running.store(false, Ordering::Relaxed);
    }
}
