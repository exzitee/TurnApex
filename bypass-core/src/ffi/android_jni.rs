use crate::config::{AppConfig, TurnPoolMode};
use crate::crypto::DtlsSession;
use crate::health::HealthChecker;
use crate::stats::TrafficStats;
use crate::tunnel::tun::TunEngine;
use crate::turn::TurnPool;
use std::net::SocketAddr;
use std::sync::Arc;
use tokio::runtime::Runtime;

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

    #[no_mangle]
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
            let pool = TurnPool::new(
                &config.turn_servers,
                vps_addr,
                config.mode,
                config.rtp_masking,
            )
            .await
            .expect("Failed to create TurnPool");

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

    #[no_mangle]
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

    #[no_mangle]
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
        // Dropping handle drops runtime and cleanly shuts down threads
    }

    #[no_mangle]
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

    #[no_mangle]
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

            handle.runtime.block_on(async {
                handle.turn_pool.set_mode(mode).await;
            });
        }
    }
}
