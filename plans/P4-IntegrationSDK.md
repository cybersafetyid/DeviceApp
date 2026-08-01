# Integration of E60Q and E60V2 SDKs and Hardware Diagnostics

This document outlines the proposed implementation plan to integrate the E60Q and E60V2 SDKs, replace mock/dummy hardware drivers with real implementations, and update the diagnostic screen to reflect actual hardware states.

## User Review Required

> [!CAUTION]
> Integrating native SDKs involves invoking JNI methods which may crash the app if the physical hardware is not present or permissions are missing. Please ensure the target devices are actual E60Q/E60V2 hardware.

## Open Questions

> [!WARNING]
> 1. Are there specific initialization parameters (e.g., serial port paths or baud rates) for `sdkJni.sdkInit()` or should we use default null/empty arrays?
> 2. For the hardware diagnostic screen, how should we expose the diagnostic state from the Driver Adapters to the Compose UI (e.g., ViewModel using StateFlow)?

## Proposed Changes

---

### Detailed SDK Usage & Error Handling

To properly utilize the `com.lenz.e60qsdk` APIs for Type A, Type B, and Felica cards, as well as SAM APDU transmission, the integration must include robust error and null handling:
- **Initialization**: Always check return codes (usually `0` for success) when calling `sdkJni.getInstance().sdkInit()` and `RfCardDriver.getInstance().open()`.
- **Card Detection (Type A, B, Felica, Mifare)**: Use `RfCardDriver.getInstance().searchCard(timeout)` to detect cards. The result `RfCardInfo` will indicate the card type. For Mifare cards, implement sector authentication (`mifareAuthenticate`) and read/write blocks (`mifareReadBlock`, `mifareWriteBlock`) using `sdkJni.getInstance()`. If `RfCardInfo` is null or `getSearchResult()` indicates an error, handle it gracefully by skipping processing.
- **Card APDU Transmit**: Use `RfCardDriver.getInstance().apduExchange(apduBytes)`. Handle cases where the returned `byte[]` is null or empty, throwing clear exceptions or mapping to failure codes.
- **SAM Initialization & APDU**: Use `ICCard` or `sdkJni.getInstance().iccardReset()` to power on the SAM module. Use `sdkJni.getInstance().iccardApdu()` to send commands, validating the status code and response buffer lengths.

---

### Hardware Driver Adapters

We will modify the `E60DriverAdapters.kt` to actually instantiate the SDK classes and invoke their methods.

#### [MODIFY] [E60DriverAdapters.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/hardware-drivers/src/main/java/com/enterprise/busvalidator/core/hardware/drivers/E60DriverAdapters.kt)
- **E60QDriverAdapter**:
  - `startCardListening`: Call `RfCardDriver.getInstance().open()`. Start a background polling loop utilizing `RfCardDriver.getInstance().searchCard()`.
    - Check the returned `RfCardInfo`. Determine the card type (Type A, B, or Felica) and extract the UID safely.
    - Setup an APDU handler lambda that calls `RfCardDriver.getInstance().apduExchange()`. If the result is null, return a custom error APDU (e.g., `6F 00`).
  - `stopCardListening`: Stop the polling loop and call `RfCardDriver.getInstance().close()`.
  - `powerOnSamSlot`: Call `sdkJni.getInstance().iccardReset(slotIndex, ...)`. Ensure return code checking and graceful failure.
  - `transmitSamApdu`: Call `sdkJni.getInstance().iccardApdu(slotIndex, ...)`. Implement robust null checking for response buffers.
  - `setLedSuccess` / `setLedFailed`: Use `Led().set(...)` with corresponding GPIO states.
  - `playSound`: Use `Beeper.getInstance().beep(int)` for audio feedback.
- **E60V2DriverAdapter**:
  - Implement similar actual calls matching the `com.lenz.e60qsdk` classes (which are shared in E60V2 aar). Ensure Type A/B/Felica distinction is preserved.
  - Implement USB integration (e.g. `UsbControl`) for specific E60V2 barcode/hardware accessories if necessary.

---

### Device Management & System APIs

