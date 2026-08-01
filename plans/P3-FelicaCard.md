# Implementation Plan: FeliCa KMT (Kartu Multi Trip) Integration

Integrate Sony FeliCa NFC card protocol (ISO/IEC 18092) into the Enterprise Bus Validator APDU engine specifically for **KCI KMT (Kartu Multi Trip)**, Indonesia's widely used transit contactless smart card.

## User Review Required
> [!IMPORTANT]
> - **Card Technology**: FeliCa cards operate using System Code `0xFE00` (KMT) with Service Code `0x000B` using FeliCa Polling, Read Without Encryption, and Write Without Encryption commands.
> - **TransCode Settlement**: Deduct and Auto Completion on KMT FeliCa cards generate acquirer settlement TransCodes formatted as `TC-KMT-{COUNTER}-{HASH}`.

## Proposed Changes

### Model Layer (`core:model`)

#### [MODIFY] [DomainModels.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/model/src/main/java/com/enterprise/busvalidator/core/model/DomainModels.kt)
- Add `KMT_FELICA("KMT", "KCI Kartu Multi Trip (FeliCa)", "FE00")` to `BankIssuer` enum.

---

### Payment Layer (`core:payment`)

#### [NEW] [KmtFelicaApdu.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/payment/src/main/java/com/enterprise/busvalidator/core/payment/apdu/banks/KmtFelicaApdu.kt)
- Create `KmtFelicaApdu` class implementing `BankApduHandler`:
  - `selectApplication`: Probes FeliCa Polling frame (`SystemCode 0xFE00`).
  - `readCardInfo`: Executes FeliCa Read Without Encryption for Service `0x000B` to extract balance, card serial number, and journey state.
  - `processAutoCompletion`: Handles open tap-in auto-completion and generates TransCode `TC-KMT-...`.
  - `deduct`: Executes FeliCa Write Without Encryption command to deduct fare atomically and return `ApduDeductResult` with TransCode `TC-KMT-...`.

#### [MODIFY] [BankApduManager.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/payment/src/main/java/com/enterprise/busvalidator/core/payment/apdu/BankApduManager.kt)
- Inject `KmtFelicaApdu` and add it to the probed `handlers` list.

---

### Unit Test Layer (`core:payment`)

#### [MODIFY] [BankApduAndQrisTest.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/payment/src/test/java/com/enterprise/busvalidator/core/payment/BankApduAndQrisTest.kt)
- Inject `kmtApdu` into test setup.
- Add test case `testKmtFelicaCardDetectionReadAndDeduct` verifying FeliCa polling, card info extraction, deduct, and TransCode generation.

---

## Verification Plan

### Automated Tests
- Execute `./gradlew test` via `run_command` in `/Volumes/Gorby/AndroidStudioProjects/DeviceApp` to verify compilation and 100% test pass rate across `:core:payment:testDebugUnitTest`.

### Manual Verification
- Verify that `BankIssuer.KMT_FELICA` is properly exposed in transaction records and Room DB persistence.
