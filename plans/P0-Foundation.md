# Implementation Plan - Enterprise Multi-Operator Android Bus Validator System

A production-ready, clean, scalable, fully modular, and maintainable Android application designed for enterprise bus validator devices. Built with **Kotlin**, **Jetpack Compose** (State-Screen architecture without popups), **Clean Architecture**, **Deep-Researched Multi-Layer Time Validation & Monotonic Drift Guard Engine**, **Bank Settlement Time Compliance Gate**, **International Public Transit Standard Dashboard UI (VDV/ITxPT layout)**, **24/7 Ultra-Low CPU/RAM Memory Optimization**, **Self-Healing Watchdog & Auto-Recovery (Anti-Hang)**, **Remote Management & Control Engine**, **Offline-First Zero-Loss Payment Engine**, **Double Deduct Safeguard & Anti-Passback**, **Dynamic Intermodal Fare & Promo Engine (Time, Profile, Bank Issuer)**, **Double Fare Validation Safeguard**, **Encrypted Storage & High-Performance Logging**, **Scheduled Encrypted Log/Database Backup Engine**, **Interactive Initialization SplashScreen & Terminal Parameter Provisioning**, **Multi-Vendor Hardware Abstraction (NFC, SAM, Serial RS232, QR, Sound, LED, GPS)**, **GMS/Non-GMS Dual Support**, **Physical Keypad Navigation**, **Realtime MQTT GPS Telemetry**, **MQTT/FCM Push Notification Engine for QRIS**, and **Root-Assisted Silent OTA & Hardware Diagnostics**.

---

## Technical Overview & Architecture Blueprint

```
+-----------------------------------------------------------------------------------+
|                                 App Module (:app)                                 |
|                       Dependency Injection Setup (Hilt / Koin)                   |
+-----------------------------------------------------------------------------------+
                                         |
     +-----------------------------------+-----------------------------------+
     |                                   |                                   |
+-----------------------+     +-----------------------+     +-----------------------+
|  feature:validator    |     |  feature:diagnostic   |     |   feature:settings    |
| (UI State Screens &   |     | (Hardware Self-Test   |     | (Operator & Vendor    |
| Keypad Event Handler) |     |  State Screens & UI)  |     |  Configuration UI)    |
+-----------------------+     +-----------------------+     +-----------------------+
     |                                   |                                   |
     +-----------------------------------+-----------------------------------+
                                         |
+-----------------------------------------------------------------------------------+
|                                 Domain Layer                                      |
| :core:model  - Business Models, Operator Config, Fare Rules, TimeConfidenceState  |
| :core:payment- Zero-Loss Payment Engine with Time Confidence Gate Guard           |
+-----------------------------------------------------------------------------------+
                                         |
+-----------------------------------------------------------------------------------+
|               Hardware Abstraction Layer (HAL) & Vendor Drivers                   |
+-----------------------------------------------------------------------------------+
| :core:hardware-api  - Hardware Driver Interfaces (NfcDriver, SamDriver,           |
|                       SerialDriver, ScannerDriver, LedDriver, AudioDriver)        |
| :core:hardware-drivers - Vendor Driver Registry (E60, Q6, Z90, A90, Telpo, MSI)   |
+-----------------------------------------------------------------------------------+
                                         |
+-----------------------------------------------------------------------------------+
|                            Core Infrastructure Modules                            |
| :core:database       - Encrypted Room DB & Persisted Time Checkpoint Ledger       |
| :core:sync           - Offline-First Auto Sync Engine & Scheduled Backup Uploader |
| :core:network        - Ktor Client, NTP Time Sync, MQTT Telemetry/Push            |
| :core:security       - Multi-Layer Time Validation Engine & Monotonic Drift Guard|
|                        AES-GCM Encrypted Logging, Root Executive, Keystore        |
| :core:location       - GPS NMEA Atomic Time Extractor & FusedLocation Provider    |
| :core:devicemanager  - 24/7 Watchdog, Self-Healing, Remote Control Engine, OTA   |
+-----------------------------------------------------------------------------------+
```

---

## Key Requirements & Architectural Solutions

### 1. Deep-Researched Multi-Layer Time Validation Engine (`:core:security` + `:core:location` + `:core:network`)
Time skew or clock jumps (backward/forward) cause severe **bank settlement rejections** (BCA, Mandiri, BNI, BRI, Bank DKI, Nobu, QRIS) and break SAM module APDU authentication.

