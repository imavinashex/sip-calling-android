# 📞 Android SIP Calling App (Kotlin + Liblinphone SDK)

A modern, production-grade Android VoIP/SIP calling application built with **Kotlin**, **Liblinphone Android SDK 5.3+**, and **Material 3**.

---

## 🚀 Key Features

- **Standard SIP Protocol Support**: Compatible with Asterisk, FreePBX, OpenSIPS, Kamailio, Freeswitch, Linphone SIP service, Twilio, and standard PBX systems.
- **Multiple Transports**: Supports **UDP**, **TCP**, and **TLS (Encrypted SIP signaling)**.
- **NAT Traversal**: Built-in **STUN** server support for smooth call traversal behind NAT/routers.
- **Full In-Call Management**:
  - 🎙️ **Microphone Mute / Unmute**
  - 🔊 **Speakerphone / Earpiece Toggle**
  - ⏸️ **Call Hold & Resume**
  - 🔢 **In-Call DTMF Keypad** (Send RFC 2833 / SIP INFO tones for IVR menus: *"Press 1 for Sales"*)
  - ⏱️ **Real-time Call Duration Timer** & Codec indicator (Opus HD, PCMU/PCMA, etc.)
- **Incoming Call Handling**:
  - Full-screen incoming call UI (`showWhenLocked` & `turnScreenOn`)
  - Ringtone & vibration alerts using Android `RingtoneManager` and `Vibrator`
  - Quick **Answer** and **Decline** actions
- **Android 14 Ready Foreground Service**:
  - `FOREGROUND_SERVICE_PHONE_CALL` integration
  - Ongoing call notifications with quick "End Call" and "Return to Call" actions
  - `WakeLock` and `WifiLock` to prevent the device from entering deep sleep during VoIP calls
- **Material 3 Dark UI**:
  - Sleek cyber/midnight aesthetics with vibrant dialpad feedback and haptic DTMF audio
  - Live SIP registration status pill (🟢 Registered / 🟡 Connecting / 🔴 Offline)

---

## 📁 Project Architecture

```
sip-calling-android/
├── app/
│   ├── build.gradle.kts                   # App configuration & Liblinphone dependency
│   ├── proguard-rules.pro                 # Liblinphone SDK proguard rules
│   └── src/main/
│       ├── AndroidManifest.xml            # SIP permissions, activities & foreground service
│       ├── java/com/imavinashexdev/sipcalling/
│       │   ├── SipApplication.kt          # Global Application & Core initialization
│       │   ├── core/
│       │   │   └── SipManager.kt          # Liblinphone Core wrapper, Call lifecycle & StateFlows
│       │   ├── model/
│       │   │   ├── SipAccount.kt          # Account model & SharedPreferences persistence
│       │   │   └── CallStateInfo.kt       # Registration & Call state sealed classes
│       │   ├── service/
│       │   │   └── SipForegroundService.kt# Android Foreground Service for calls
│       │   ├── ui/
│       │   │   ├── MainActivity.kt        # Dialer, registration badge & settings trigger
│       │   │   ├── CallActivity.kt        # Active Call screen with Mute/Speaker/Hold/DTMF
│       │   │   ├── IncomingCallActivity.kt# Full-screen incoming ringing UI
│       │   │   └── SipSettingsActivity.kt # SIP PBX credentials & transport setup
│       │   └── util/
│       │       └── SoundManager.kt        # Ringtone playback & DTMF tones
│       └── res/
│           ├── layout/                    # Material 3 responsive layouts
│           ├── drawable/                  # Vector icons & button ripples
│           └── values/                    # Colors, strings, themes
├── gradle/
│   ├── libs.versions.toml                 # Version Catalog (AGP 8.4, Kotlin 1.9, Linphone SDK 5.3)
│   └── wrapper/
├── build.gradle.kts                       # Root build configuration
├── settings.gradle.kts                    # Linphone Maven repository configuration
└── README.md
```

---

## 🛠️ Getting Started in Android Studio

### 1. Open the Project
1. Launch **Android Studio** (Giraffe, Hedgehog, Iguana, Jellyfish, Koala or newer).
2. Click **File -> Open...**
3. Navigate to:
   ```
   path/to/sip-calling-android
   ```
4. Click **OK**. Android Studio will recognize the project and sync Gradle dependencies.

> **Note on Maven Repository**: Liblinphone SDK is hosted on Belledonne Communications' Maven repository configured in `settings.gradle.kts`:
> ```kotlin
> maven {
>     url = uri("https://download.linphone.org/maven_repository/")
> }
> ```

### 2. Run the App
- Connect a physical Android device (with USB debugging enabled) or start an Android Emulator (API 26+).
- Press **Run ▶ (Shift + F10)**.

---

## 🧪 How to Test SIP Calling

### Option A: Free Linphone Account (Easiest)
1. Create a free SIP account at [sip.linphone.org](https://subscribe.linphone.org/).
2. In the app, tap the **Settings ⚙️** icon in the top right.
3. Fill in:
   - **SIP Domain / Host**: `sip.linphone.org`
   - **SIP Port**: `5060` (or `5061` for TLS)
   - **Username**: Your chosen username
   - **Password**: Your SIP password
   - **Transport**: `UDP` (or `TLS`)
4. Tap **Save & Connect**.
5. Once registered, the status pill will turn **🟢 Online: sip:username@sip.linphone.org**.
6. **Test Echo Call**: Dial `sip:music@sip.linphone.org` or `sip:echo@sip.linphone.org` to test two-way audio playback!

### Option B: Local Asterisk / FreePBX / Docker
If you have a local Asterisk or FreePBX server (e.g. running on `192.168.1.100`):
1. Create two PJSIP or SIP extensions (e.g. `1001` and `1002`).
2. Configure Device 1 with Extension `1001`, and Device 2 with Extension `1002`.
3. In the dialpad on Device 1, type `1002` and press the **Green Call Button**.
4. Device 2 will instantly show the **Incoming Call** screen with ringtone and vibration!

---

## 🔒 Permissions Used

| Permission | Reason |
|---|---|
| `RECORD_AUDIO` | Required to capture microphone audio during VoIP calls |
| `MODIFY_AUDIO_SETTINGS` | Required for switching between earpiece and speakerphone |
| `FOREGROUND_SERVICE_PHONE_CALL` | Required on Android 14+ for background audio stream continuity |
| `POST_NOTIFICATIONS` | Required on Android 13+ to display call notifications |
| `USE_FULL_SCREEN_INTENT` | Allows displaying the incoming call screen when phone is locked |
| `WAKE_LOCK` | Keeps the CPU active during an ongoing voice conversation |
