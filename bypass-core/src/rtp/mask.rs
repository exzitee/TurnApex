use byteorder::{BigEndian, ByteOrder};
use rand::Rng;
use std::sync::atomic::{AtomicU16, AtomicU32, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::sync::RwLock;

pub const RTP_VERSION: u8 = 2;
pub const DEFAULT_PAYLOAD_TYPE: u8 = 96; // Dynamic payload type (VP8/H.264 video WebRTC)
pub const TIMESTAMP_INCREMENT_30FPS: u32 = 3000; // 90000 Hz clock / 30 fps
pub const SSRC_ROTATION_INTERVAL: Duration = Duration::from_secs(300); // Rotate every 5 minutes

#[derive(Debug, thiserror::Error)]
pub enum RtpError {
    #[error("Packet too short for RTP header: {0} bytes")]
    PacketTooShort(usize),
    #[error("Invalid RTP version: {0}, expected 2")]
    InvalidVersion(u8),
    #[error("Invalid payload type: {0}, expected dynamic range 96..=127")]
    InvalidPayloadType(u8),
    #[error("Packet corrupted or truncated extension")]
    TruncatedExtension,
}

#[derive(Debug, Clone)]
pub struct RtpHeader {
    pub version: u8,
    pub payload_type: u8,
    pub sequence_number: u16,
    pub timestamp: u32,
    pub ssrc: u32,
    pub payload: Vec<u8>,
}

pub struct RtpMasker {
    payload_type: u8,
    seq: AtomicU16,
    timestamp: AtomicU32,
    ssrc: AtomicU32,
    last_ssrc_rotation: Arc<RwLock<Instant>>,
}

impl Default for RtpMasker {
    fn default() -> Self {
        Self::new(DEFAULT_PAYLOAD_TYPE)
    }
}

impl RtpMasker {
    pub fn new(payload_type: u8) -> Self {
        let mut rng = rand::thread_rng();
        let initial_seq: u16 = rng.gen();
        let initial_ts: u32 = rng.gen();
        let initial_ssrc: u32 = rng.gen();

        Self {
            payload_type,
            seq: AtomicU16::new(initial_seq),
            timestamp: AtomicU32::new(initial_ts),
            ssrc: AtomicU32::new(initial_ssrc),
            last_ssrc_rotation: Arc::new(RwLock::new(Instant::now())),
        }
    }

    /// Check if SSRC rotation is due, and refresh if needed
    pub async fn check_ssrc_rotation(&self) {
        let now = Instant::now();
        let should_rotate = {
            let last = self.last_ssrc_rotation.read().await;
            now.duration_since(*last) > SSRC_ROTATION_INTERVAL
        };

        if should_rotate {
            let mut last = self.last_ssrc_rotation.write().await;
            if now.duration_since(*last) > SSRC_ROTATION_INTERVAL {
                let new_ssrc: u32 = rand::thread_rng().gen();
                self.ssrc.store(new_ssrc, Ordering::Relaxed);
                *last = now;
                tracing::debug!("RTP SSRC rotated to 0x{:08X}", new_ssrc);
            }
        }
    }

    /// Encapsulates a DTLS or IP payload into an authentic RTP WebRTC packet
    pub fn encode(&self, payload: &[u8]) -> Vec<u8> {
        let seq = self.seq.fetch_add(1, Ordering::Relaxed);
        let ts = self.timestamp.fetch_add(TIMESTAMP_INCREMENT_30FPS, Ordering::Relaxed);
        let ssrc = self.ssrc.load(Ordering::Relaxed);

        let mut packet = Vec::with_capacity(12 + payload.len());
        // Byte 0: V=2, P=0, X=0, CC=0 -> 1000 0000 = 0x80
        packet.push(0x80);
        // Byte 1: M=0 (or 1 on keyframes), PT=payload_type
        packet.push(self.payload_type & 0x7F);

        // Bytes 2-3: Sequence number
        let mut seq_bytes = [0u8; 2];
        BigEndian::write_u16(&mut seq_bytes, seq);
        packet.extend_from_slice(&seq_bytes);

        // Bytes 4-7: Timestamp (monotonic 30fps)
        let mut ts_bytes = [0u8; 4];
        BigEndian::write_u32(&mut ts_bytes, ts);
        packet.extend_from_slice(&ts_bytes);

        // Bytes 8-11: SSRC
        let mut ssrc_bytes = [0u8; 4];
        BigEndian::write_u32(&mut ssrc_bytes, ssrc);
        packet.extend_from_slice(&ssrc_bytes);

        // Payload
        packet.extend_from_slice(payload);
        packet
    }

    /// Strips RTP header and returns the extracted payload
    pub fn decode(rtp_packet: &[u8]) -> Result<Vec<u8>, RtpError> {
        if rtp_packet.len() < 12 {
            return Err(RtpError::PacketTooShort(rtp_packet.len()));
        }

        let b0 = rtp_packet[0];
        let version = b0 >> 6;
        if version != RTP_VERSION {
            return Err(RtpError::InvalidVersion(version));
        }

        let has_padding = (b0 & 0x20) != 0;
        let has_extension = (b0 & 0x10) != 0;
        let csrc_count = (b0 & 0x0F) as usize;

        let b1 = rtp_packet[1];
        let payload_type = b1 & 0x7F;
        // Verify realistic dynamic video range (96..127) or standard audio/video
        if !(96..=127).contains(&payload_type) && payload_type != 0 && payload_type != 8 {
            return Err(RtpError::InvalidPayloadType(payload_type));
        }

        let mut offset = 12 + (csrc_count * 4);
        if rtp_packet.len() < offset {
            return Err(RtpError::PacketTooShort(rtp_packet.len()));
        }

        // Handle RTP header extension if present
        if has_extension {
            if rtp_packet.len() < offset + 4 {
                return Err(RtpError::TruncatedExtension);
            }
            let ext_len_words = BigEndian::read_u16(&rtp_packet[offset + 2..offset + 4]) as usize;
            offset += 4 + (ext_len_words * 4);
            if rtp_packet.len() < offset {
                return Err(RtpError::TruncatedExtension);
            }
        }

        let mut end = rtp_packet.len();
        if has_padding && end > offset {
            let padding_len = rtp_packet[end - 1] as usize;
            if padding_len <= (end - offset) {
                end -= padding_len;
            }
        }

        Ok(rtp_packet[offset..end].to_vec())
    }

    /// Full parser into RtpHeader
    pub fn parse_header(rtp_packet: &[u8]) -> Result<RtpHeader, RtpError> {
        if rtp_packet.len() < 12 {
            return Err(RtpError::PacketTooShort(rtp_packet.len()));
        }

        let version = rtp_packet[0] >> 6;
        if version != RTP_VERSION {
            return Err(RtpError::InvalidVersion(version));
        }

        let payload_type = rtp_packet[1] & 0x7F;
        let sequence_number = BigEndian::read_u16(&rtp_packet[2..4]);
        let timestamp = BigEndian::read_u32(&rtp_packet[4..8]);
        let ssrc = BigEndian::read_u32(&rtp_packet[8..12]);

        let payload = Self::decode(rtp_packet)?;

        Ok(RtpHeader {
            version,
            payload_type,
            sequence_number,
            timestamp,
            ssrc,
            payload,
        })
    }
}

// Global convenience functions required by spec:
pub fn rtp_encode(dtls_packet: &[u8]) -> Vec<u8> {
    use std::sync::OnceLock;
    static GLOBAL_MASKER: OnceLock<RtpMasker> = OnceLock::new();
    let masker = GLOBAL_MASKER.get_or_init(|| RtpMasker::new(DEFAULT_PAYLOAD_TYPE));
    masker.encode(dtls_packet)
}

pub fn rtp_decode(rtp_packet: &[u8]) -> Vec<u8> {
    RtpMasker::decode(rtp_packet).unwrap_or_else(|_| rtp_packet.to_vec())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rtp_encode_decode() {
        let masker = RtpMasker::new(96);
        let payload = b"Hello, encrypted DTLS stream via VK Calls!";
        let encoded = masker.encode(payload);

        assert!(encoded.len() > 12);
        assert_eq!(encoded[0] >> 6, 2); // Version 2
        assert_eq!(encoded[1] & 0x7F, 96); // PT 96

        let decoded = RtpMasker::decode(&encoded).expect("Decode should succeed");
        assert_eq!(decoded, payload);
    }

    #[test]
    fn test_rtp_header_parsing() {
        let masker = RtpMasker::new(98);
        let payload = b"Test 123";
        let encoded = masker.encode(payload);

        let parsed = RtpMasker::parse_header(&encoded).expect("Parse header failed");
        assert_eq!(parsed.version, 2);
        assert_eq!(parsed.payload_type, 98);
        assert_eq!(parsed.payload, payload);
    }
}
