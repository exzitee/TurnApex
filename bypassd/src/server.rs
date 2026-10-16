use anyhow::Result;
use bypass_core::crypto::DtlsSession;
use bypass_core::rtp::RtpMasker;
use bypass_core::turn::stun::{decode_channel_data, is_channel_data, StunMessage, ATTR_DATA, DATA_INDICATION};
use std::collections::HashMap;
use std::net::SocketAddr;
use std::sync::Arc;
use std::time::Instant;
use tokio::net::UdpSocket;
use tokio::sync::RwLock;

pub struct ClientSession {
    pub client_addr: SocketAddr,
    pub dtls: Arc<DtlsSession>,
    pub rtp_masker: Arc<RtpMasker>,
    pub last_seen: Instant,
}

pub struct BypassdServer {
    pub listen_addr: SocketAddr,
    pub wg_target_addr: SocketAddr,
    pub psk: String,
    sessions: Arc<RwLock<HashMap<SocketAddr, Arc<ClientSession>>>>,
}

impl BypassdServer {
    pub fn new(listen_addr: SocketAddr, wg_target_addr: SocketAddr, psk: String) -> Self {
        Self {
            listen_addr,
            wg_target_addr,
            psk,
            sessions: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    pub async fn run(&self) -> Result<()> {
        let incoming_sock = Arc::new(UdpSocket::bind(self.listen_addr).await?);
        let wg_sock = Arc::new(UdpSocket::bind("127.0.0.1:0").await?);

        tracing::info!(
            "bypassd VPS server listening on UDP {}, forwarding to WireGuard {}",
            self.listen_addr,
            self.wg_target_addr
        );

        // Map to keep track of the last client for WireGuard return packets
        let last_client = Arc::new(RwLock::new(Option::<SocketAddr>::None));

        // 1. Task: Incoming from client / TURN -> Decapsulate -> Forward to WireGuard
        let in_sock = incoming_sock.clone();
        let out_wg = wg_sock.clone();
        let sessions_map = self.sessions.clone();
        let last_client_ref = last_client.clone();
        let psk_clone = self.psk.clone();
        let target_wg = self.wg_target_addr;

        tokio::spawn(async move {
            let mut buf = vec![0u8; 65535];
            loop {
                match in_sock.recv_from(&mut buf).await {
                    Ok((len, peer_addr)) => {
                        let raw = &buf[..len];

                        // Unwrap potential TURN framing (ChannelData or DataIndication)
                        let payload = if is_channel_data(raw) {
                            if let Some((_, p)) = decode_channel_data(raw) {
                                p
                            } else {
                                continue;
                            }
                        } else if let Some(msg) = StunMessage::decode(raw) {
                            if msg.msg_type == DATA_INDICATION {
                                if let Some(attr) = msg.get_attribute(ATTR_DATA) {
                                    attr.value.clone()
                                } else {
                                    continue;
                                }
                            } else {
                                continue;
                            }
                        } else {
                            raw.to_vec()
                        };

                        // Strip RTP mask
                        let dtls_payload = bypass_core::rtp::rtp_decode(&payload);

                        // Find or create session
                        let session = {
                            let mut map = sessions_map.write().await;
                            map.entry(peer_addr)
                                .or_insert_with(|| {
                                    Arc::new(ClientSession {
                                        client_addr: peer_addr,
                                        dtls: Arc::new(DtlsSession::new_with_psk(&psk_clone)),
                                        rtp_masker: Arc::new(RtpMasker::default()),
                                        last_seen: Instant::now(),
                                    })
                                })
                                .clone()
                        };

                        *last_client_ref.write().await = Some(peer_addr);

                        // Decrypt DTLS record
                        match session.dtls.decrypt_record(&dtls_payload) {
                            Ok(plain_wireguard_pkt) => {
                                let _ = out_wg.send_to(&plain_wireguard_pkt, target_wg).await;
                            }
                            Err(e) => {
                                tracing::debug!("DTLS decrypt failed from {}: {}", peer_addr, e);
                            }
                        }
                    }
                    Err(e) => {
                        tracing::error!("Error reading incoming UDP packet: {}", e);
                    }
                }
            }
        });

        // 2. Task: Outbound from WireGuard -> Encapsulate (DTLS + RTP) -> Return to client via TURN
        let return_wg = wg_sock.clone();
        let return_in = incoming_sock.clone();
        let sessions_return = self.sessions.clone();

        loop {
            let mut ret_buf = vec![0u8; 65535];
            match return_wg.recv_from(&mut ret_buf).await {
                Ok((len, _)) => {
                    let plain_wg_pkt = &ret_buf[..len];

                    let target_peer = {
                        let guard = last_client.read().await;
                        *guard
                    };

                    if let Some(peer_addr) = target_peer {
                        let session_opt = {
                            let map = sessions_return.read().await;
                            map.get(&peer_addr).cloned()
                        };

                        if let Some(session) = session_opt {
                            if let Ok(encrypted_dtls) = session.dtls.encrypt_record(plain_wg_pkt) {
                                session.rtp_masker.check_ssrc_rotation().await;
                                let rtp_packet = session.rtp_masker.encode(&encrypted_dtls);
                                let _ = return_in.send_to(&rtp_packet, peer_addr).await;
                            }
                        }
                    }
                }
                Err(e) => {
                    tracing::error!("Error reading from WireGuard response socket: {}", e);
                }
            }
        }
    }
}