We solve this using a **4-Layer Cryptographic & Monotonic Time Validation Engine**:

- **Layer 1: Multi-Source Time Synchronization Pipeline**:
  - **Source A (GPS NMEA Atomic Time)**: Extracts UTC atomic time directly from raw GPS NMEA sentences (`$GPRMC` / `$GPZDA`). Works 100% offline without cellular data.
  - **Source B (NTP Stratum-1/2 Pools)**: Queries `pool.ntp.org` / `time.google.com` via SNTP protocol over network connections.
  - **Source C (NITZ Cellular Network Time)**: Intercepts cell tower NITZ broadcast time from SIM card provider.
  - **Source D (Hardware RTC Node)**: Reads hardware RTC `/dev/rtc0` via JNI/Root.
- **Layer 2: Monotonic Drift Guard (`MonotonicTimeGuard`)**:
  - Leverages Android `SystemClock.elapsedRealtimeNanos()` (which never decreases and cannot be tampered with by users or system settings) to compute true elapsed time velocity:
    $$\Delta t_{real} = \text{elapsedRealtimeNanos}() - \text{lastReferenceNanos}$$
  - If system wall clock (`System.currentTimeMillis()`) diverges from $\Delta t_{real}$ by more than $\pm 5$ seconds, a **Clock Drift Anomaly** is flagged.
- **Layer 3: Persisted Monotonic Time Checkpoint Ledger**:
  - Every transaction and log commit writes an encrypted time checkpoint (`last_valid_utc_timestamp`) to Room DB.
  - Upon device boot or app startup, if `CurrentSystemTime < LastPersistedTimestamp`, the engine detects **Backward Time Tampering**.
- **Layer 4: Time Confidence Transaction Gate (`TimeConfidenceGate`)**:
  - **SECURE_SYNCED**: Verified by GPS/NTP/NITZ. Full card/QR transactions allowed.
  - **MONOTONIC_VALIDATED**: Verified by Monotonic Hardware offset since last secure sync. Full transactions allowed.
  - **TIME_UNTRUSTED**: Detected backward jump, unverified clock, or skew > 30s.
    - **Action**: All payment transactions are **INSTANTLY SUSPENDED** to protect public money.
    - State screen displays `UNTRUSTED_SYSTEM_TIME` with warning sound and red LED.
    - Auto-recovery: Silent root command `su -c date -s ...` updates system clock from GPS NMEA atomic time.

### 2. International Public Transit Standard Dashboard UI (VDV / ITxPT Layout)
- Live header with Clock, Bus Code, GPS 3D Fix, SIM 4G Signal, Offline/Online Badge, Hardware HAL status (`RS232: OK`, `SAM: OK`, `NFC: OK`), Active Route, Base Fare, Today's Counter, Last Transaction, Pending Sync Queue, and Version footer.

### 3. 24/7 Ultra-Low CPU/RAM Footprint & Memory Management Strategy
- Zero-Memory-Leak Policy, byte buffer pooling, Compose recomposition optimization, baseline profile.
- Single-threaded IO coroutines (`Dispatchers.IO.limitedParallelism(4)`).

### 4. Self-Healing Watchdog Engine (Anti-Hang / Anti-Freeze) (`:core:devicemanager`)
- `AppHealthWatchdog`: ANR & deadlock detection with auto-restart / root reboot (`su -c reboot`).
- Hardware Watchdog Kicker via RS232 / GPIO sysfs.

### 5. Secure Remote Management & Remote Control Engine (`:core:devicemanager` + `:core:network`)
- Remote MQTT commands: `cmd_reboot`, `cmd_restart_app`, `cmd_fetch_logs`, `cmd_update_config`, `cmd_ota_update`, `cmd_remote_screen_capture`, `cmd_clear_cache`.

### 6. Offline-First Zero-Loss Payment & Security Engine (`:core:payment` + `:core:database`)
- Atomic 6-step APDU card deduction pipeline with rollback protection.
- Anti-Passback double-deduct protection per Card UID.
- Full audit logging with HMAC signature per record.

