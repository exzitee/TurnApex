use byteorder::{BigEndian, ByteOrder};
use rand::Rng;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};

pub const STUN_MAGIC_COOKIE: u32 = 0x2112A442;
pub const STUN_HEADER_SIZE: usize = 20;

// Message Types
pub const BINDING_REQUEST: u16 = 0x0001;
pub const BINDING_SUCCESS_RESPONSE: u16 = 0x0101;
pub const BINDING_ERROR_RESPONSE: u16 = 0x0111;

pub const ALLOCATE_REQUEST: u16 = 0x0003;
pub const ALLOCATE_SUCCESS_RESPONSE: u16 = 0x0103;
pub const ALLOCATE_ERROR_RESPONSE: u16 = 0x0113;

pub const REFRESH_REQUEST: u16 = 0x0004;
pub const REFRESH_SUCCESS_RESPONSE: u16 = 0x0104;

pub const SEND_INDICATION: u16 = 0x0006;
pub const DATA_INDICATION: u16 = 0x0007;

pub const CREATE_PERMISSION_REQUEST: u16 = 0x0008;
pub const CREATE_PERMISSION_SUCCESS: u16 = 0x0108;

pub const CHANNEL_BIND_REQUEST: u16 = 0x0009;
pub const CHANNEL_BIND_SUCCESS: u16 = 0x0109;

// Attribute Types
pub const ATTR_MAPPED_ADDRESS: u16 = 0x0001;
pub const ATTR_USERNAME: u16 = 0x0006;
pub const ATTR_MESSAGE_INTEGRITY: u16 = 0x0008;
pub const ATTR_ERROR_CODE: u16 = 0x0009;
pub const ATTR_REALM: u16 = 0x0014;
pub const ATTR_NONCE: u16 = 0x0015;
pub const ATTR_XOR_MAPPED_ADDRESS: u16 = 0x0020;
pub const ATTR_CHANNEL_NUMBER: u16 = 0x000C;
pub const ATTR_LIFETIME: u16 = 0x000D;
pub const ATTR_XOR_PEER_ADDRESS: u16 = 0x0012;
pub const ATTR_DATA: u16 = 0x0013;
pub const ATTR_XOR_RELAYED_ADDRESS: u16 = 0x0016;
pub const ATTR_REQUESTED_TRANSPORT: u16 = 0x0019;

#[derive(Debug, Clone)]
pub struct StunMessage {
    pub msg_type: u16,
    pub length: u16,
    pub magic_cookie: u32,
    pub transaction_id: [u8; 12],
    pub attributes: Vec<StunAttribute>,
}

#[derive(Debug, Clone)]
pub struct StunAttribute {
    pub attr_type: u16,
    pub value: Vec<u8>,
}

impl StunMessage {
    pub fn new(msg_type: u16, transaction_id: [u8; 12]) -> Self {
        Self {
            msg_type,
            length: 0,
            magic_cookie: STUN_MAGIC_COOKIE,
            transaction_id,
            attributes: Vec::new(),
        }
    }

    pub fn random_transaction_id() -> [u8; 12] {
        let mut id = [0u8; 12];
        rand::thread_rng().fill(&mut id);
        id
    }

    pub fn add_attribute(&mut self, attr_type: u16, value: Vec<u8>) {
        self.attributes.push(StunAttribute { attr_type, value });
    }

    pub fn encode(&self) -> Vec<u8> {
        let mut body = Vec::new();
        for attr in &self.attributes {
            let mut header = [0u8; 4];
            BigEndian::write_u16(&mut header[0..2], attr.attr_type);
            BigEndian::write_u16(&mut header[2..4], attr.value.len() as u16);
            body.extend_from_slice(&header);
            body.extend_from_slice(&attr.value);

            // Pad to multiple of 4 bytes
            let pad_len = (4 - (attr.value.len() % 4)) % 4;
            if pad_len > 0 {
                body.extend(std::iter::repeat(0).take(pad_len));
            }
        }

        let mut packet = Vec::with_capacity(STUN_HEADER_SIZE + body.len());
        let mut header = [0u8; STUN_HEADER_SIZE];
        BigEndian::write_u16(&mut header[0..2], self.msg_type);
        BigEndian::write_u16(&mut header[2..4], body.len() as u16);
        BigEndian::write_u32(&mut header[4..8], self.magic_cookie);
        header[8..20].copy_from_slice(&self.transaction_id);

        packet.extend_from_slice(&header);
        packet.extend_from_slice(&body);
        packet
    }

    pub fn decode(data: &[u8]) -> Option<Self> {
        if data.len() < STUN_HEADER_SIZE {
            return None;
        }

        let msg_type = BigEndian::read_u16(&data[0..2]);
        let length = BigEndian::read_u16(&data[2..4]) as usize;
        let magic_cookie = BigEndian::read_u32(&data[4..8]);

        if magic_cookie != STUN_MAGIC_COOKIE {
            return None;
        }

        let mut transaction_id = [0u8; 12];
        transaction_id.copy_from_slice(&data[8..20]);

        if data.len() < STUN_HEADER_SIZE + length {
            return None;
        }

        let mut attributes = Vec::new();
        let mut offset = STUN_HEADER_SIZE;
        let end = STUN_HEADER_SIZE + length;

        while offset + 4 <= end {
            let attr_type = BigEndian::read_u16(&data[offset..offset + 2]);
            let attr_len = BigEndian::read_u16(&data[offset + 2..offset + 4]) as usize;
            offset += 4;

            if offset + attr_len > end {
                break;
            }

            let value = data[offset..offset + attr_len].to_vec();
            attributes.push(StunAttribute { attr_type, value });

            offset += attr_len;
            let pad = (4 - (attr_len % 4)) % 4;
            offset += pad;
        }

        Some(Self {
            msg_type,
            length: length as u16,
            magic_cookie,
            transaction_id,
            attributes,
        })
    }

