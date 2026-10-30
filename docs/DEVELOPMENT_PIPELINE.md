# 🚀 Android SIP Calling App — Development & Release Pipeline

This document defines the official engineering workflow, branching strategy, versioning rules, and pre-release checklist for the **SIP Phone** project.

---

## 🏗️ 1. Architecture & Repository Structure

```
sip-calling-android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml                  # App manifest, permissions, foreground service
│   │   ├── java/com/imavinashexdev/sipcalling/  # Main Kotlin codebase
│   │   │   ├── core/SipManager.kt               # Liblinphone SDK Core engine wrapper & state flows
│   │   │   ├── service/SipForegroundService.kt  # Android 14+ background VoIP call service
│   │   │   ├── ui/                              # Activities, dialogs & adapters
│   │   │   ├── data/                            # SQLite helpers & repositories (Call logs, contacts)
│   │   │   ├── model/                           # Data models & sealed state classes
│   │   │   └── util/                            # Audio, DTMF, phone formatting & caller ID
│   │   └── res/                                 # Layouts, vector drawables & adaptive mipmap icons
│   └── build.gradle.kts                         # App dependencies, SDK versioning, packaging
├── docs/
│   └── DEVELOPMENT_PIPELINE.md                  # This pipeline document
├── build.gradle.kts                             # Root Gradle build configuration
├── settings.gradle.kts                          # Repository & Liblinphone Maven configuration
└── README.md                                    # Public project introduction
```

---

## 🌿 2. Git Branching Strategy

| Branch | Purpose | Build Type | Stability |
|---|---|---|---|
| **`dev`** | Daily active development, new features, bug fixes, experiments | Debug / Beta APK | Work in progress |
| **`main`** | Production releases, App Store builds (Indus, Play Store) | Signed Release APK | 100% Tested & Stable |

### 룰 Rules:
1. **Never commit directly to `main`**: All work happens on `dev`.
2. **Merge `dev` into `main`** only when a release is ready and tested on physical hardware.
3. **Never delete `dev`**: `dev` remains the persistent working branch.

---

## 🛣️ 3. The 3-Stage Release Pipeline

```
[ Stage 1: Development ] ──────▶ [ Stage 2: Community Beta ] ──────▶ [ Stage 3: Store Production ]
      Branch: dev                     Branch: dev (Release tag)             Branch: main
  • Rapid iterations              • Real-device testing                 • Indus Appstore / Play Store
  • Debug APK                     • GitHub Releases                     • Strict store compliance
  • Local tests                   • Feedback & bug fixing               • Production Keystore (.jks)
```

### Stage 1: Local Development
- **Branch**: `dev`
- **Build command**: `.\gradlew assembleDebug`
- **Output**: `app/build/outputs/apk/debug/app-debug.apk`
- **Target**: Local emulator or developer phone via USB debugging.

### Stage 2: Beta / Community Testing
- **Branch**: `dev`
- **Distribution**: GitHub Releases (`https://github.com/imavinashex/sip-calling-android/releases`)
- **Version format**: e.g., `1.0.4-beta` (Optional beta suffix allowed ONLY on GitHub).
- **Target**: Beta testers with real PBX servers (Asterisk, FreePBX, Linphone SIP).

### Stage 3: Store Production (Indus Appstore / Google Play)
- **Branch**: `main` (merged cleanly from `dev`)
- **Distribution**: Official App Store Consoles
- **App Name**: Strictly **`SIP Phone`** (NO "beta", NO "demo", NO "test").
- **Version format**: Pure SemVer: **`1.0.4`**, **`1.0.5`**, **`1.1.0`** (NO `-beta`).
- **Signature**: Signed with Production Keystore (`sip-release-key.jks`).

---

## 🔢 4. Versioning Protocol

Every release must update two fields in `app/build.gradle.kts`:

```kotlin
defaultConfig {
    applicationId = "com.sipphone.sipcalling"
    minSdk = 26
    targetSdk = 34
    
    versionCode = 4         // 👈 ALWAYS increment by +1 for every store release
    versionName = "1.0.4"   // 👈 Human readable version (No "beta" for store builds)
}
```

- **`versionCode`**: Strictly a positive integer. App stores reject uploads if `versionCode` is equal to or lower than the previous upload.
- **`versionName`**: Clean semantic version (`MAJOR.MINOR.PATCH`).

---

## ✅ 5. Pre-Release Checklist (The "Go-Live" Gate)

Before uploading an APK to PhonePe Indus Appstore or Google Play Console, verify each checkbox:

- [ ] **No "Beta" in Code**: `versionName` in `app/build.gradle.kts` is pure (e.g. `"1.0.4"`).
- [ ] **Version Code Incremented**: `versionCode` is higher than previously published build.
- [ ] **No "Beta" in App Name**: `strings.xml` has `<string name="app_name">SIP Phone</string>`.
- [ ] **Console Metadata Clean**: App title, short description, and full description on Indus Appstore contain NO references to "beta", "demo", "testing", or "pre-release".
- [ ] **Signed with Production Keystore**: Built via Android Studio `Generate Signed Bundle/APK` using `sip-release-key.jks` with V1 & V2 signatures enabled.
- [ ] **Hardware Sanity Check**: Verified on a physical phone:
  - Inbound & outbound audio (Microphone & Speaker/Earpiece).
  - Proximity sensor turns screen off when held to ear.
  - Call hold & resume works smoothly.
  - Background call notification functions when screen is off.
- [ ] **Assets Ready**: 512×512 high-res app icon (`play_store_512.png`) and at least 2 clear screenshots attached.

---

## 🛠️ 6. Quick Command Reference

```powershell
# 1. Stop stuck Gradle daemons
.\gradlew --stop

# 2. Build Debug APK
.\gradlew assembleDebug

# 3. Build Release APK
.\gradlew assembleRelease

# 4. Run Unit Tests
.\gradlew test

# 5. Git Sync Workflow
git checkout dev
git add -A
git commit -m "Your descriptive commit"
git push origin dev
```
