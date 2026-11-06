# ⚙️ SIP Server & PBX Configuration Guide

This guide provides configuration instructions for connecting **SIP Phone** to popular SIP servers, PBXs, and cloud VoIP providers.

---

## 🌐 1. Public Free SIP Service (Linphone.org)

Ideal for quick testing, two-way audio verification, and echo tests.

1. **Create Account**: Register a free SIP account at [subscribe.linphone.org](https://subscribe.linphone.org/).
2. **Configure in App**:
   - **Domain**: `sip.linphone.org`
   - **Port**: `5060` (UDP) or `5061` (TLS)
   - **Username**: Your username (without `@sip.linphone.org`)
   - **Password**: Your SIP password
   - **Transport**: `UDP` or `TLS`
   - **STUN Server**: `stun.linphone.org`
3. **Audio Test Destinations**:
   - **Echo Test**: Dial `sip:echo@sip.linphone.org` — speaks back your own voice to test latency and audio quality.
   - **Music on Hold**: Dial `sip:music@sip.linphone.org` — plays continuous music to verify downlink RTP audio.

---

## 🏢 2. Asterisk / FreePBX Configuration

### A. FreePBX PJSIP Extension Settings
When creating an extension in FreePBX (**Applications -> Extensions -> Add New PJSIP Extension**):

```ini
[General Tab]
User Extension: 1001
Display Name: Avinash
Secret: StrongPassword123

[Advanced Tab]
Qualify: Yes
Qualify Frequency: 60
Rewrite Contact: Yes          ; 👈 CRITICAL for NAT
RTP Symmetric: Yes            ; 👈 CRITICAL for two-way audio behind NAT
Force rport: Yes              ; 👈 CRITICAL for UDP traversal
Direct Media: No              ; Forces audio through PBX
Media Encryption: None (or SRTP if using TLS)
```

### B. Raw Asterisk `pjsip.conf` Example

```ini
; =====================================
; Endpoint Definition
; =====================================
[1001]
type=endpoint
context=from-internal
disallow=all
allow=opus
allow=ulaw
allow=alaw
auth=1001
aors=1001
rtp_symmetric=yes
force_rport=yes
rewrite_contact=yes
direct_media=no

[1001]
type=auth
auth_type=userpass
password=StrongPassword123
username=1001

[1001]
type=aor
max_contacts=2
remove_existing=yes
qualify_frequency=60
```

---

## 🔒 3. Transport Protocols Comparison

| Transport | Default Port | Advantages | Best For |
|---|---|---|---|
| **UDP** | `5060` | Lowest latency, standard default for almost all PBX systems. | Local office LAN, low-packet-loss Wi-Fi networks. |
| **TCP** | `5060` | Guaranteed delivery of large SIP packets (e.g. extensive SDP with multiple codecs). | Cellular networks (4G/5G) where carriers filter large UDP datagrams. |
| **TLS** | `5061` | End-to-end encrypted SIP signaling (prevents eavesdropping, MITM attacks). | Public internet calling, enterprise compliance, remote workers. |

---

## 🛠️ 4. NAT Traversal & Audio Troubleshooting

### ⚠️ Issue 1: One-Way Audio (I can hear them, but they cannot hear me)
* **Root Cause**: The device is behind a symmetric NAT/firewall that does not map the inbound RTP media port.
* **Fix**:
  1. In the app settings, set **STUN Server** to `stun.l.google.com:19302` or `stun.linphone.org`.
  2. On Asterisk/FreePBX, verify that `rtp_symmetric=yes` and `force_rport=yes` are enabled.
  3. Ensure UDP RTP ports `10000-20000` are forwarded to your PBX server.

### ⚠️ Issue 2: Registration Failed (401 / 403 / Timeout)
* **401 Unauthorized**: Check username and password. Note that some PBXs use an `Authentication ID` different from the extension number.
* **403 Forbidden**: Extension is disabled or PBX IP whitelist (Fail2Ban) has temporarily blocked your IP.
* **Timeout (No Response)**: Check that the SIP port (`5060`) is reachable. Verify that cellular carrier is not blocking SIP ALG. Switching transport to **TCP** often bypasses carrier UDP SIP blocks.

### ⚠️ Issue 3: Calls drop after exactly 30 seconds
* **Root Cause**: Asterisk sent an `ACK` packet, but due to incorrect NAT `externip` configuration, the ACK was sent to the wrong IP, causing the PBX to terminate the call.
* **Fix**: In Asterisk `pjsip.conf` / FreePBX SIP Settings, configure `External Address` and `Local Networks` correctly.