    pub fn get_attribute(&self, attr_type: u16) -> Option<&StunAttribute> {
        self.attributes.iter().find(|a| a.attr_type == attr_type)
    }

    pub fn get_xor_address(&self, attr_type: u16) -> Option<SocketAddr> {
        let attr = self.get_attribute(attr_type)?;
        if attr.value.len() < 8 {
            return None;
        }
        let family = attr.value[1];
        if family != 0x01 {
            // IPv4 supported
            return None;
        }
        let xor_port = BigEndian::read_u16(&attr.value[2..4]);
        let port = xor_port ^ ((STUN_MAGIC_COOKIE >> 16) as u16);

        let xor_ip = BigEndian::read_u32(&attr.value[4..8]);
        let ip = Ipv4Addr::from(xor_ip ^ STUN_MAGIC_COOKIE);

        Some(SocketAddr::new(IpAddr::V4(ip), port))
    }
}

/// Helper builders for RFC 5389 & RFC 5766

pub fn build_binding_request(tx_id: [u8; 12]) -> Vec<u8> {
    let msg = StunMessage::new(BINDING_REQUEST, tx_id);
    msg.encode()
}

pub fn build_allocate_request(tx_id: [u8; 12], lifetime_secs: u32) -> Vec<u8> {
    let mut msg = StunMessage::new(ALLOCATE_REQUEST, tx_id);

    // REQUESTED-TRANSPORT: UDP = 17 (0x11), 3 bytes reserved
    msg.add_attribute(ATTR_REQUESTED_TRANSPORT, vec![17, 0, 0, 0]);

    // LIFETIME
    let mut lt_bytes = [0u8; 4];
    BigEndian::write_u32(&mut lt_bytes, lifetime_secs);
    msg.add_attribute(ATTR_LIFETIME, lt_bytes.to_vec());

    msg.encode()
}

pub fn build_refresh_request(tx_id: [u8; 12], lifetime_secs: u32) -> Vec<u8> {
    let mut msg = StunMessage::new(REFRESH_REQUEST, tx_id);
    let mut lt_bytes = [0u8; 4];
    BigEndian::write_u32(&mut lt_bytes, lifetime_secs);
    msg.add_attribute(ATTR_LIFETIME, lt_bytes.to_vec());
    msg.encode()
}

pub fn build_create_permission(tx_id: [u8; 12], peer_addr: SocketAddr) -> Vec<u8> {
    let mut msg = StunMessage::new(CREATE_PERMISSION_REQUEST, tx_id);

    match peer_addr {
        SocketAddr::V4(v4) => {
            let mut val = vec![0u8; 8];
            val[1] = 0x01; // IPv4
            let xor_port = v4.port() ^ ((STUN_MAGIC_COOKIE >> 16) as u16);
            BigEndian::write_u16(&mut val[2..4], xor_port);

            let ip_u32 = u32::from_be_bytes(v4.ip().octets());
            let xor_ip = ip_u32 ^ STUN_MAGIC_COOKIE;
            BigEndian::write_u32(&mut val[4..8], xor_ip);
            msg.add_attribute(ATTR_XOR_PEER_ADDRESS, val);
        }
        SocketAddr::V6(_) => {}
    }

    msg.encode()
}

pub fn build_send_indication(peer_addr: SocketAddr, data: &[u8]) -> Vec<u8> {
    let mut msg = StunMessage::new(SEND_INDICATION, StunMessage::random_transaction_id());

    if let SocketAddr::V4(v4) = peer_addr {
        let mut val = vec![0u8; 8];
        val[1] = 0x01; // IPv4
        let xor_port = v4.port() ^ ((STUN_MAGIC_COOKIE >> 16) as u16);
        BigEndian::write_u16(&mut val[2..4], xor_port);
        let ip_u32 = u32::from_be_bytes(v4.ip().octets());
        let xor_ip = ip_u32 ^ STUN_MAGIC_COOKIE;
        BigEndian::write_u32(&mut val[4..8], xor_ip);
        msg.add_attribute(ATTR_XOR_PEER_ADDRESS, val);
    }

    msg.add_attribute(ATTR_DATA, data.to_vec());
    msg.encode()
}

/// ChannelData message RFC 5766 Section 11
pub fn encode_channel_data(channel_number: u16, data: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(4 + data.len() + 3);
    let mut hdr = [0u8; 4];
    BigEndian::write_u16(&mut hdr[0..2], channel_number);
    BigEndian::write_u16(&mut hdr[2..4], data.len() as u16);
    out.extend_from_slice(&hdr);
    out.extend_from_slice(data);

    let pad = (4 - (data.len() % 4)) % 4;
    if pad > 0 {
        out.extend(std::iter::repeat(0).take(pad));
    }
    out
}

pub fn decode_channel_data(buf: &[u8]) -> Option<(u16, Vec<u8>)> {
    if buf.len() < 4 {
        return None;
    }
    let channel_num = BigEndian::read_u16(&buf[0..2]);
    // Valid channel range 0x4000 through 0x7FFE
    if !(0x4000..=0x7FFE).contains(&channel_num) {
        return None;
    }
    let length = BigEndian::read_u16(&buf[2..4]) as usize;
    if buf.len() < 4 + length {
        return None;
    }
    Some((channel_num, buf[4..4 + length].to_vec()))
}

pub fn is_channel_data(buf: &[u8]) -> bool {
    if buf.len() < 4 {
        return false;
    }
    let b0 = buf[0];
    // Channel numbers start with 0b01xxxxxx (0x4000..0x7FFE)
    (0x40..=0x7F).contains(&b0)
}
