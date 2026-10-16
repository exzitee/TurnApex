// SPDX-FileCopyrightText: 2026 TurnApex Team
// SPDX-License-Identifier: MIT OR Apache-2.0

use crate::{
    client_perf::{self, Stage as PerfStage},
    packet::PacketBuf,
};
use anyhow::{Result, bail};
use crossbeam_queue::ArrayQueue;
use std::sync::atomic::{AtomicBool, AtomicU64, AtomicUsize, Ordering};
use std::sync::{Arc, RwLock};
use tokio::sync::{Notify, mpsc};
use tokio_util::sync::CancellationToken;

const QUEUE_ACTIVE: u64 = 1;

struct QueuedPacket {
    packet: PacketBuf,
    epoch: u64,
}

struct PacketQueue {
    queue: ArrayQueue<QueuedPacket>,
    notify: Notify,
    state: AtomicU64,
    senders: AtomicUsize,
    receiver_open: AtomicBool,
}

pub struct PacketSender {
    shared: Arc<PacketQueue>,
}

impl Clone for PacketSender {
    fn clone(&self) -> Self {
        self.shared.senders.fetch_add(1, Ordering::Relaxed);
        Self {
            shared: self.shared.clone(),
        }
    }
}

impl Drop for PacketSender {
    fn drop(&mut self) {
        if self.shared.senders.fetch_sub(1, Ordering::AcqRel) == 1 {
            self.shared.notify.notify_waiters();
        }
    }
}

impl PacketSender {
    pub fn try_send(&self, packet: PacketBuf) -> std::result::Result<(), PacketBuf> {
        self.send(packet, false)
    }

    pub fn force_send(&self, packet: PacketBuf) -> std::result::Result<(), PacketBuf> {
        self.send(packet, true)
    }

    fn send(&self, packet: PacketBuf, force: bool) -> std::result::Result<(), PacketBuf> {
        client_perf::measure_sampled(PerfStage::PacketQueue, 128, || {
            self.send_inner(packet, force)
        })
    }

    fn send_inner(&self, packet: PacketBuf, force: bool) -> std::result::Result<(), PacketBuf> {
        if !self.shared.receiver_open.load(Ordering::Acquire) {
            return Err(packet);
        }
        let state = self.shared.state.load(Ordering::Acquire);
        if state & QUEUE_ACTIVE == 0 {
            return Err(packet);
        }
        let queued = QueuedPacket {
            packet,
            epoch: state >> 1,
        };
        if force {
            drop(self.shared.queue.force_push(queued));
        } else if let Err(queued) = self.shared.queue.push(queued) {
            return Err(queued.packet);
        }
        self.shared.notify.notify_one();
        Ok(())
    }
}

pub struct PacketReceiver {
    shared: Arc<PacketQueue>,
}

impl PacketReceiver {
    pub fn try_recv(&self) -> Option<PacketBuf> {
        client_perf::measure_sampled(PerfStage::PacketQueue, 128, || self.try_recv_inner())
    }

    fn try_recv_inner(&self) -> Option<PacketBuf> {
        loop {
            let queued = self.shared.queue.pop()?;
            let state = self.shared.state.load(Ordering::Acquire);
            if state & QUEUE_ACTIVE != 0 && queued.epoch == state >> 1 {
                return Some(queued.packet);
            }
        }
    }

    pub async fn recv(&self, cancel: &CancellationToken) -> Option<PacketBuf> {
        loop {
            if cancel.is_cancelled() {
                return None;
            }
            if let Some(packet) = self.try_recv() {
                return Some(packet);
            }
            if self.shared.senders.load(Ordering::Acquire) == 0 {
                return None;
            }
            let notified = self.shared.notify.notified();
            if let Some(packet) = self.try_recv() {
                return Some(packet);
            }
            if self.shared.senders.load(Ordering::Acquire) == 0 {
                return None;
            }
            tokio::select! {
                biased;
                _ = cancel.cancelled() => return None,
                _ = notified => {}
            }
        }
    }

    pub fn purge(&self) {
        while self.shared.queue.pop().is_some() {}
    }

    pub fn is_closed(&self) -> bool {
        self.shared.senders.load(Ordering::Acquire) == 0 && self.shared.queue.is_empty()
    }

