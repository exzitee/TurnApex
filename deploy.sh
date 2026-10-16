#!/usr/bin/env bash
# ==============================================================================
# TurnApex VPS Deploy Script
# Automated deployment of WireGuard + bypassd daemon with RTP unmasking
# ==============================================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== [TurnApex VPS Auto-Deployer] ===${NC}"

if [ "$EUID" -ne 0 ]; then
  echo -e "${RED}Error: Please run this script as root (sudo bash deploy.sh)${NC}"
  exit 1
fi

PORT=${1:-56000}
WG_PORT=51820
PSK=${2:-"vk-turn-bypass-secret-v1"}

echo -e "${GREEN}[1/6] Installing dependencies...${NC}"
apt-get update -y
apt-get install -y wireguard wireguard-tools iptables ufw curl tar

echo -e "${GREEN}[2/6] Detecting network and IP...${NC}"
IFACE=$(ip route show default | awk '{print $5}' | head -n1)
PUBLIC_IP=$(curl -s https://api.ipify.org || curl -s https://icanhazip.com || hostname -I | awk '{print $1}')
echo "Network interface: $IFACE, Public IP: $PUBLIC_IP"

echo -e "${GREEN}[3/6] Generating WireGuard Keys...${NC}"
SERVER_PRIVKEY=$(wg genkey)
SERVER_PUBKEY=$(echo "$SERVER_PRIVKEY" | wg pubkey)
CLIENT_PRIVKEY=$(wg genkey)
CLIENT_PUBKEY=$(echo "$CLIENT_PRIVKEY" | wg pubkey)

echo -e "${GREEN}[4/6] Configuring WireGuard (wg0)...${NC}"
mkdir -p /etc/wireguard
cat <<EOF > /etc/wireguard/wg0.conf
[Interface]
Address = 10.8.0.1/24
ListenPort = $WG_PORT
PrivateKey = $SERVER_PRIVKEY
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o $IFACE -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o $IFACE -j MASQUERADE

[Peer]
PublicKey = $CLIENT_PUBKEY
AllowedIPs = 10.8.0.2/32
EOF

chmod 600 /etc/wireguard/wg0.conf

echo "net.ipv4.ip_forward = 1" > /etc/sysctl.d/99-wireguard-forward.conf
sysctl -p /etc/sysctl.d/99-wireguard-forward.conf >/dev/null

systemctl enable --now wg-quick@wg0

echo -e "${GREEN}[5/6] Configuring Firewall (UFW)...${NC}"
ufw allow 22/tcp
ufw allow $PORT/udp
ufw --force enable

echo -e "${GREEN}[6/6] Installing bypassd service...${NC}"
if [ ! -f "/usr/local/bin/bypassd" ]; then
    echo "Downloading latest bypassd binary release..."
    # Fallback to local build if cargo exists
    if command -v cargo &>/dev/null; then
        cargo build --release -p bypassd
        cp target/release/bypassd /usr/local/bin/
    else
        echo "Note: Place compiled 'bypassd' into /usr/local/bin/bypassd"
    fi
fi

cat <<EOF > /etc/systemd/system/bypassd.service
[Unit]
Description=VK TURN Bypass VPS Daemon
After=network.target wg-quick@wg0.service

[Service]
Type=simple
ExecStart=/usr/local/bin/bypassd --listen 0.0.0.0:$PORT --wireguard-target 127.0.0.1:$WG_PORT --psk $PSK
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now bypassd || true

echo -e "\n${BLUE}================================================================${NC}"
echo -e "${GREEN}✓ VPS Deployment Finished!${NC}"
echo -e "${BLUE}================================================================${NC}"
echo -e "Copy this JSON into your Android app or client config:\n"

cat <<EOF
{
  "vps": {
    "ip": "$PUBLIC_IP",
    "port": $PORT,
    "psk": "$PSK"
  },
  "turn_servers": [
    "turn.vk.com:3478",
    "turn-ttl.vk.com:3478",
    "5.255.211.241:3478"
  ],
  "mode": "aggregate",
  "rtp_masking": true,
  "split_tunnel": {
    "mode": "list",
    "tunnel_apps": ["org.telegram.messenger", "com.google.android.youtube", "com.zhiliaoapp.musically"],
    "direct_domains": ["sberbank.ru", "gosuslugi.ru"]
  },
  "health_interval_sec": 3,
  "failover_timeout_sec": 3,
  "mtu": 1280,
  "n_streams": 4,
  "profiles": ["home", "work", "parents"]
}
EOF
echo -e "\n${BLUE}================================================================${NC}"
