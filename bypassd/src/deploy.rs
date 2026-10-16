use anyhow::{anyhow, Result};
use std::process::Command;

pub struct DeployConfig {
    pub server_ip: String,
    pub listen_port: u16,
    pub wg_port: u16,
    pub psk: String,
    pub server_wg_priv: String,
    pub server_wg_pub: String,
    pub client_wg_priv: String,
    pub client_wg_pub: String,
}

pub fn run_auto_deploy(listen_port: u16, wg_port: u16, psk: &str) -> Result<DeployConfig> {
    println!("=== [Bypass VPS Auto-Deployer] ===");
    println!("1. Updating packages and installing WireGuard & dependencies...");

    let apt_status = Command::new("apt-get")
        .args(["update", "-y"])
        .status()?;
    if !apt_status.success() {
        eprintln!("Warning: apt-get update returned non-zero status");
    }

    let install_status = Command::new("apt-get")
        .args([
            "install",
            "-y",
            "wireguard",
            "wireguard-tools",
            "iptables",
            "ufw",
            "curl",
        ])
        .status()?;
    if !install_status.success() {
        return Err(anyhow!("Failed to install required packages via apt-get"));
    }

    println!("2. Detecting network interface and public IP...");
    let iface = detect_default_interface().unwrap_or_else(|| "eth0".to_string());
    let public_ip = detect_public_ip().unwrap_or_else(|| "127.0.0.1".to_string());
    println!("   Interface: {}, Public IP: {}", iface, public_ip);

    println!("3. Generating WireGuard cryptographic keypairs...");
    let (server_wg_priv, server_wg_pub) = generate_wg_keypair()?;
    let (client_wg_priv, client_wg_pub) = generate_wg_keypair()?;

    println!("4. Writing WireGuard configuration to /etc/wireguard/wg0.conf...");
    let wg0_conf = format!(
        r#"[Interface]
Address = 10.8.0.1/24
ListenPort = {wg_port}
PrivateKey = {server_wg_priv}
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o {iface} -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o {iface} -j MASQUERADE

[Peer]
PublicKey = {client_wg_pub}
AllowedIPs = 10.8.0.2/32
"#
    );
    std::fs::write("/etc/wireguard/wg0.conf", wg0_conf)?;

    println!("5. Enabling IPv4 forwarding...");
    std::fs::write(
        "/etc/sysctl.d/99-wireguard-forward.conf",
        "net.ipv4.ip_forward = 1\nnet.ipv4.conf.all.forwarding = 1\n",
    )?;
    let _ = Command::new("sysctl").arg("-p").arg("/etc/sysctl.d/99-wireguard-forward.conf").status();

    println!("6. Starting WireGuard (wg-quick@wg0)...");
    let _ = Command::new("systemctl").args(["enable", "--now", "wg-quick@wg0"]).status();

    println!("7. Configuring UFW firewall...");
    let _ = Command::new("ufw").args(["allow", "22/tcp"]).status();
    let _ = Command::new("ufw").args(["allow", &format!("{}/udp", listen_port)]).status();
    let _ = Command::new("ufw").args(["--force", "enable"]).status();

    println!("8. Installing bypassd systemd service...");
    let current_exe = std::env::current_exe()?;
    let current_exe_path = current_exe.to_string_lossy();

    let service_file = format!(
        r#"[Unit]
Description=VK TURN Bypass VPS Daemon
After=network.target wg-quick@wg0.service

[Service]
Type=simple
ExecStart={current_exe_path} --listen 0.0.0.0:{listen_port} --wireguard-target 127.0.0.1:{wg_port} --psk {psk}
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
"#
    );
    std::fs::write("/etc/systemd/system/bypassd.service", service_file)?;
    let _ = Command::new("systemctl").arg("daemon-reload").status();
    let _ = Command::new("systemctl").args(["enable", "--now", "bypassd"]).status();

    println!("=== [Auto-Deploy Completed Successfully] ===");

    let deploy_cfg = DeployConfig {
        server_ip: public_ip,
        listen_port,
        wg_port,
        psk: psk.to_string(),
        server_wg_priv,
        server_wg_pub,
        client_wg_priv,
        client_wg_pub,
    };

    print_client_instructions(&deploy_cfg);

    Ok(deploy_cfg)
}

fn generate_wg_keypair() -> Result<(String, String)> {
    let priv_out = Command::new("wg").arg("genkey").output()?;
    let priv_key = String::from_utf8(priv_out.stdout)?.trim().to_string();

    let mut child = Command::new("wg")
        .arg("pubkey")
        .stdin(std::process::Stdio::piped())
        .stdout(std::process::Stdio::piped())
        .spawn()?;

    if let Some(mut stdin) = child.stdin.take() {
        use std::io::Write;
        stdin.write_all(priv_key.as_bytes())?;
    }

    let pub_out = child.wait_with_output()?;
    let pub_key = String::from_utf8(pub_out.stdout)?.trim().to_string();

    Ok((priv_key, pub_key))
}

fn detect_default_interface() -> Option<String> {
    let out = Command::new("ip").args(["route", "show", "default"]).output().ok()?;
    let text = String::from_utf8_lossy(&out.stdout);
    // Typical output: default via 1.2.3.1 dev eth0 proto ...
    let parts: Vec<&str> = text.split_whitespace().collect();
    for i in 0..parts.len() {
        if parts[i] == "dev" && i + 1 < parts.len() {
            return Some(parts[i + 1].to_string());
        }
    }
    None
}

fn detect_public_ip() -> Option<String> {
    let out = Command::new("curl").args(["-s", "https://api.ipify.org"]).output().ok()?;
    let ip = String::from_utf8_lossy(&out.stdout).trim().to_string();
    if !ip.is_empty() {
        Some(ip)
    } else {
        None
    }
}

fn print_client_instructions(cfg: &DeployConfig) {
    println!("\nCopy the following JSON config into your Android App or client config.json:\n");
    let client_json = format!(
        r#"{{
  "vps": {{
    "ip": "{}",
    "port": {},
    "psk": "{}"
  }},
  "turn_servers": [
    "turn.vk.com:3478",
    "turn-ttl.vk.com:3478",
    "5.255.211.241:3478"
  ],
  "mode": "aggregate",
  "rtp_masking": true,
  "split_tunnel": {{
    "mode": "list",
    "tunnel_apps": ["org.telegram.messenger", "com.google.android.youtube", "com.zhiliaoapp.musically"],
    "direct_domains": ["sberbank.ru", "gosuslugi.ru"]
  }},
  "health_interval_sec": 3,
  "failover_timeout_sec": 3,
  "mtu": 1280,
  "n_streams": 4,
  "profiles": ["home", "work", "parents"]
}}"#,
        cfg.server_ip, cfg.listen_port, cfg.psk
    );
    println!("{}\n", client_json);
}
