use crate::crypto::DtlsSession;
use crate::health::HealthChecker;
use crate::stats::TrafficStats;
use crate::tunnel::tun::TunEngine;
use crate::turn::TurnPool;
use std::sync::Arc;
use tokio::runtime::Runtime;

#[cfg(feature = "android-jni")]
use crate::config::{AppConfig, TurnPoolMode};
#[cfg(feature = "android-jni")]
use crate::obfs::{ObfsCipher, ObfsMode};
#[cfg(feature = "android-jni")]
use std::net::SocketAddr;

pub struct EngineHandle {
    pub runtime: Runtime,
    pub turn_pool: Arc<TurnPool>,
    pub dtls: Arc<DtlsSession>,
    pub health_checker: Arc<HealthChecker>,
    pub stats: Arc<TrafficStats>,
    pub tun_engine: Option<Arc<TunEngine>>,
}

#[cfg(feature = "android-jni")]
pub mod jni_exports {
    use super::*;
    use jni::objects::{JClass, JString};
    use jni::sys::{jint, jlong, jstring};
    use jni::JNIEnv;

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_com_bypass_app_BypassCore_nativeInit(
        mut env: JNIEnv,
        _class: JClass,
        config_json: JString,
    ) -> jlong {
        let json_str: String = match env.get_string(&config_json) {
            Ok(s) => s.into(),
            Err(_) => return 0,
        };

        let config: AppConfig = match AppConfig::from_json(&json_str) {
            Ok(c) => c,
            Err(e) => {
                tracing::error!("Failed to parse config JSON: {}", e);
                return 0;
            }
        };

        let rt = match Runtime::new() {
            Ok(r) => r,
            Err(e) => {
                tracing::error!("Failed to create Tokio runtime: {}", e);
                return 0;
            }
        };

        let vps_addr: SocketAddr = match format!("{}:{}", config.vps.ip, config.vps.port).parse() {
            Ok(a) => a,
            Err(e) => {
                tracing::error!("Invalid VPS address: {}", e);
                return 0;
            }
        };

        let (turn_pool, dtls, health, stats) = rt.block_on(async {
            let key = crate::wrap::derive_wrap_key(&config.vps.psk).unwrap_or([0u8; 32]);
            let cipher = Arc::new(ObfsCipher::new(key).unwrap());
            let obfs_mode = if config.rtp_masking {
                ObfsMode::Video
            } else {
                ObfsMode::Audio
            };

            let pool = Arc::new(
                TurnPool::new(
                    config.turn_servers.clone(),
                    config.mode.into(),
                    cipher,
                    obfs_mode,
                    "user",
                    "pass",
                    vps_addr,
                    tokio_util::sync::CancellationToken::new(),
                )
                .await
                .expect("Failed to create TurnPool"),
            );

            pool.set_hash_mode(config.hash_mode.into());
            if !config.vk_hashes.is_empty() {
                let _ = pool.update_hashes(&config.vk_hashes).await;
            }
            pool.start_hash_watcher();

            let dtls = Arc::new(DtlsSession::new_with_psk(&config.vps.psk));
            let health = Arc::new(HealthChecker::new(pool.clone(), config.health_interval_sec));
            health.clone().start();

            let stats = Arc::new(TrafficStats::new());
            stats.clone().start_rate_calculator();

            (pool, dtls, health, stats)
        });

        let handle = Box::new(EngineHandle {
            runtime: rt,
            turn_pool,
            dtls,
            health_checker: health,
            stats,
            tun_engine: None,
        });

        Box::into_raw(handle) as jlong
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_com_bypass_app_BypassCore_nativeStartTun(
        _env: JNIEnv,
        _class: JClass,
        handle_ptr: jlong,
        _tun_fd: jint,
    ) -> jint {
        if handle_ptr == 0 {
            return -1;
        }

        let handle = unsafe { &mut *(handle_ptr as *mut EngineHandle) };
        let engine = Arc::new(TunEngine::new(
            handle.turn_pool.clone(),
            handle.dtls.clone(),
            handle.stats.clone(),
        ));

        #[cfg(target_os = "android")]
        {
            if let Err(e) = engine.clone().start_android_loop(_tun_fd) {
                tracing::error!("Failed to start Android TUN loop: {}", e);
                return -2;
            }
        }

        handle.tun_engine = Some(engine);
        0
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_com_bypass_app_BypassCore_nativeStop(
        _env: JNIEnv,
        _class: JClass,
        handle_ptr: jlong,
    ) {
        if handle_ptr == 0 {
            return;
        }

        let handle = unsafe { Box::from_raw(handle_ptr as *mut EngineHandle) };
        if let Some(ref engine) = handle.tun_engine {
            engine.stop();
        }
        handle.health_checker.stop();
        handle.turn_pool.stop();
        handle.stats.stop();
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_com_bypass_app_BypassCore_nativeGetStats(
        env: JNIEnv,
        _class: JClass,
        handle_ptr: jlong,
    ) -> jstring {
        if handle_ptr == 0 {
            return env.new_string("{}").unwrap().into_raw();
        }

        let handle = unsafe { &*(handle_ptr as *mut EngineHandle) };
        let snapshot = handle.runtime.block_on(async {
            handle.stats.snapshot().await
        });

        let json = serde_json::to_string(&snapshot).unwrap_or_else(|_| "{}".to_string());
        env.new_string(json).unwrap().into_raw()
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_com_bypass_app_BypassCore_nativeSetMode(
        mut env: JNIEnv,
        _class: JClass,
        handle_ptr: jlong,
        mode_str: JString,
    ) {
        if handle_ptr == 0 {
            return;
        }

        let handle = unsafe { &*(handle_ptr as *mut EngineHandle) };
        if let Ok(m) = env.get_string(&mode_str) {
            let m_str: String = m.into();
            let mode = if m_str.eq_ignore_ascii_case("failover") {
                TurnPoolMode::Failover
            } else {
                TurnPoolMode::Aggregate
            };

            handle.turn_pool.set_mode(mode);
        }
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_com_bypass_app_BypassCore_nativeUpdateHashes(
        mut env: JNIEnv,
        _class: JClass,
        handle_ptr: jlong,
        hashes_str: JString,
    ) -> jint {
        if handle_ptr == 0 {
            return -1;
        }

        let handle = unsafe { &*(handle_ptr as *mut EngineHandle) };
        if let Ok(s) = env.get_string(&hashes_str) {
            let str_val: String = s.into();
            let inputs: Vec<String> = serde_json::from_str(&str_val).unwrap_or_else(|_| vec![str_val]);
            let count = handle.runtime.block_on(async {
                handle.turn_pool.update_hashes(&inputs).await.unwrap_or(0)
            });
            count as jint
        } else {
            -2
        }
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_com_bypass_app_BypassCore_nativeGetHashStatus(
        env: JNIEnv,
        _class: JClass,
        handle_ptr: jlong,
    ) -> jstring {
        if handle_ptr == 0 {
            return env.new_string("[]").unwrap().into_raw();
        }

        let handle = unsafe { &*(handle_ptr as *mut EngineHandle) };
        let hashes = handle.turn_pool.get_hashes();
        let json = serde_json::to_string(&hashes).unwrap_or_else(|_| "[]".to_string());
        env.new_string(json).unwrap().into_raw()
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_com_bypass_app_BypassCore_nativeSetHashMode(
        mut env: JNIEnv,
        _class: JClass,
        handle_ptr: jlong,
        mode_str: JString,
    ) {
        if handle_ptr == 0 {
            return;
        }

        let handle = unsafe { &*(handle_ptr as *mut EngineHandle) };
        if let Ok(m) = env.get_string(&mode_str) {
            let m_str: String = m.into();
            let mode = if m_str.eq_ignore_ascii_case("auto") {
                crate::config::HashMode::Auto
            } else {
                crate::config::HashMode::Manual
            };
            handle.turn_pool.set_hash_mode(mode);
        }
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_com_bypass_app_BypassCore_nativeCheckVkHashes(
        mut env: JNIEnv,
        _class: JClass,
        hashes_str: JString,
    ) -> jstring {
        if let Ok(s) = env.get_string(&hashes_str) {
            let str_val: String = s.into();
            let raw_hashes: Vec<String> = serde_json::from_str(&str_val).unwrap_or_else(|_| vec![str_val]);
            let clean_hashes: Vec<String> = raw_hashes
                .iter()
                .flat_map(|h| crate::turn::pool::TurnPool::parse_hashes_input(h))
                .collect();

            let rt = tokio::runtime::Builder::new_current_thread()
                .enable_all()
                .build();

            if let Ok(runtime) = rt {
                let checks = runtime.block_on(async {
                    crate::vk::auth::check_vk_hashes("chrome", &[], &clean_hashes).await
                });

                let mut results = Vec::new();
                for (h, check) in checks {
                    let masked = crate::config::mask_vk_hash(&h);
                    let valid = matches!(check, crate::vk::auth::VkHashCheck::Valid);
                    results.push(serde_json::json!({
                        "hash": h,
                        "masked": masked,
                        "valid": valid,
                    }));
                }
                let json = serde_json::to_string(&results).unwrap_or_else(|_| "[]".to_string());
                return env.new_string(json).unwrap().into_raw();
            }
        }
        env.new_string("[]").unwrap().into_raw()
    }
}
