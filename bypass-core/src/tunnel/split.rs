use crate::config::{SplitTunnelConfig, TunnelMode};

pub struct SplitTunnelMatcher {
    config: SplitTunnelConfig,
}

impl SplitTunnelMatcher {
    pub fn new(config: SplitTunnelConfig) -> Self {
        Self { config }
    }

    /// Determines if a target domain should be routed through the VK TURN tunnel
    pub fn should_route_domain(&self, domain: &str) -> bool {
        let domain_lower = domain.to_lowercase();

        match self.config.mode {
            TunnelMode::All => {
                // Route everything, except explicit direct domains (e.g. Russian banking / state services)
                !self.matches_any(&domain_lower, &self.config.direct_domains)
            }
            TunnelMode::List => {
                // Route ONLY domains matching the tunnel list
                self.matches_any(&domain_lower, &self.config.tunnel_domains)
            }
            TunnelMode::Except => {
                // Route all except direct domains
                !self.matches_any(&domain_lower, &self.config.direct_domains)
            }
        }
    }

    /// Determines if an app package should be routed
    pub fn should_route_app(&self, package_name: &str) -> bool {
        match self.config.mode {
            TunnelMode::All => {
                !self.config.direct_apps.iter().any(|p| p == package_name)
            }
            TunnelMode::List => {
                self.config.tunnel_apps.iter().any(|p| p == package_name)
            }
            TunnelMode::Except => {
                !self.config.direct_apps.iter().any(|p| p == package_name)
            }
        }
    }

    /// Helper for Android VpnService Builder: returns list of allowed apps (for LIST mode)
    pub fn allowed_apps_for_vpn(&self) -> Option<Vec<String>> {
        match self.config.mode {
            TunnelMode::List => Some(self.config.tunnel_apps.clone()),
            _ => None,
        }
    }

    /// Helper for Android VpnService Builder: returns list of disallowed apps (for EXCEPT or ALL with direct apps)
    pub fn disallowed_apps_for_vpn(&self) -> Option<Vec<String>> {
        match self.config.mode {
            TunnelMode::Except | TunnelMode::All => {
                if !self.config.direct_apps.is_empty() {
                    Some(self.config.direct_apps.clone())
                } else {
                    None
                }
            }
            _ => None,
        }
    }

    fn matches_any(&self, domain: &str, list: &[String]) -> bool {
        for item in list {
            let item_lower = item.to_lowercase();
            if domain == item_lower || domain.ends_with(&format!(".{}", item_lower)) {
                return true;
            }
        }
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_split_tunnel_list_mode() {
        let mut cfg = SplitTunnelConfig::default();
        cfg.mode = TunnelMode::List;
        cfg.tunnel_domains = vec!["telegram.org".to_string(), "youtube.com".to_string()];
        cfg.direct_domains = vec!["sberbank.ru".to_string()];

        let matcher = SplitTunnelMatcher::new(cfg);
        assert!(matcher.should_route_domain("telegram.org"));
        assert!(matcher.should_route_domain("api.telegram.org"));
        assert!(matcher.should_route_domain("youtube.com"));
        assert!(!matcher.should_route_domain("sberbank.ru"));
        assert!(!matcher.should_route_domain("online.sberbank.ru"));
        assert!(!matcher.should_route_domain("yandex.ru"));
    }

    #[test]
    fn test_split_tunnel_except_mode() {
        let mut cfg = SplitTunnelConfig::default();
        cfg.mode = TunnelMode::Except;
        cfg.direct_domains = vec!["sberbank.ru".to_string(), "gosuslugi.ru".to_string()];

        let matcher = SplitTunnelMatcher::new(cfg);
        assert!(matcher.should_route_domain("telegram.org"));
        assert!(matcher.should_route_domain("google.com"));
        assert!(!matcher.should_route_domain("sberbank.ru"));
        assert!(!matcher.should_route_domain("sub.gosuslugi.ru"));
    }
}
