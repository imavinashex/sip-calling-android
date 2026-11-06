# 📱 App Store Release & Publishing Guide
### (PhonePe Appstore / Indus Appstore & Google Play Console)

This document contains step-by-step instructions for building, signing, and publishing **SIP Phone** to public Android marketplaces while complying with store policies and avoiding review rejections.

---

## 🔑 1. Keystore Creation & Release Signing

App stores strictly reject debug-signed APKs (`android:debuggable="true"` or signed with Android Debug Key). A production release keystore (`.jks`) is mandatory.

### Generate a Production Keystore via Command Line
```powershell
keytool -genkey -v -keystore sip-release-key.jks -alias sipkey -keyalg RSA -keysize 2048 -validity 10000
```

### Store Credentials Safely in `keystore.properties` (Excluded from Git)
Create a file `keystore.properties` in your project root:
```properties
storeFile=../sip-release-key.jks
storePassword=YourKeystorePassword
keyAlias=sipkey
keyPassword=YourKeyPassword
```

### Build a Signed Release APK / AAB
In Android Studio:
1. Go to **Build -> Generate Signed Bundle / APK...**
2. Choose **APK** (or **Android App Bundle** for Google Play).
3. Select your `sip-release-key.jks`, enter the alias and password.
4. Select build variant: **`release`**.
5. Check both **V1 (Jar Signature)** and **V2 (Full APK Signature)**.
6. Click **Finish**. The output will be located at:
   `app/build/outputs/apk/release/app-release.apk`

---

## 🚫 2. Avoiding Rejections: Lessons from PhonePe Appstore

### The "Pre-Release State" Rejection Reason:
> *"Attention Developer, Your App is put On Hold. Reasons: The app is in pre-release state (e.g., beta, testing, or demo). Required Action: Please upload the live app and submit for review."*

### Why This Happens & How to Prevent It:
1. **Never use `-beta`, `demo`, or `test` in `versionName`**:
   - ❌ Bad: `versionName = "1.0.4-beta"` or `versionName = "1.0.4-test"`
   - ✅ Good: `versionName = "1.0.6"` (Strict SemVer: `MAJOR.MINOR.PATCH`).
2. **Never upload `app-debug.apk` to a public release track**:
   - Automated scanners inspect `AndroidManifest.xml`. If `android:debuggable="true"` is found, the build is flagged as a test build.
3. **Never include "Demo" or "Beta" in Console Store Listing**:
   - **App Name**: Strictly **`SIP Phone`** (Not *"SIP Phone Beta"* or *"Demo VoIP"*).
   - **Short & Full Descriptions**: Avoid phrases like *"This is a demo VoIP app"* or *"For testing only"*.
4. **Always Provide Reviewer Test Instructions / Demo Account**:
   - Reviewers do not own a private SIP PBX server. If they launch the app and see *"Not Configured"*, they will think the app is an incomplete mockup.

---

## 📝 3. Reviewer Notes Template (Copy & Paste to Console)

When submitting for review in the **App Review Notes / Demo Account Credentials** field, provide this exact text:

```text
To the App Review Team:

SIP Phone is a fully functional, standard RFC 3261 compliant VoIP softphone client.
Users can configure their own corporate/residential SIP PBX accounts (Asterisk, FreePBX, Linphone, etc.) in Settings.

To test the application's calling capabilities without requiring a private PBX:
1. You can test public SIP audio endpoints directly from the dialer without account setup:
   - Type: sip:echo@sip.linphone.org (Echo test - verifies 2-way microphone & audio playback)
   - Type: sip:music@sip.linphone.org (Music test - verifies downlink RTP stream)

2. Or configure a free Linphone test account in Settings:
   - Domain: sip.linphone.org
   - Port: 5060
   - Transport: UDP
   - Test Echo Call: sip:echo@sip.linphone.org
```

---

## 🛡️ 4. ProGuard & Minification Rules

Liblinphone relies heavily on native JNI C++ libraries. ProGuard rules in `app/proguard-rules.pro` protect JNI bindings from being obfuscated or stripped during release builds:

```proguard
# Liblinphone SDK JNI Preservation
-keep class org.linphone.core.** { *; }
-keep interface org.linphone.core.** { *; }
-dontwarn org.linphone.core.**

# Google libphonenumber
-keep class com.google.i18n.phonenumbers.** { *; }
```

---

## 📋 5. Pre-Submission Checklist

- [ ] `versionCode` in `app/build.gradle.kts` incremented by +1.
- [ ] `versionName` is pure semantic version (e.g. `"1.0.6"`).
- [ ] Built and signed using production `.jks` (Release variant).
- [ ] Unit tests pass: `.\gradlew test`.
- [ ] App tested on physical Android device for outgoing & incoming audio.
- [ ] 512×512 icon attached (`play_store_512.png`).
- [ ] At least 2 clear app screenshots attached in console.
- [ ] Privacy Policy URL supplied.
