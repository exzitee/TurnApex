// SPDX-FileCopyrightText: 2026 TurnApex Team
// SPDX-License-Identifier: MIT OR Apache-2.0

use crate::{
    fec::{self, Budget},
    flow_frame::{self, FlowSequencer},
    obfs::{ObfsCipher, ObfsConfig, ObfsMode, ObfsState},
    packet::PacketBuf,
    scheduler::packet_class,
    turn::TurnAllocation,
};
use std::sync::Arc;
use tokio::sync::mpsc;
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;

pub struct Worker {
    pub id: usize,
    pub allocation: Arc<TurnAllocation>,
    pub sender: mpsc::Sender<PacketBuf>,
    handle: Option<JoinHandle<()>>,
}

impl Worker {
    pub fn new(
        id: usize,
        allocation: Arc<TurnAllocation>,
        cipher: Arc<ObfsCipher>,
        obfs_mode: ObfsMode,
        cancel: CancellationToken,
    ) -> (Self, mpsc::Sender<PacketBuf>) {
        let (tx, mut rx) = mpsc::channel::<PacketBuf>(256);
        let alloc = allocation.clone();
        let handle = tokio::spawn(async move {
            let mut sequencer = FlowSequencer::new();
            let mut fec_budget = Budget::new();
            let mut obfs_state = ObfsState::new();
            let obfs_config = ObfsConfig::new(obfs_mode);

            loop {
                tokio::select! {
                    _ = cancel.cancelled() => break,
                    packet_opt = rx.recv() => {
                        let Some(mut packet) = packet_opt else { break };
                        let _class = packet_class(packet.as_slice());
                        if let Some(header) = sequencer.next(packet.as_slice()) {
                            if let Ok(buf) = packet.prepend(flow_frame::FRAME_LEN) {
                                header.encode(buf);
                            }
                        }
                        let duplicate = fec_budget.allow() && fec::should_duplicate(packet.as_slice());
                        if let Err(err) = cipher.wrap(&mut packet, &obfs_config, &mut obfs_state) {
                            crate::log_error!("[WORKER {}] obfs wrap error: {err}", id);
                            continue;
                        }
                        if let Err(err) = alloc.send_with_duplicate(packet, duplicate).await {
                            crate::log_error!("[WORKER {}] turn send error: {err}", id);
                        }
                    }
                }
            }
        });

        (
            Self {
                id,
                allocation,
                sender: tx.clone(),
                handle: Some(handle),
            },
            tx,
        )
    }

    pub async fn stop(&mut self) {
        if let Some(h) = self.handle.take() {
            h.abort();
            let _ = h.await;
        }
    }
}

pub fn run_groups(
    workers_per_group: usize,
    allocations: &[Arc<TurnAllocation>],
    cipher: Arc<ObfsCipher>,
    mode: ObfsMode,
    cancel: CancellationToken,
) -> Vec<Worker> {
    let mut workers = Vec::new();
    let mut worker_id = 0;
    for alloc in allocations {
        for _ in 0..workers_per_group {
            let (worker, _) = Worker::new(
                worker_id,
                alloc.clone(),
                cipher.clone(),
                mode,
                cancel.clone(),
            );
            workers.push(worker);
            worker_id += 1;
        }
    }
    workers
}
