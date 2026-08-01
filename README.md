# Enterprise Multi-Operator Android Bus Validator System 🚌

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-1.9.0+-blue.svg?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack_Compose-Latest-4285F4.svg?logo=android" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/Architecture-Clean%20%7C%20MVI-brightgreen.svg" alt="Architecture">
  <img src="https://img.shields.io/badge/Offline-First-orange.svg" alt="Offline First">
  <img src="https://img.shields.io/badge/Security-High-red.svg" alt="Security">
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License">
</p>

## Overview

A production-ready, clean, scalable, fully modular, and maintainable Android application designed for **Enterprise Bus Validator Devices**. Built with **Kotlin** and **Jetpack Compose** (State-Screen architecture without popups), this application is engineered to run 24/7 on dedicated validator devices (such as E60, Q6, Z90).

It prioritizes extreme reliability, offline-first payment pipelines, and multi-vendor hardware compatibility in both GMS and Non-GMS environments.

---

## 🔥 Core Capabilities

### 1. Payment & Fare Engine
* **Offline-First Zero-Loss Payment Engine:** Guaranteed transaction integrity.
* **Double Deduct Safeguard & Anti-Passback:** Prevents accidental dual-taps and fare evasion.
* **Dynamic Intermodal Fare & Promo Engine:** Rule-based calculation by Time, Profile, and Bank Issuer.
* **Double Fare Validation Safeguard:** Ensures robust bank-level settlement.

### 2. Time & Security Engine
* **Deep-Researched Multi-Layer Time Validation & Monotonic Drift Guard Engine:** Protects against offline time-tampering.
* **Bank Settlement Time Compliance Gate:** Ensures valid timestamps for banking operations.
* **Encrypted Storage & High-Performance Logging:** SQLCipher-backed Room database with encrypted file logging.
* **Scheduled Encrypted Log/Database Backup Engine:** Secure data retention and recovery.

### 3. Hardware & Telemetry
* **Multi-Vendor Hardware Abstraction:** HAL interface for NFC, SAM, Serial RS232, QR Scanner, Sound, LED, and GPS.
* **Realtime MQTT GPS Telemetry:** Live fleet tracking and vehicle positioning.
* **MQTT/FCM Push Notification Engine:** Instant QRIS (QR code) payment status updates.
* **GMS / Non-GMS Dual Support:** Compatible with locked-down AOSP enterprise devices.

### 4. UI/UX & Navigation (Jetpack Compose)
* **International Public Transit Standard Dashboard UI:** Inspired by VDV / ITxPT layout paradigms.
* **Physical Keypad Navigation:** Full MVI architecture navigable via hardware keys—No touch required.
* **Zero-Popup Policy:** Absolute screen ownership; all states (Success, Fail, Untrusted Time) are rendered as full screens.
* **Interactive Initialization SplashScreen & Terminal Parameter Provisioning:** Smooth startup and config sync.

### 5. Stability & Diagnostics
* **24/7 Ultra-Low CPU/RAM Memory Optimization:** Zero-leak coroutine state management and byte-buffer pooling.
* **Self-Healing Watchdog & Auto-Recovery (Anti-Hang):** Process recovery logic for unattended hardware.
* **Remote Management & Control Engine:** OTA triggers and parameter updates.
* **Root-Assisted Silent OTA & Hardware Diagnostics:** Built-in hardware test suites for field engineers.

---

## 🏗️ Architecture Blueprint

The application enforces a strictly separated multi-module architecture:

```text
DeviceApp/
├── app/                      # Application entry point and DI (Hilt)
├── core/                     # Foundational libraries and shared logic
│   ├── common/               # Shared utilities, extensions
│   ├── model/                # Domain models, enums
│   ├── database/             # Encrypted Room DB & Persisted Time Ledger
│   ├── network/              # Ktor Client, NTP Time Sync, MQTT Telemetry
│   ├── security/             # Monotonic Drift Guard, Encrypted Logger
│   ├── hardware-api/         # HAL interface contracts (NFC, SAM, Serial, etc.)
│   ├── hardware-drivers/     # Vendor-specific implementations (E60, Q6, Z90)
│   ├── payment/              # Zero-Loss Payment Engine, APDU pipelines
│   ├── sync/                 # Offline-First Auto Sync Engine & Backup
│   ├── location/             # GPS NMEA Atomic Time Extractor
│   └── devicemanager/        # Watchdog, Self-Healing, Root OTA
└── feature/                  # User-facing features and Compose screens
    ├── validator/            # Main UI, Splash, Payment flows
    ├── diagnostic/           # Hardware Self-Test state screens
    └── settings/             # Operator Configuration UI
```

For more details on the implementation plan, please see [P0-Foundation Plan](plans/P0-Foundation.md).

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Iguana / Jellyfish (or latest)
- JDK 17
- Access to specific Vendor SDKs (placed in `libs/vendor-sdk/`)

### Build Instructions
1. Clone the repository.
2. Provide necessary properties in `local.properties` (e.g., API URLs, MQTT credentials).
3. Sync Gradle and build the `:app` module.

---

## 🤝 Contributing
We welcome contributions to the project. Please see the [CONTRIBUTING.md](CONTRIBUTING.md) file for guidelines on how to submit issues, feature requests, and pull requests.

## 📄 License
This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