    pub fn has_queued_packet(&self) -> bool {
        !self.shared.queue.is_empty()
    }
}

impl Drop for PacketReceiver {
    fn drop(&mut self) {
        self.shared.receiver_open.store(false, Ordering::Release);
        self.purge();
        self.shared.notify.notify_waiters();
    }
}

pub fn packet_channel(capacity: usize, active: bool) -> (PacketSender, PacketReceiver) {
    let state = u64::from(active) * QUEUE_ACTIVE;
    let shared = Arc::new(PacketQueue {
        queue: ArrayQueue::new(capacity.max(1)),
        notify: Notify::new(),
        state: AtomicU64::new(state),
        senders: AtomicUsize::new(1),
        receiver_open: AtomicBool::new(true),
    });
    (
        PacketSender {
            shared: shared.clone(),
        },
        PacketReceiver { shared },
    )
}

#[derive(Clone)]
pub struct WorkerChannels {
    pub id: usize,
    pub incarnation_id: u64,
    pub turn_path: Arc<str>,
    pub latency: PacketSender,
    pub priority: PacketSender,
    pub bulk: PacketSender,
}

#[derive(Clone)]
pub struct Dispatcher {
    workers: Arc<RwLock<Vec<mpsc::Sender<PacketBuf>>>>,
    round_robin_idx: Arc<AtomicUsize>,
}

impl Default for Dispatcher {
    fn default() -> Self {
        Self::new()
    }
}

impl Dispatcher {
    pub fn new() -> Self {
        Self {
            workers: Arc::new(RwLock::new(Vec::new())),
            round_robin_idx: Arc::new(AtomicUsize::new(0)),
        }
    }

    pub fn set_workers(&self, senders: Vec<mpsc::Sender<PacketBuf>>) {
        let mut guard = self.workers.write().unwrap();
        *guard = senders;
    }

    pub fn add_worker(&self, sender: mpsc::Sender<PacketBuf>) {
        let mut guard = self.workers.write().unwrap();
        guard.push(sender);
    }

    pub fn clear_workers(&self) {
        let mut guard = self.workers.write().unwrap();
        guard.clear();
    }

    pub fn worker_count(&self) -> usize {
        self.workers.read().unwrap().len()
    }

    pub fn register(&self, _channels: WorkerChannels) {}

    pub fn unregister(&self, _id: usize, _incarnation: u64) {}

    pub fn return_packet(&self, _packet: PacketBuf) {}

    pub async fn dispatch(&self, packet: PacketBuf) -> Result<()> {
        let senders = {
            let guard = self.workers.read().unwrap();
            guard.clone()
        };
        if senders.is_empty() {
            bail!("No workers available in Dispatcher");
        }
        let total = senders.len();
        let start = self.round_robin_idx.fetch_add(1, Ordering::Relaxed) % total;

        let mut cur = packet;
        for offset in 0..total {
            let target = (start + offset) % total;
            match senders[target].try_send(cur) {
                Ok(()) => return Ok(()),
                Err(mpsc::error::TrySendError::Full(pkt)) => cur = pkt,
                Err(mpsc::error::TrySendError::Closed(pkt)) => cur = pkt,
            }
        }
        senders[start]
            .send(cur)
            .await
            .map_err(|e| anyhow::anyhow!("Dispatch send failed: {e}"))
    }

    pub fn try_dispatch(&self, packet: PacketBuf) -> Result<()> {
        let senders = {
            let guard = self.workers.read().unwrap();
            guard.clone()
        };
        if senders.is_empty() {
            bail!("No workers available in Dispatcher");
        }
        let total = senders.len();
        let start = self.round_robin_idx.fetch_add(1, Ordering::Relaxed) % total;

        let mut cur = packet;
        for offset in 0..total {
            let target = (start + offset) % total;
            match senders[target].try_send(cur) {
                Ok(()) => return Ok(()),
                Err(mpsc::error::TrySendError::Full(pkt)) => cur = pkt,
                Err(mpsc::error::TrySendError::Closed(pkt)) => cur = pkt,
            }
        }
        bail!("All worker queues are full");
    }
}
