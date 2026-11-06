# 🔄 VoIP Call Flows & State Lifecycle

This document describes the operational call flows, state transitions, and background handling in **SIP Phone**.

---

## 1. 🟢 Registration Flow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Settings as SipSettingsActivity
    participant SM as SipManager
    participant Core as Liblinphone Core
    participant PBX as SIP Registrar / PBX

    User->>Settings: Enter Domain, Port, User, Pass & Transport
    Settings->>Settings: Validate & Save to SharedPreferences
    Settings->>SM: registerAccount(account)
    SM->>Core: clearAccounts() & addAuthInfo(...)
    SM->>Core: createAccountParams(identity, server, expires=120)
    SM->>Core: createNatPolicy(stun="stun.linphone.org", ice=true)
    SM->>Core: addAccount(account) & defaultAccount = account
    Core->>PBX: SIP REGISTER (Contact: sip:user@ip:port)
    PBX-->>Core: SIP 401 Unauthorized (Auth Challenge)
    Core->>PBX: SIP REGISTER (with MD5/SHA-256 Digest)
    PBX-->>Core: SIP 200 OK
    Core->>SM: onAccountRegistrationStateChanged(Ok)
    SM->>SM: _registrationState.value = Registered(identity)
    SM-->>User: UI updates to Online 🟢
```

---

## 2. 📞 Outgoing Call Flow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Main as MainActivity
    participant SM as SipManager
    participant Core as Liblinphone Core
    participant FS as SipForegroundService
    participant CallUI as CallActivity
    participant Remote as Remote Callee / PBX

    User->>Main: Dial number & tap Call Button
    Main->>SM: makeCall(destination)
    SM->>SM: PhoneNumberNormalizer.normalize(destination)
    SM->>Core: inviteAddressWithParams(address, params)
    Core->>Remote: SIP INVITE (SDP Offer: Opus, PCMU)
    SM->>FS: startActiveForegroundService(callerText)
    Main->>CallUI: startActivity(CallActivity)
    Remote-->>Core: SIP 180 Ringing
    Core->>SM: onCallStateChanged(OutgoingRinging)
    SM->>CallUI: UI displays "Ringing…"
    Remote-->>Core: SIP 200 OK (SDP Answer)
    Core->>Remote: SIP ACK
    Core->>SM: onCallStateChanged(StreamsRunning)
    SM->>SM: trackingCallConnected = true & startCallTimer()
    SM->>CallUI: UI displays "Connected" & starts live timer
    Note over Core,Remote: Two-way RTP Audio Streams Active
```

---

## 3. 📳 Incoming Call Flow

### Scenarios:
- **Case A: App is in Foreground (Open)** ➔ Opens `IncomingCallActivity` directly.
- **Case B: Device is Locked / Screen Off** ➔ `FullScreenIntent` notification wakes up the screen and displays `IncomingCallActivity`.
- **Case C: User is in another app (Background)** ➔ High-priority Heads-Up Notification appears with **Answer** and **Decline** buttons.

```mermaid
sequenceDiagram
    autonumber
    participant Remote as Remote Caller
    participant Core as Liblinphone Core
    participant SM as SipManager
    participant FS as SipForegroundService
    participant Notif as Notification Manager
    participant IncUI as IncomingCallActivity
    actor User

    Remote->>Core: SIP INVITE
    Core-->>Remote: SIP 180 Ringing
    Core->>SM: onCallStateChanged(IncomingReceived)
    SM->>SM: CallerIdResolver.resolve(remoteAddress, displayName)
    SM->>FS: startIncomingForegroundService(name, number)
    FS->>Notif: Notify with setFullScreenIntent(IncomingCallActivity)
    
    alt Device Locked or Screen Off
        Notif->>IncUI: Wake Screen & Show Full-Screen UI
    else App in Background / Another App
        Notif->>User: Heads-Up Banner with [Answer] and [Decline]
    else App in Foreground
        SM->>IncUI: startActivity(IncomingCallActivity)
    end
    
    IncUI->>IncUI: SoundManager.startRingtoneAndVibration()
    
    alt User Taps Answer
        User->>IncUI: Tap Answer
        IncUI->>SM: acceptCall()
        SM->>Core: call.accept()
        Core->>Remote: SIP 200 OK
        IncUI->>IncUI: finish() & open CallActivity
    else User Taps Decline
        User->>IncUI: Tap Decline
        IncUI->>SM: terminateCall()
        SM->>Core: call.terminate()
        Core->>Remote: SIP 603 Decline / 486 Busy
        IncUI->>IncUI: finish()
    end
```

---

## 4. 🎛️ In-Call Control Operations

| Operation | Trigger | Underlying Action |
|---|---|---|
| **Mute / Unmute** | Tap Mute Button | `core.isMicEnabled = !core.isMicEnabled`. Updates `_activeCallData`. |
| **Speakerphone** | Tap Speaker Button | Toggles `AudioManager.mode = MODE_IN_COMMUNICATION` with `AudioDeviceInfo.TYPE_BUILTIN_SPEAKER` (Android 12+) and Linphone's `call.outputAudioDevice = AudioDevice.Type.Speaker`. |
| **Hold / Resume** | Tap Hold Button | Toggles `call.pause()` / `call.resume()`. Signals SIP re-INVITE (`a=sendonly` / `a=recvonly`). |
| **DTMF Keypad** | Tap Digit on In-Call Keypad | `call.sendDtmf(char)`. Transmits RFC 2833 / 4733 RTP telephony events across active audio stream. |

---

## 5. 🛑 Call Termination & Log Persistence

```mermaid
sequenceDiagram
    autonumber
    participant Core as Liblinphone Core
    participant SM as SipManager
    participant FS as SipForegroundService
    participant Sound as SoundManager
    participant Repo as CallLogRepository
    participant DB as SQLite (call_logs.db)

    Note over Core: Either party hangs up (BYE or CANCEL)
    Core->>SM: onCallStateChanged(End / Released / Error)
    SM->>SM: stopCallTimer() & resetAudioRouting()
    SM->>Sound: stopRingtoneAndVibration()
    
    SM->>Repo: insertCall(CallLogEntry)
    Note over SM,Repo: Saves clean number (e.g. 064726747),<br/>duration, timestamp, and type (INCOMING / OUTGOING / MISSED)
    Repo->>DB: INSERT INTO call_logs ...
    
    SM->>FS: stopForegroundService()
    FS->>FS: releaseLocks() & stopSelf()
    SM->>SM: _activeCallData.value = Terminated
```
