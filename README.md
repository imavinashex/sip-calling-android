# 📞 SIP Phone — Android VoIP & SIP Client

[![Android](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84?logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Liblinphone SDK](https://img.shields.io/badge/Liblinphone-5.3+-007ACC?logo=cplusplus&logoColor=white)](https://linphone.org)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

A modern, production-grade Android VoIP/SIP calling application built with **Kotlin**, **Liblinphone Android SDK 5.3+**, and **Material 3**. Engineered for low-latency voice calling, carrier NAT traversal, and high reliability across Wi-Fi and mobile data.

---

## 📚 Complete Project Documentation

| Document | Description |
|---|---|
| [🏛️ **Architecture & Technical Design**](docs/ARCHITECTURE.md) | Component hierarchy, StateFlows, SQLite databases, and audio routing. |
| [🔄 **VoIP Call Flows & Lifecycles**](docs/CALL_FLOWS.md) | Sequence diagrams for Registration, Outgoing, Incoming (Foreground & Lock Screen), and Termination. |
| [⚙️ **SIP Server & PBX Setup Guide**](docs/SIP_CONFIGURATION.md) | Connecting to Linphone, Asterisk, FreePBX, OpenSIPS, Kamailio, NAT & STUN config. |
| [📱 **App Store Release & Publishing**](docs/APP_STORE_RELEASE_GUIDE.md) | Production Keystore, PhonePe Appstore & Google Play guidelines, avoiding rejections. |
| [🚀 **Development & Branching Pipeline**](docs/DEVELOPMENT_PIPELINE.md) | Git workflow (`dev` ➔ `main`), SemVer versioning rules, and pre-release gates. |

---

## 🚀 Key Features

### 📡 SIP Protocols & Media Engine
- **Standard RFC 3261 Compliance**: Works with Asterisk, FreePBX, OpenSIPS, Kamailio, Freeswitch, Linphone SIP, and Twilio.
- **Multiple Transports**: Full support for **UDP**, **TCP**, and **TLS (Encrypted SIP signaling)**.
- **Dynamic NAT Traversal**: Built-in **STUN** (`stun.linphone.org` / custom) and ICE keep-alive handling.
- **Listening Sockets**: Ephemeral listening port binding (`transports.udpPort = -1`) enabling incoming calls without third-party push servers.

### 🎙️ In-Call Management
- **Audio Routing**: Seamless switching between Earpiece and Speakerphone across Android `AudioManager` and Liblinphone `AudioDevice`.
- **Microphone Control**: Instant mute / unmute toggle.
- **Call Hold & Resume**: Pauses and resumes RTP media streams with SIP re-INVITE signaling.
- **In-Call DTMF Keypad**: Sends RFC 2833 / 4733 telephony tones for IVR phone trees (*"Press 1 for Sales"*).
- **Live Call Timer & Codec Badge**: Displays active duration and negotiated payload (Opus HD, PCMU/PCMA).

### 🔔 Incoming Call Handling (Lock Screen & Background)
- **Full-Screen Intent**: Wakes up locked screens and displays `IncomingCallActivity` directly.
- **Heads-Up Banner**: High-priority notification banner with instant **Answer** and **Decline** buttons when using other apps.
- **Battery Optimization Exemption**: Prevents Android Doze mode from sleeping SIP sockets.
- **Android 14 Ready**: Compliant with `FOREGROUND_SERVICE_PHONE_CALL` and `USE_FULL_SCREEN_INTENT`.

### 📇 Contacts & Call History
- **Clean Caller ID Resolution**: Strips raw URIs (`sip:064726747@10.1.3.341` ➔ `064726747`) and matches against contacts database.
- **Recent Calls Tab**: Call logs with call direction icons, formatted duration, timestamp, dynamic initials (never `"SI"`), and one-tap callback.
- **Contacts Tab**: Add, search, and import phonebook contacts with automated international number normalization via Google `libphonenumber`.
- **QWERTY Toggle**: Integrated soft keyboard toggle for direct SIP address dialing (e.g. `sip:echo@sip.linphone.org`).

---

## 📁 Repository Structure

```
sip-calling-android/
├── app/
│   ├── build.gradle.kts                   # Dependencies, SDK targets (Min 26, Target 34)
│   ├── proguard-rules.pro                 # Liblinphone SDK JNI proguard preservation
│   └── src/main/
│       ├── AndroidManifest.xml            # Permissions, activities & foreground service
│       ├── java/com/imavinashexdev/sipcalling/
│       │   ├── SipApplication.kt          # Global Application & Core initialization
│       │   ├── core/
│       │   │   └── SipManager.kt          # Linphone Core wrapper, Call lifecycle & StateFlows
│       │   ├── service/
│       │   │   └── SipForegroundService.kt# Background Foreground Service for calls
│       │   ├── data/                      # SQLite DBs: CallLogRepository & ContactRepository
│       │   ├── model/                     # Data models (SipAccount, CallLogEntry, Contact)
│       │   ├── ui/                        # Activities, Dialogs & Adapters
│       │   └── util/                      # CallerIdResolver, PhoneNumberNormalizer, SoundManager
│       └── res/                           # Material 3 layouts, vector drawables & icons
├── docs/                                  # Comprehensive technical & release documentation
├── gradle/libs.versions.toml              # Version Catalog
├── build.gradle.kts                       # Root build configuration
└── settings.gradle.kts                    # Maven repository configuration
```

---

## 🛠️ Quick Start & Build

### 1. Requirements
- **Android Studio**: Hedgehog (2023.1.1) or newer.
- **JDK**: Java 17.
- **Android SDK**: Compile SDK 34, Min SDK 26 (Android 8.0 Oreo).

### 2. Gradle Commands
```powershell
# Stop Gradle daemons
.\gradlew --stop

# Run all unit tests
.\gradlew test

# Build Debug APK (outputs to app/build/outputs/apk/debug/app-debug.apk)
.\gradlew assembleDebug

# Build Signed Release APK
.\gradlew assembleRelease
```

---

## 🧪 Testing SIP Audio

1. Open the app and tap **Settings ⚙️** (top right).
2. Enter your SIP server credentials (e.g. [sip.linphone.org](https://subscribe.linphone.org/)).
3. Tap **Save & Connect**. Once registered, the status pill turns **🟢 Online: sip:username@domain**.
4. Test with free echo services:
   - **Echo Test**: Dial `sip:echo@sip.linphone.org` (verifies microphone and two-way playback).
   - **Music Stream**: Dial `sip:music@sip.linphone.org` (verifies downlink audio).

---

## 🔒 Permissions Overview

| Permission | Category | Purpose |
|---|---|---|
| `RECORD_AUDIO` | Dangerous (Runtime) | Capture microphone audio during VoIP calls. |
| `MODIFY_AUDIO_SETTINGS` | Normal | Toggle between earpiece and speakerphone. |
| `FOREGROUND_SERVICE_PHONE_CALL` | Foreground Service | Keep active VoIP audio streams alive on Android 14+. |
| `USE_FULL_SCREEN_INTENT` | Normal | Wake up locked device screen on incoming calls. |
| `POST_NOTIFICATIONS` | Dangerous (Runtime) | Display active and incoming call notifications on Android 13+. |
| `READ_CONTACTS` | Dangerous (Runtime) | Import device contacts into local contacts tab. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Normal | Prevent OEM power managers from killing background SIP sockets. |
| `WAKE_LOCK` | Normal | Keep CPU awake during ongoing voice calls. |

---

## 📄 License
This project is licensed under the terms of the GNU General Public License v3.0.