We will implement a `DeviceManager` layer using the `sdkJni` and `LenzSystemManager` to handle advanced hardware control.

#### [NEW] [LenzDeviceManager.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/devicemanager/src/main/java/com/enterprise/busvalidator/core/devicemanager/LenzDeviceManager.kt)
- **Restart & Power**: Invoke `LenzSystemManager.reboot()` and `LenzSystemManager.shutdown()`.
- **Install/Uninstall App**: Invoke `LenzSystemManager.setInstallApkPath()` for OTA updates and `uninstallApp()`.
- **Firmware & Versions**: Expose functions returning `sdkJni.getInstance().m3KernelVer()`, `m3BootVer()`, and `sdkVersion()`. Implement firmware OTA via `sdkJni.getInstance().m3KernelUpdate()`.
- **Debug Tools**: Use `sdkJni.getInstance().systemCmdExec()` to run internal diagnostic bash commands if needed.

---

### Auto Boot (Kiosk/Daemon Mode)

The application MUST automatically run when the device boots, without fail.

#### [MODIFY] [AndroidManifest.xml](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/app/src/main/AndroidManifest.xml)
- Add `RECEIVE_BOOT_COMPLETED` permission.
- Register a `BootReceiver` that listens to `android.intent.action.BOOT_COMPLETED` and `android.intent.action.LOCKED_BOOT_COMPLETED`.
- Configure the main Activity as a `HOME` launcher category so it auto-starts and prevents user exit if set as the default launcher.

#### [NEW] [BootReceiver.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/app/src/main/java/com/enterprise/busvalidator/app/BootReceiver.kt)
- BroadcastReceiver that triggers an explicit Intent to start the main Application Activity immediately upon device boot.
- If supported by the device, we will also invoke `LenzSystemManager.startDaemonApp("com.enterprise.busvalidator")` during initialization to ensure the system watchdog restarts the app if it crashes.

---

### Hardware Diagnostic Feature

We will update the Diagnostic UI to query real hardware statuses.

#### [NEW] [HardwareDiagnosticViewModel.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/feature/diagnostic/src/main/java/com/enterprise/busvalidator/feature/diagnostic/HardwareDiagnosticViewModel.kt)
- Create a ViewModel that interacts with a `DiagnosticRepository` or the Hardware APIs directly to check the status of NFC, SAM, Audio, LED, and Location.

#### [MODIFY] [HardwareDiagnosticScreen.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/feature/diagnostic/src/main/java/com/enterprise/busvalidator/feature/diagnostic/HardwareDiagnosticScreen.kt)
- Bind the Compose UI to the `HardwareDiagnosticViewModel`.
- Replace the hardcoded `diagnosticResults` with dynamic states observed from the ViewModel.
- When "JALANKAN SELF-DIAGNOSTIC" is clicked, trigger a refresh of all hardware component tests.

---

### Unit Testing

We will create unit tests for the driver adapters to ensure proper method routing, mocking the native SDK calls using MockK where appropriate.

#### [NEW] [E60QDriverAdapterTest.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/hardware-drivers/src/test/java/com/enterprise/busvalidator/core/hardware/drivers/E60QDriverAdapterTest.kt)
- Setup MockK for `RfCardDriver`, `Led`, `Beeper`, and `sdkJni`.
- Verify that `startCardListening` calls `open()` on the RF driver.
- Verify LED and Sound methods invoke the correct native wrappers.

#### [NEW] [E60V2DriverAdapterTest.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/hardware-drivers/src/test/java/com/enterprise/busvalidator/core/hardware/drivers/E60V2DriverAdapterTest.kt)
- Similar tests for the E60V2 specific integrations.

## Verification Plan

### Automated Tests
- `./gradlew :core:hardware-drivers:testDebugUnitTest`

### Manual Verification
- Build and deploy the application to a physical E60Q and E60V2 device.
- Open the Diagnostics Screen and tap "JALANKAN SELF-DIAGNOSTIC".
- Verify that the UI correctly displays the actual status of the NFC Antenna, SAM Module, LED, and Speaker.
