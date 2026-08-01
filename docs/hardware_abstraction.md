# Hardware Abstraction Layer (HAL)

## Objective
Enterprise bus validators run on extremely fragmented and diverse OEM hardware (e.g., Telpo, ZCS, Newland, MSI). Hardcoding SDK calls into feature logic breaks maintainability. The HAL strictly abstracts all hardware interactions.

## Core Modules

### 1. `:core:hardware-api`
Defines standard Kotlin interfaces for all hardware capabilities. The domain and UI layers only ever depend on these interfaces.
- `NfcDriver`: `connect()`, `transceive(apdu: ByteArray)`, `disconnect()`, `waitForCard()`
- `SamDriver`: `powerOn(slot: Int)`, `transceive(apdu: ByteArray)`
- `SerialDriver`: `openPort(path: String, baudRate: Int)`, `write()`, `readFlow(): Flow<ByteArray>`
- `ScannerDriver`: `enable()`, `disable()`, `onBarcodeScanned(): Flow<String>`
- `LedDriver`: `setGreen()`, `setRed()`, `setBlue()`, `turnOff()`
- `AudioDriver`: `playSuccess()`, `playError()`, `playWarning()`
- `KeypadDriver`: `onKeyPressed(): Flow<KeyEvent>` (Up, Down, Enter, Esc)

### 2. `:core:hardware-drivers`
Contains the concrete implementations utilizing OEM-specific SDKs.

- **Implementations:**
  - `TelpoHardwareModule`
  - `ZcsHardwareModule` (Z90, Z91)
  - `NewlandHardwareModule` (Q6)
  - `GenericAndroidHardwareModule` (Uses standard Android APIs for non-specialized devices)

- **DeviceModelDetector & VendorDriverFactory:**
  Upon initialization, `DeviceModelDetector` reads `android.os.Build.MODEL` and `Build.MANUFACTURER` to determine the physical device. The `VendorDriverFactory` then injects the correct concrete driver implementation satisfying the `:core:hardware-api` interfaces into the DI graph.

## Coroutine Safety & Threading
Hardware SDKs are notoriously blocking and thread-unsafe. The HAL guarantees:
- All driver calls execute on dedicated IO dispatchers (`Dispatchers.IO.limitedParallelism(1)` for serial lines).
- Graceful timeouts using `withTimeout()` to prevent a frozen driver from deadlocking the app.
