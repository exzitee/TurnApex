use byteorder::{BigEndian, ByteOrder};
use chacha20poly1305::aead::{Aead, KeyInit};
use chacha20poly1305::{ChaCha20Poly1305, Key, Nonce};
use rand::Rng;
use sha2::{Digest, Sha256};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use tokio::sync::RwLock;

pub const DTLS_RECORD_HEADER_SIZE: usize = 13;
pub const DTLS_VERSION_1_2: u16 = 0xFEFD;
pub const CONTENT_TYPE_APP_DATA: u8 = 23;
pub const CONTENT_TYPE_HANDSHAKE: u8 = 22;

#[derive(Debug, thiserror::Error)]
pub enum DtlsError {
    #[error("Packet too short for DTLS record: {0} bytes")]
    RecordTooShort(usize),
    #[error("Unsupported DTLS version: 0x{0:04X}")]
    UnsupportedVersion(u16),
    #[error("Decryption failed or invalid authentication tag")]
    DecryptionFailed,
}

pub struct DtlsSession {
    cipher: ChaCha20Poly1305,
    seq: AtomicU64,
    epoch: u16,
    pub session_ticket: Arc<RwLock<[u8; 32]>>,
}

impl DtlsSession {
    pub fn new_with_psk(psk: &str) -> Self {
        // Derive 256-bit key using SHA-256
        let mut hasher = Sha256::new();
        hasher.update(psk.as_bytes());
        let key_bytes = hasher.finalize();
        let key = Key::from_slice(&key_bytes);
        let cipher = ChaCha20Poly1305::new(key);

        let mut initial_ticket = [0u8; 32];
        rand::thread_rng().fill(&mut initial_ticket);

        Self {
            cipher,
            seq: AtomicU64::new(0),
            epoch: 1,
            session_ticket: Arc::new(RwLock::new(initial_ticket)),
        }
    }

    /// Encrypts application data into an authentic DTLS 1.2 record
    pub fn encrypt_record(&self, plaintext: &[u8]) -> Result<Vec<u8>, DtlsError> {
        let seq = self.seq.fetch_add(1, Ordering::Relaxed);

        // Construct 96-bit AEAD Nonce from epoch (16 bits) and seq (48 bits) + salt (32 bits)
        let mut nonce_bytes = [0u8; 12];
        BigEndian::write_u16(&mut nonce_bytes[0..2], self.epoch);
        let seq_48 = seq & 0x0000_FFFF_FFFF_FFFF;
        nonce_bytes[2..8].copy_from_slice(&seq_48.to_be_bytes()[2..8]);
        BigEndian::write_u32(&mut nonce_bytes[8..12], 0xCAFE_BABE);

        let nonce = Nonce::from_slice(&nonce_bytes);

        // Encrypt with ChaCha20-Poly1305
        let ciphertext = self
            .cipher
            .encrypt(nonce, plaintext)
            .map_err(|_| DtlsError::DecryptionFailed)?;

        let mut record = Vec::with_capacity(DTLS_RECORD_HEADER_SIZE + ciphertext.len());

        // Header: ContentType (1) + Version (2) + Epoch (2) + SequenceNumber (6) + Length (2)
        record.push(CONTENT_TYPE_APP_DATA);

        let mut ver_bytes = [0u8; 2];
        BigEndian::write_u16(&mut ver_bytes, DTLS_VERSION_1_2);
        record.extend_from_slice(&ver_bytes);

        let mut epoch_bytes = [0u8; 2];
        BigEndian::write_u16(&mut epoch_bytes, self.epoch);
        record.extend_from_slice(&epoch_bytes);

        let mut seq_bytes = [0u8; 8];
        BigEndian::write_u64(&mut seq_bytes, seq_48);
        record.extend_from_slice(&seq_bytes[2..8]);

        let mut len_bytes = [0u8; 2];
        BigEndian::write_u16(&mut len_bytes, ciphertext.len() as u16);
        record.extend_from_slice(&len_bytes);

        record.extend_from_slice(&ciphertext);
        Ok(record)
    }

    /// Decrypts a DTLS 1.2 record, validating header and AEAD tag
    pub fn decrypt_record(&self, record: &[u8]) -> Result<Vec<u8>, DtlsError> {
        if record.len() < DTLS_RECORD_HEADER_SIZE {
            return Err(DtlsError::RecordTooShort(record.len()));
        }

        let _content_type = record[0];
        let version = BigEndian::read_u16(&record[1..3]);
        if version != DTLS_VERSION_1_2 && version != 0x0303 {
            return Err(DtlsError::UnsupportedVersion(version));
        }

        let epoch = BigEndian::read_u16(&record[3..5]);
        let mut seq_buf = [0u8; 8];
        seq_buf[2..8].copy_from_slice(&record[5..11]);
        let _seq_48 = BigEndian::read_u64(&seq_buf);
        let length = BigEndian::read_u16(&record[11..13]) as usize;

        if record.len() < DTLS_RECORD_HEADER_SIZE + length {
            return Err(DtlsError::RecordTooShort(record.len()));
        }

        let ciphertext = &record[DTLS_RECORD_HEADER_SIZE..DTLS_RECORD_HEADER_SIZE + length];

        let mut nonce_bytes = [0u8; 12];
        BigEndian::write_u16(&mut nonce_bytes[0..2], epoch);
        nonce_bytes[2..8].copy_from_slice(&seq_buf[2..8]);
        BigEndian::write_u32(&mut nonce_bytes[8..12], 0xCAFE_BABE);

        let nonce = Nonce::from_slice(&nonce_bytes);

        self.cipher
            .decrypt(nonce, ciphertext)
            .map_err(|_| DtlsError::DecryptionFailed)
    }

    /// 0-RTT session resumption ticket generation
    pub async fn issue_session_ticket(&self) -> [u8; 32] {
        let mut new_ticket = [0u8; 32];
        rand::thread_rng().fill(&mut new_ticket);
        *self.session_ticket.write().await = new_ticket;
        new_ticket
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_dtls_encrypt_decrypt() {
        let session = DtlsSession::new_with_psk("super-secret-key-123");
        let payload = b"Hello Secure Tunnel via VK!";

        let encrypted = session.encrypt_record(payload).expect("Encryption failed");
        assert!(encrypted.len() > DTLS_RECORD_HEADER_SIZE);
        assert_eq!(encrypted[0], CONTENT_TYPE_APP_DATA);

        let decrypted = session.decrypt_record(&encrypted).expect("Decryption failed");
        assert_eq!(decrypted, payload);
    }
}
