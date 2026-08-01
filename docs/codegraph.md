# Codegraph

This document maps the most important classes and ownership boundaries in the current implementation.

## Application Graph

```mermaid
flowchart TD
    BusValidatorApplication --> RuntimePermissionProvisioner
    BusValidatorApplication --> AppHealthWatchdog
    BusValidatorApplication --> MqttTelemetryClient
    BusValidatorApplication --> EncryptedLogger
    BusValidatorApplication --> LenzSystemManager["LenzSystemManager.Default().startDaemonApp"]

    MainActivity --> InitializationPipelineManager
    MainActivity --> PaymentEngine
    MainActivity --> VendorDriverFactory
    MainActivity --> BusLocationManager
    MainActivity --> RemoteControlManager
    MainActivity --> TransactionDao
    MainActivity --> RuntimePermissionProvisioner

    MainActivity --> InteractiveInitializationSplashScreen
    MainActivity --> ValidatorDashboardScreen
    MainActivity --> SettingsScreen
    MainActivity --> HardwareDiagnosticScreen
```

## Payment Graph

```mermaid
flowchart TD
    PaymentEngine --> MultiSourceTimeSyncEngine
    PaymentEngine --> TransactionDao
    PaymentEngine --> LedDriver
    PaymentEngine --> AudioDriver
    PaymentEngine --> BankApduManager
    PaymentEngine --> QrisPaymentEngine

    BankApduManager --> BankApduHandler
    BankApduHandler --> MandiriEmoneyApdu
    BankApduHandler --> BcaFlazzApdu
    BankApduHandler --> BriBrizziApdu
    BankApduHandler --> BniTapCashApdu
    BankApduHandler --> BankDkiJakCardApdu
    BankApduHandler --> BankNobuApdu
    BankApduHandler --> KmtFelicaApdu

    PaymentEngine --> TransactionRecord
    TransactionRecord --> TransactionEntity
    TransactionEntity --> ValidatorDatabase
```

## Hardware Graph

```mermaid
flowchart TD
    AppModule --> VendorDriverFactory
    VendorDriverFactory --> DeviceModelDetector
    VendorDriverFactory --> E60QDriverAdapter
    VendorDriverFactory --> E60V2DriverAdapter
    VendorDriverFactory --> E60SerialAdapter
    VendorDriverFactory --> DefaultNfcDriver
    VendorDriverFactory --> DefaultSamDriver
    VendorDriverFactory --> DefaultSerialDriver
    VendorDriverFactory --> DefaultScannerDriver
    VendorDriverFactory --> DefaultLedDriver
    VendorDriverFactory --> DefaultAudioDriver
    VendorDriverFactory --> DefaultKeypadDriver

    E60QDriverAdapter --> NfcDriver
    E60QDriverAdapter --> SamDriver
    E60QDriverAdapter --> ScannerDriver
    E60QDriverAdapter --> LedDriver
    E60QDriverAdapter --> AudioDriver

    E60V2DriverAdapter --> NfcDriver
    E60V2DriverAdapter --> SamDriver
    E60V2DriverAdapter --> ScannerDriver
    E60V2DriverAdapter --> LedDriver
    E60V2DriverAdapter --> AudioDriver

    E60SerialAdapter --> SerialDriver
```

## Security and Device Graph

```mermaid
flowchart TD
    RuntimePermissionProvisioner --> SuManager
    RuntimePermissionProvisioner --> DevicePolicyManager
    RuntimePermissionProvisioner --> EncryptedLogger

    MultiSourceTimeSyncEngine --> SuManager
    MultiSourceTimeSyncEngine --> EncryptedLogger

    DatabaseModule --> ValidatorDatabase
    DatabaseModule --> SQLCipher
    ValidatorDatabase --> TransactionDao

    DatabaseBackupManager --> SQLCipherDBFile["bus_validator_encrypted.db"]
    DatabaseBackupManager --> EncryptedBackup["db_backup_<timestamp>.db.enc"]
    EncryptedLogger --> EncryptedLogs["filesDir/encrypted_logs"]

    RemoteControlManager --> MqttTelemetryClient
    RemoteControlManager --> SuManager
    LenzDeviceManager --> LenzSDK["LENZ SDK"]
```

## UI Graph

```mermaid
flowchart TD
    ValidatorDashboardScreen --> TopTelemetryHeader
    ValidatorDashboardScreen --> BiskitaIdleContent
    ValidatorDashboardScreen --> CitraIdleContent
    ValidatorDashboardScreen --> SurabayaIdleContent
    ValidatorDashboardScreen --> ProcessingStateContent
    ValidatorDashboardScreen --> SuccessStateContent
    ValidatorDashboardScreen --> CardAlreadyTappedContent
    ValidatorDashboardScreen --> InsufficientBalanceContent
    ValidatorDashboardScreen --> UntrustedTimeContent
    ValidatorDashboardScreen --> SimulatorActionBar

    SettingsScreen --> OperatorPresets
    SettingsScreen --> VendorDeviceModel

    HardwareDiagnosticScreen --> HardwareDiagnosticViewModel
    HardwareDiagnosticViewModel --> NfcDriver
    HardwareDiagnosticViewModel --> SamDriver
    HardwareDiagnosticViewModel --> LedDriver
    HardwareDiagnosticViewModel --> AudioDriver
    HardwareDiagnosticViewModel --> ScannerDriver
    HardwareDiagnosticViewModel --> SerialDriver
    HardwareDiagnosticViewModel --> LenzDeviceManager
```

## Hotspots

- `MainActivity`: currently owns routing, init orchestration, payment simulator, telemetry state derivation, and key handling. Extract ViewModel/use cases when production card listening is wired.
- `DatabaseModule`: destructive migration must be removed before production data.
- `RuntimePermissionProvisioner`: powerful system operations; keep command list allowlisted.
- `VendorDriverFactory`: every new vendor must be registered here and tested.
- `PaymentEngine`: all new payment methods must preserve mutex, time gate, persistence, and feedback semantics.
