use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum TurnPoolMode {
    Failover,
    Aggregate,
}

impl Default for TurnPoolMode {
    fn default() -> Self {
        TurnPoolMode::Aggregate
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum HashMode {
    Auto,
    Manual,
}

impl Default for HashMode {
    fn default() -> Self {
        HashMode::Auto
    }
}

pub fn mask_vk_hash(hash: &str) -> String {
    let clean = hash.trim();
    if clean.len() > 8 {
        format!("{}...{}", &clean[..4], &clean[clean.len() - 4..])
    } else if !clean.is_empty() {
        "****".to_string()
    } else {
        "—".to_string()
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum TunnelMode {
    All,
    List,
    Except,
}

impl Default for TunnelMode {
    fn default() -> Self {
        TunnelMode::List
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SplitTunnelConfig {
    pub mode: TunnelMode,
    #[serde(default = "default_tunnel_apps")]
    pub tunnel_apps: Vec<String>,
    #[serde(default = "default_direct_apps")]
    pub direct_apps: Vec<String>,
    #[serde(default = "default_tunnel_domains")]
    pub tunnel_domains: Vec<String>,
    #[serde(default = "default_direct_domains")]
    pub direct_domains: Vec<String>,
}

fn default_tunnel_apps() -> Vec<String> {
    vec![
        "org.telegram.messenger".to_string(),
        "com.google.android.youtube".to_string(),
        "com.zhiliaoapp.musically".to_string(), // TikTok
        "com.instagram.android".to_string(),
        "com.twitter.android".to_string(),
    ]
}

fn default_direct_apps() -> Vec<String> {
    vec![
        "ru.sberbankmobile".to_string(),
        "ru.tinkoff.activities".to_string(),
        "ru.gosuslugi.portal".to_string(),
        "ru.vtb24.mobilebanking".to_string(),
        "ru.yandex.searchplugin".to_string(),
    ]
}

fn default_tunnel_domains() -> Vec<String> {
    vec![
        "telegram.org".to_string(),
        "t.me".to_string(),
        "youtube.com".to_string(),
        "googlevideo.com".to_string(),
        "instagram.com".to_string(),
        "twitter.com".to_string(),
        "x.com".to_string(),
    ]
}

fn default_direct_domains() -> Vec<String> {
    vec![
        "sberbank.ru".to_string(),
        "gosuslugi.ru".to_string(),
        "tinkoff.ru".to_string(),
        "vtb.ru".to_string(),
        "yandex.ru".to_string(),
        "vk.com".to_string(),
    ]
}

impl Default for SplitTunnelConfig {
    fn default() -> Self {
        Self {
            mode: TunnelMode::List,
            tunnel_apps: default_tunnel_apps(),
            direct_apps: default_direct_apps(),
            tunnel_domains: default_tunnel_domains(),
            direct_domains: default_direct_domains(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VpsConfig {
    pub ip: String,
    pub port: u16,
    #[serde(default)]
    pub ssh_user: Option<String>,
    #[serde(default)]
    pub ssh_key: Option<String>,
    #[serde(default)]
    pub ssh_password: Option<String>,
    #[serde(default = "default_psk")]
    pub psk: String,
}

fn default_psk() -> String {
    "vk-turn-bypass-secret-v1".to_string()
}

impl Default for VpsConfig {
    fn default() -> Self {
        Self {
            ip: "127.0.0.1".to_string(),
            port: 56000,
            ssh_user: Some("root".to_string()),
            ssh_key: None,
            ssh_password: None,
            psk: default_psk(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppConfig {
    pub vps: VpsConfig,
    #[serde(default = "default_turn_servers")]
    pub turn_servers: Vec<String>,
    #[serde(default)]
    pub mode: TurnPoolMode,
    #[serde(default = "default_true")]
    pub rtp_masking: bool,
    #[serde(default)]
    pub split_tunnel: SplitTunnelConfig,
    #[serde(default = "default_health_interval")]
    pub health_interval_sec: u64,
    #[serde(default = "default_failover_timeout")]
    pub failover_timeout_sec: u64,
    #[serde(default = "default_mtu")]
    pub mtu: u16,
    #[serde(default)]
    pub hash_mode: HashMode,
    #[serde(default)]
    pub vk_hashes: Vec<String>,
    #[serde(default)]
    pub app_routes: std::collections::HashMap<String, String>,
    #[serde(default = "default_n_streams")]
    pub n_streams: usize,
    #[serde(default = "default_profiles")]
    pub profiles: Vec<String>,
}

fn default_true() -> bool {
    true
}

fn default_turn_servers() -> Vec<String> {
    vec![
        "turn.vk.com:3478".to_string(),
        "turn-ttl.vk.com:3478".to_string(),
        "5.255.211.241:3478".to_string(),
    ]
}

fn default_health_interval() -> u64 {
    3
}

fn default_failover_timeout() -> u64 {
    3
}

fn default_mtu() -> u16 {
    1280
}

fn default_n_streams() -> usize {
    4
}

fn default_profiles() -> Vec<String> {
    vec![
        "home".to_string(),
        "work".to_string(),
        "parents".to_string(),
    ]
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            vps: VpsConfig::default(),
            turn_servers: default_turn_servers(),
            mode: TurnPoolMode::Aggregate,
            rtp_masking: true,
            split_tunnel: SplitTunnelConfig::default(),
            health_interval_sec: default_health_interval(),
            failover_timeout_sec: default_failover_timeout(),
            mtu: default_mtu(),
            hash_mode: HashMode::default(),
            vk_hashes: Vec::new(),
            app_routes: std::collections::HashMap::new(),
            n_streams: default_n_streams(),
            profiles: default_profiles(),
        }
    }
}

impl AppConfig {
    /// Preset: 'Для родителей' - Maximum simplicity and reliability (Failover mode, All traffic through tunnel)
    pub fn for_parents(vps_ip: &str, vps_port: u16, psk: &str) -> Self {
        let mut cfg = Self::default();
        cfg.vps.ip = vps_ip.to_string();
        cfg.vps.port = vps_port;
        cfg.vps.psk = psk.to_string();
        cfg.mode = TurnPoolMode::Failover;
        cfg.split_tunnel.mode = TunnelMode::All;
        cfg.rtp_masking = true;
        cfg
    }

    /// Preset: 'Aggregate' - Maximum speed (Aggregates multiple VK TURN channels round-robin)
    pub fn for_aggregate(vps_ip: &str, vps_port: u16, psk: &str) -> Self {
        let mut cfg = Self::default();
        cfg.vps.ip = vps_ip.to_string();
        cfg.vps.port = vps_port;
        cfg.vps.psk = psk.to_string();
        cfg.mode = TurnPoolMode::Aggregate;
        cfg.split_tunnel.mode = TunnelMode::List;
        cfg.rtp_masking = true;
        cfg
    }

    pub fn to_json(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string_pretty(self)
    }

    pub fn from_json(json_str: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(json_str)
    }
}