### 7. Dynamic Intermodal Fare & Multi-Tier Promo Rules Engine (`:core:payment` + `:core:model`)
- Distance-based fare + intermodal transfer discounts (MRT/LRT/Bus/Angkot).
- Promos: Time-based (off-peak), Passenger Profiles (Lansia/Student/PNS), and Bank Issuer promos.
- Double Fare Validation Safeguard (Layer 1 SAM/Card check + Layer 2 Mathematical bounds).

### 8. Interactive Initialization SplashScreen & Terminal Parameter Engine (`feature:validator` + `:core:devicemanager`)
- Sequential progress checklist (Keystore -> DB -> Hardware HAL -> Config Fetch [MID, TID, PINCODE, PROCESSING_CODE, SAM_ID, MARRIAGE_CODE, TAP_MODE] -> NTP/GPS Time Sync -> Health Test).

---

## User Review Required

> [!IMPORTANT]
> **Base Workspace Setup & Gradle Versioning:**
> Since `/Volumes/Gorby/AndroidStudioProjects/DeviceApp` is currently empty, we will create the complete project structure from scratch using modern Gradle Version Catalogs (`libs.versions.toml`), Kotlin 2.x, Android Gradle Plugin 8.x+, Jetpack Compose, and Hilt Dependency Injection.
>
> Min SDK will be set to `21` (Android Lollipop) and Target SDK to `35` (Android 15/16) as requested.

---

## Proposed Changes & File Directory Structure

```
DeviceApp/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
├── libs/
│   └── vendor-sdk/
│       ├── e60/
│       ├── q6/
│       ├── zcs/
│       ├── a90/
│       ├── telpo/
│       └── msi/
├── app/
│   ├── build.gradle.kts
│   └── src/main/java/com/enterprise/busvalidator/
│       ├── BusValidatorApp.kt
│       ├── MainActivity.kt
│       └── di/
├── core/
│   ├── common/
│   ├── model/
│   ├── database/
│   ├── network/
│   ├── security/
│   ├── hardware-api/
│   ├── hardware-drivers/
│   ├── payment/
│   ├── sync/
│   ├── location/
│   └── devicemanager/
└── feature/
    ├── validator/
    ├── diagnostic/
    └── settings/
```

---

## Phase Breakdown for Project Implementation

### Phase 1: Foundation & Build Infrastructure
- Initialize Gradle Root, `libs.versions.toml`, Multi-Module configuration (`settings.gradle.kts`).
- Configure Kotlin 2.x, Compose Compiler, Hilt DI, Android MinSDK 21, TargetSDK 35.
- Setup `libs/vendor-sdk/` directory structure.

### Phase 2: Hardware Abstraction Layer (HAL) & Multi-Vendor Driver Factory
- `:core:hardware-api`: Interface contracts (`NfcDriver`, `SamDriver`, `SerialDriver`, `ScannerDriver`, `LedDriver`, `AudioDriver`, `KeypadDriver`).
- `:core:hardware-drivers`: `DeviceModelDetector` and `VendorDriverFactory` (`E60Q`, `E60V2`, `Q6`, `Z90`, `A90`, `Z91`, `TELPO`, `MSI`, `GENERIC`).

### Phase 3: Core Security, Multi-Layer Time Engine & Database
- `:core:security`:
  - `MultiSourceTimeSyncEngine` (SNTP, GPS NMEA Atomic Time, NITZ, Hardware RTC).
  - `MonotonicTimeGuard` (`SystemClock.elapsedRealtimeNanos()` velocity check).
  - `TimePersistedCheckpoint` (Room DB timestamp checkpoint ledger).
  - `TimeConfidenceGate` (Gate keeper suspending transactions when time is untrusted).
  - `EncryptedLogger` (AES-256-GCM binary log stream), Root Executor (`SuManager`), Keystore.
- `:core:database`: Encrypted Room Database (`ValidatorDatabase`) with SQLCipher.
- `:core:network`: Ktor/Retrofit client + SNTP Time Client + Terminal Config Fetcher + TLS MQTT Telemetry & Push.
- `:core:location`: GPS NMEA Atomic Time Extractor + Multi-provider Location Manager.

### Phase 4: Zero-Loss Payment Engine, Double Deduct Safeguard & Fare/Promo Rules
- `:core:payment`: Atomic APDU payment deduction with `TimeConfidenceGate` guard check, `AntiPassbackGuard`, `DoubleFareValidator`, Intermodal fare calculator & Multi-tier promo engine, Indonesian Electronic Card engines (Flazz, TapCash, e-Money, Brizzi, JakCard, Nobu) & Dynamic QRIS push confirmation.

