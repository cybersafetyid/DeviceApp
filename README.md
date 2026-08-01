# Enterprise Bus Validator App

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-1.9.0+-blue.svg?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack_Compose-Latest-4285F4.svg?logo=android" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/Architecture-Clean%20%7C%20MVI-brightgreen.svg" alt="Architecture">
  <img src="https://img.shields.io/badge/Offline-First-orange.svg" alt="Offline First">
  <img src="https://img.shields.io/badge/Security-High-red.svg" alt="Security">
</p>

## Overview

The **Enterprise Bus Validator App** is a highly resilient, fully modular Android application designed specifically for enterprise transit hardware. Built with Kotlin and Jetpack Compose, this app provides offline-first ticketing, multi-operator feature toggles, multi-vendor hardware SDK support, and uncompromising security protocols.

It is engineered to run 24/7 on dedicated validator devices (such as E60, Q6, Z90), maintaining extreme reliability, self-healing capabilities, and low resource utilization even on non-touchscreen setups and heavily locked-down environments (GMS & non-GMS).

## 🚀 Key Features

* **Modular Architecture**: Strictly enforced separation of concerns, ensuring high scalability across multiple hardware vendors and transit operators.
* **Offline-First Synchronization**: Built-in Auto Sync Engine with Scheduled Backup Uploader and a Persisted Time Checkpoint Ledger.
* **Multi-Vendor Hardware API**: HAL interface contracts allowing easy swapping of hardware drivers (NFC, SAM, Serial RS232, Scanner, GPS, Sound, LED).
* **Zero-Loss Payment Engine**: APDU transaction pipelines designed to never lose a transaction.
* **Extreme Reliability & Security**:
    * Zero-Memory-Leak Policy.
    * Multi-Layer Time Validation & Monotonic Drift Guard.
    * Encrypted local database (Room) and logs.
* **Tailored UI (Jetpack Compose)**:
    * Zero-Popup Policy: Ensuring stability on non-touchscreen devices.
    * Physical Keypad Support: Full MVI State-Screen architecture navigatable via physical keys.

## 🏗️ Project Structure

```text
DeviceApp/
├── app/                      # Application entry point and Dependency Injection (Hilt/Koin) setup.
├── core/                     # Foundational libraries and shared logic.
│   ├── common/               # Shared utilities, extensions, and constants.
│   ├── model/                # Domain business models, config objects, state enums.
│   ├── database/             # Encrypted Room DB & Persisted Time Checkpoint Ledger.
│   ├── network/              # Ktor Client, NTP Time Sync, MQTT Telemetry/Push.
│   ├── security/             # Multi-Layer Time Validation & Monotonic Drift Guard, Encrypted Logger.
│   ├── hardware-api/         # HAL interface contracts (NFC, SAM, Serial, Scanner, etc.).
│   ├── hardware-drivers/     # Vendor-specific implementations (E60, Q6, Z90, etc.).
│   ├── payment/              # Zero-Loss Payment Engine, APDU transaction pipelines.
│   ├── sync/                 # Offline-First Auto Sync Engine & Scheduled Backup Uploader.
│   ├── location/             # GPS NMEA Atomic Time Extractor & Location Provider.
│   └── devicemanager/        # Watchdog, Self-Healing, Remote Control, Root OTA.
└── feature/                  # User-facing features and Compose screens.
    ├── validator/            # Main validator UI, Splash initialization, payment flows.
    ├── diagnostic/           # Hardware Self-Test state screens.
    └── settings/             # Operator & Vendor Configuration UI.
```

## 🛠️ Performance Optimizations

To handle robust enterprise requirements 24/7:
* **Byte Buffer Pooling:** Network and serial communication utilize object pooling for byte arrays to eliminate garbage collection pauses.
* **Single-Threaded IO:** Hardware communication happens on dedicated, limited-parallelism coroutine dispatchers (e.g., `Dispatchers.IO.limitedParallelism(1)`) to prevent port conflicts and race conditions.
* **Strict Lifecycle Management:** Specifically engineered to manage hardware driver callbacks and serial port listeners without leaks.

## 📄 Documentation

For further details on the system design, check out our [Architecture Blueprint](docs/architecture.md).
