# Architecture Blueprint

## System Overview
The Enterprise Bus Validator App is built on a clean, scalable, fully modular Android architecture utilizing Kotlin and Jetpack Compose. It is designed to run 24/7 on dedicated enterprise hardware with offline-first capabilities, prioritizing extreme reliability, security, and low resource utilization.

## Multi-Module Structure

The application is divided into several modules to enforce separation of concerns, improve build times, and enable high scalability across different hardware vendors and transit operators.

```
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

## UI Architecture (Jetpack Compose)
The UI is strictly based on a **State-Screen architecture** powered by Jetpack Compose.
- **Zero-Popup Policy:** To ensure absolute stability on non-touchscreen or heavily locked-down devices, there are no floating popups or dialogs. Every state (Success, Failed, Untrusted Time, Out of Service) is rendered as a full-screen or definitive UI state.
- **Physical Keypad Support:** State machines are tied to physical key events (Up, Down, Enter, Esc), allowing complete navigation without touch input.
- **MVI Pattern:** The UI layer observes immutable state streams (e.g., `StateFlow`) from ViewModels and dispatches intents/events back.

## 24/7 Stability & Memory Optimization
- **Zero-Memory-Leak Policy:** Strict lifecycle management, particularly for hardware driver callbacks and serial port listeners.
- **Byte Buffer Pooling:** Network and serial communication utilize object pooling for byte arrays to eliminate garbage collection pauses.
- **Single-Threaded IO:** Hardware communication happens on dedicated, limited-parallelism coroutine dispatchers (e.g., `Dispatchers.IO.limitedParallelism(1)`) to prevent port conflicts and race conditions.
