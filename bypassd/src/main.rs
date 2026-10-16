mod deploy;
mod server;

use anyhow::Result;
use clap::Parser;
use server::BypassdServer;
use std::net::SocketAddr;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[derive(Parser, Debug)]
#[command(author, version, about = "VPS Daemon for VK TURN-based censorship bypass engine", long_about = None)]
struct Args {
    /// UDP listen address for incoming TURN packets from client
    #[arg(short, long, default_value = "0.0.0.0:56000")]
    listen: SocketAddr,

    /// Target WireGuard endpoint to relay unmasked traffic to
    #[arg(short, long, default_value = "127.0.0.1:51820")]
    wireguard_target: SocketAddr,

    /// Pre-shared authentication key for DTLS encryption
    #[arg(long, default_value = "vk-turn-bypass-secret-v1")]
    psk: String,

    /// Automatically configure and install WireGuard, UFW firewall, and systemd service
    #[arg(long)]
    auto_deploy: bool,
}

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::registry()
        .with(tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into()))
        .with(tracing_subscriber::fmt::layer())
        .init();

    let args = Args::parse();

    if args.auto_deploy {
        deploy::run_auto_deploy(args.listen.port(), args.wireguard_target.port(), &args.psk)?;
        return Ok(());
    }

    let server = BypassdServer::new(args.listen, args.wireguard_target, args.psk);
    server.run().await?;

    Ok(())
}