### Phase 5: 24/7 Watchdog, Self-Healing, Remote Control & Silent OTA
- `:core:devicemanager`: `AppHealthWatchdog`, `RemoteControlManager`, `InitializationPipelineManager`, Silent OTA Installer via Root.
- `:core:sync`: Offline-first ledger sync & Scheduled Backup Uploader.

### Phase 6: Feature Modules & International Transit Standard Jetpack Compose UI
- `:feature:validator`: Interactive Initialization SplashScreen, International Transit Standard Dashboard UI, Zero-Popup UI State Machine, Physical Keypad navigation.
- `:feature:diagnostic`: Hardware Health Diagnostic State Screen.
- `:feature:settings`: Operator Profile & Vendor Hardware Config UI.

---

## Verification & Quality Assurance Plan

### Automated Tests
- **Unit Tests**:
  - Test `MonotonicTimeGuard` detects wall-clock backward drift and forward jump anomalies (`./gradlew test`).
  - Test `TimeConfidenceGate` suspends payment when time state is `TIME_UNTRUSTED`.
  - Test GPS NMEA `$GPRMC` UTC time parsing.
  - Test `TimePersistedCheckpoint` rejects boot if current clock is less than last persisted timestamp.
  - Test Watchdog heartbeat failure detection & restart triggers.
  - Test Remote Control MQTT command signature verification.
  - Test Atomic APDU deduction & rollback protection.
  - Test Anti-Passback double-deduct protection.
  - Test Intermodal Fare calculation & Multi-tier Promo logic.
  - Test Double Fare Validation Safeguard.

### Hardware & Manual Verification
1. **Time Tamper & Settlement Safeguard Test**:
   - Manually change system time backward by 2 hours -> verify `MonotonicTimeGuard` & `TimePersistedCheckpoint` immediately set state to `TIME_UNTRUSTED`.
   - Verify payment screen suspends transactions and displays `UNTRUSTED_SYSTEM_TIME` error with red LED.
   - Verify GPS NMEA / NTP re-sync automatically restores time and silently updates RTC via Root, returning state to `SECURE_SYNCED`.
2. **International Transit Dashboard Display**: Verify top status bar renders live Clock, Bus Code, GPS satellite count, SIM 4G signal, Online/Offline badge, Serial/SAM/NFC status, Today's Counter, Last Transaction summary, and Pending Sync queue.
3. **24/7 Watchdog & Self-Healing Test**: Simulate main thread freeze -> verify Watchdog detects freeze within 10s and triggers auto-restart/reboot.
4. **Remote Management Commands**: Send MQTT command `cmd_reboot` / `cmd_fetch_logs` / `cmd_remote_screen_capture` -> verify validator executes action securely.
5. **Zero-Loss Payment & Double Deduct Test**: Tap same card twice rapidly -> verify 1st tap deducts fare, 2nd tap triggers `CardAlreadyTappedState` (Anti-Passback) without deducting money.
6. **Intermodal & Dynamic Promo Fare**: Test fare calculation for different passenger profiles (Lansia/Student/PNS) and bank issuers under off-peak/peak hours.
7. **Interactive SplashScreen & Config Fetch**: Boot app -> verify real-time task progress checklist on Compose SplashScreen.
8. **Encrypted Log & Scheduled Backup**: Verify AES log encryption and scheduled backup package integrity.
9. **MQTT GPS Tracking**: Stream GPS telemetry over MQTT with offline buffering.
10. **Realtime Push QR Payment**: Verify instant State Screen transition to `Success` upon QRIS payment push notification.
11. **Multi-Vendor SDK Switching**: Verify device auto-detection logic (`E60Q`, `E60V2`, `Q6`, `Z90`, `A90`, `Z91`, `TELPO`, `MSI`).
12. **Offline-First & Auto-Sync**: Verify automatic background batch sync upon network recovery.
13. **Keypad Navigation**: Test physical Key Up / Down / Enter / Esc button presses.
14. **Self-Diagnostic Test**: Run full diagnostic suite for NFC, SAM, Serial, Scanner, Audio, GPS, Network, and MQTT.
