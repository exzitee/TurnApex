use crate::turn::TurnPool;
use anyhow::{anyhow, Result};
use std::net::SocketAddr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

pub const DEFAULT_SOCKS5_PORT: u16 = 9000;

pub struct Socks5Server {
    listen_addr: SocketAddr,
    turn_pool: Arc<TurnPool>,
    is_running: Arc<AtomicBool>,
}

impl Socks5Server {
    pub fn new(listen_addr: SocketAddr, turn_pool: Arc<TurnPool>) -> Self {
        Self {
            listen_addr,
            turn_pool,
            is_running: Arc::new(AtomicBool::new(true)),
        }
    }

    pub async fn start(self: Arc<Self>) -> Result<()> {
        let listener = TcpListener::bind(self.listen_addr).await?;
        tracing::info!("SOCKS5 proxy server listening on {}", self.listen_addr);

        while self.is_running.load(Ordering::Relaxed) {
            match listener.accept().await {
                Ok((socket, client_addr)) => {
                    let pool = self.turn_pool.clone();
                    tokio::spawn(async move {
                        if let Err(e) = Self::handle_connection(socket, pool).await {
                            tracing::debug!("SOCKS5 connection error from {}: {}", client_addr, e);
                        }
                    });
                }
                Err(e) => {
                    tracing::error!("SOCKS5 accept error: {}", e);
                    tokio::time::sleep(std::time::Duration::from_millis(50)).await;
                }
            }
        }

        Ok(())
    }

    async fn handle_connection(mut socket: TcpStream, _turn_pool: Arc<TurnPool>) -> Result<()> {
        // SOCKS5 Greeting: Version (0x05) + NMethods + Methods
        let mut buf = [0u8; 258];
        let n = socket.read(&mut buf).await?;
        if n < 2 || buf[0] != 0x05 {
            return Err(anyhow!("Invalid SOCKS5 version"));
        }

        // Response: Version (0x05) + Method: NO_AUTH (0x00)
        socket.write_all(&[0x05, 0x00]).await?;

        // Request details: VER(5), CMD(1=CONNECT), RSV(0), ATYP
        let n = socket.read(&mut buf).await?;
        if n < 7 || buf[0] != 0x05 {
            return Err(anyhow!("Invalid SOCKS5 request"));
        }

        let cmd = buf[1];
        if cmd != 0x01 {
            // Only CONNECT supported
            socket.write_all(&[0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0]).await?;
            return Err(anyhow!("Unsupported SOCKS5 command: {}", cmd));
        }

        let atyp = buf[3];
        let target_dest = match atyp {
            0x01 => {
                // IPv4
                let ip = std::net::Ipv4Addr::new(buf[4], buf[5], buf[6], buf[7]);
                let port = byteorder::BigEndian::read_u16(&buf[8..10]);
                format!("{}:{}", ip, port)
            }
            0x03 => {
                // Domain
                let len = buf[4] as usize;
                let domain = String::from_utf8_lossy(&buf[5..5 + len]).to_string();
                let port = byteorder::BigEndian::read_u16(&buf[5 + len..7 + len]);
                format!("{}:{}", domain, port)
            }
            _ => {
                socket.write_all(&[0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0]).await?;
                return Err(anyhow!("Unsupported address type"));
            }
        };

        tracing::info!("SOCKS5 forwarding to target: {}", target_dest);

        // Connect directly or via tunnel stream
        let mut target_stream = TcpStream::connect(&target_dest).await?;

        // Send success reply
        socket.write_all(&[0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0]).await?;

        // Bi-directional copy
        let (mut cr, mut cw) = socket.into_split();
        let (mut tr, mut tw) = target_stream.into_split();

        tokio::select! {
            _ = tokio::io::copy(&mut cr, &mut tw) => {},
            _ = tokio::io::copy(&mut tr, &mut cw) => {},
        }

        Ok(())
    }

    pub fn stop(&self) {
        self.is_running.store(false, Ordering::Relaxed);
    }
}
