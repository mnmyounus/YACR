# YACR — Your All Call Recorder

<p align="center">
  <img src="docs/logo.png" width="120" alt="YACR Logo" />
</p>

<p align="center">
  <strong>Enterprise-grade, 100% offline call recording for Android</strong><br/>
  Developer: <strong>MNM YOUNUS</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-10--15-3DDC84?logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-2024-4285F4?logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/Encryption-AES--GCM--256-E53935" />
  <img src="https://img.shields.io/badge/Network-Zero%20Internet-success" />
  <img src="https://img.shields.io/badge/License-Personal%20Use-blue" />
</p>

---

## Overview

YACR is a production-ready Android call recording application built with Clean Architecture, modern Jetpack libraries, and enterprise-grade security. It records **cellular** and **VoIP** calls (WhatsApp, Signal, Telegram, Viber, Messenger, Skype, Google Meet, Zoom), encrypts every recording with **AES-GCM-256** via the Android Keystore, and stores everything locally — **no internet permission, ever**.

---

## Architecture

```
com.mnmyounus.yacr/
├── data/                          # Data layer
│   ├── crypto/
│   │   ├── KeystoreManager        # Android Keystore AES-GCM-256 key lifecycle
│   │   ├── EncryptionPipeline     # Chunked streaming encryption / WAV export
│   │   └── EncryptingOutputStream # Per-chunk GCM write stream
│   ├── local/
│   │   ├── database/              # Room DB (YACRDatabase, RecordingDao, RecordingEntity)
│   │   └── datastore/             # Jetpack DataStore preferences
│   └── repository/                # RecordingRepositoryImpl
│
├── domain/                        # Domain layer (zero framework dependency)
│   ├── model/                     # Recording, CallType, CallEvent
│   ├── repository/                # RecordingRepository interface
│   └── usecase/                   # GetAll, Search, Delete, Decrypt, ToggleFlag
│
├── presentation/                  # Presentation layer
│   ├── navigation/                # YACRNavHost, Screen sealed class
│   ├── screens/
│   │   ├── home/                  # HomeScreen + HomeViewModel
│   │   ├── player/                # PlayerScreen + PlayerViewModel (ExoPlayer)
│   │   └── settings/              # SettingsScreen + SettingsViewModel
│   ├── theme/                     # Material3 dark theme (Color, Type, Theme)
│   └── MainActivity               # Single activity, biometric gate
│
├── service/
│   ├── AudioRecordingEngine       # AudioRecord + streaming encryption
│   ├── CallRecorderService        # Foreground service (lifecycle manager)
│   ├── YACRAccessibilityService   # VoIP call detection (WhatsApp, Signal, etc.)
│   ├── PhoneStateReceiver         # Cellular call detection
│   └── BootReceiver               # Startup initialization
│
└── di/                            # Hilt DI modules
    ├── AppModule
    ├── CryptoModule
    ├── DatabaseModule
    └── RepositoryModule
```

---

## Security Architecture

### AES-GCM-256 Encryption-Before-Storage

```
AudioRecord (PCM bytes)
        │
        ▼  (in memory, never on disk as plaintext)
EncryptingOutputStream
        │  For each buffer chunk:
        │    1. Request fresh Cipher from KeystoreManager (new random 96-bit IV)
        │    2. cipher.doFinal(plaintextChunk) → ciphertext + 16-byte GCM tag
        │    3. Write: [IV 12B][length 4B][ciphertext + tag]
        │
        ▼
  .yacr file on disk (only encrypted bytes reach storage)
```

### File Format

```
┌────────────────────────────────────────────────────────┐
│  YACR Encrypted Audio File (.yacr)                     │
├────────────┬───────────────────────────────────────────┤
│  Header    │ [YACR 4B][version 2B][sampleRate 4B]      │
│            │ [channels 2B][bitDepth 2B]                 │
├────────────┼───────────────────────────────────────────┤
│  Chunk 1   │ [IV 12B][ciphertextLen 4B][ciphertext]    │
│  Chunk 2   │ [IV 12B][ciphertextLen 4B][ciphertext]    │
│  ...       │  (each chunk independently encrypted)     │
└────────────┴───────────────────────────────────────────┘
```

### Zero Internet Policy

- `android.permission.INTERNET` is **absent** from `AndroidManifest.xml`
- `network_security_config.xml` explicitly denies all network connections
- `data_extraction_rules.xml` disables all cloud backup mechanisms
- The application is **physically incapable** of transmitting data

---

## Supported Applications

| Application | Detection Method |
|-------------|------------------|
| Cellular (PSTN) | `TelephonyManager` `PHONE_STATE` broadcast |
| WhatsApp | `AccessibilityService` window class + content nodes |
| Signal | `AccessibilityService` `WebRtcCallActivity` |
| Telegram | `AccessibilityService` `VoIPActivity` |
| Viber | `AccessibilityService` `CallActivity` |
| Messenger | `AccessibilityService` `RtcActivity` |
| Skype / Teams | `AccessibilityService` `CallingActivity` |
| Google Meet | `AccessibilityService` `MeetingActivity` |
| Zoom | `AccessibilityService` `ZoomMeetingActivity` |

---

## Setup & Build

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 35

### Local Build

```bash
# Clone the repository
git clone https://github.com/mnmyounus/yacr.git
cd yacr

# Create keystore for signing (first time only)
keytool -genkey -v \
  -keystore yacr-release.keystore \
  -alias yacr-release-key \
  -keyalg RSA -keysize 4096 \
  -validity 36500

# Configure signing
cp keystore.properties.template keystore.properties
# Edit keystore.properties with your keystore path and passwords

# Build debug APK
./gradlew assembleDebug

# Build signed release APK
./gradlew assembleRelease
```

### CI/CD (GitHub Actions)

Add the following secrets to your GitHub repository:

| Secret | Description |
|--------|-------------|
| `SIGNING_KEYSTORE_BASE64` | `base64 yacr-release.keystore` output |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

Push a version tag to trigger a GitHub Release:
```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## Permissions

| Permission | Purpose |
|------------|---------|
| `READ_PHONE_STATE` | Detect call state changes |
| `READ_CALL_LOG` | Label recordings with contact names |
| `PROCESS_OUTGOING_CALLS` | Capture outgoing call numbers |
| `RECORD_AUDIO` | Capture call audio |
| `FOREGROUND_SERVICE` | Persistent recording service |
| `FOREGROUND_SERVICE_MICROPHONE` | Android 10+ microphone foreground type |
| `POST_NOTIFICATIONS` | Recording active notification |
| `RECEIVE_BOOT_COMPLETED` | Auto-start monitoring on reboot |
| `USE_BIOMETRIC` | Optional app lock |

**Explicitly absent:** `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`

---

## Android 13+ Restricted Settings

On Android 13+ (API 33), APKs sideloaded outside the Play Store require the user to explicitly grant "Restricted Settings" before enabling Accessibility Services:

1. Install YACR
2. Go to **Settings → Apps → YACR → ⋮ (three dots) → Allow restricted settings**
3. Then go to **Settings → Accessibility → YACR VoIP Monitor** and enable it

---

## YACR Helper App (Advanced)

For deeper audio routing on rooted or privileged devices, a **YACR Helper** companion app (signed with the same certificate) bridges to system APIs unavailable to regular apps. The Helper communicates exclusively via local broadcast — no network involvement.

This module is out of scope for the base YACR release but the architecture supports it via the IPC bridge defined in `YACRAccessibilityService.getRestrictedSettingsIntent()`.

---

## License

For personal use only by **MNM YOUNUS**.
