// SPDX-FileCopyrightText: 2026 TurnApex Team
// SPDX-License-Identifier: MIT OR Apache-2.0

pub mod client_perf;
pub mod config;
pub mod cpu_task;
pub mod crypto;
pub mod dispatcher;
pub mod dns;
pub mod events;
pub mod fec;
pub mod ffi;
pub mod flow_frame;
pub mod health;
pub mod logging;
pub mod obfs;
pub mod packet;
pub mod protocol;
pub mod repair;
pub mod rtp;
pub mod scheduler;
pub mod session;
pub mod stats;
pub mod tunnel;
pub mod turn;
pub mod udp_batch;
pub mod vk;
pub mod worker;
pub mod wrap;

pub use protocol as wire_protocol;
pub use scheduler as striped_scheduler;

// Re-exports from TurnApex core
pub use config::{AppConfig, HashMode, SplitTunnelConfig, TunnelMode, VpsConfig, mask_vk_hash};
pub use crypto::DtlsSession;
pub use dispatcher::Dispatcher;
pub use fec::{Budget, should_duplicate};
pub use flow_frame::{FlowReassembler, FlowSequencer};
pub use health::{HealthChecker, TargetProbeStatus};
pub use obfs::{ObfsCipher, ObfsConfig, ObfsMode};
pub use rtp::{rtp_decode, rtp_encode, RtpHeader, RtpMasker};
pub use scheduler::{PacketClass, packet_class};
pub use stats::{TrafficStats, TrafficStatsSnapshot};
pub use tunnel::{Socks5Server, SplitTunnelMatcher, TunEngine};
pub use turn::{
    NativeAddress, NativeCore, StunMessage, TurnAllocation, TurnEndpoint, TurnPool, TurnPoolEntry,
    TurnPoolMode, VkHash, resolve_turn_endpoints,
};
pub use vk::captcha::{CaptchaSolver, VkCaptchaError};
pub use worker::{Worker, run_groups};
