# Implementation Plan - APDU for All Banks & QRIS Tap Integration

This plan details the implementation of bank-specific APDU handlers (`readcardinfo`, `deduct`, `auto completion`, and `grace period` for Mandiri e-Money) for all 6 supported banks (Bank Mandiri e-Money, BCA Flazz, BRI Brizzi, BNI TapCash, Bank DKI JakCard, Bank Nobu E-Money), as well as integrating QRIS Tap & Dynamic QRIS processing into the core `:core:payment` engine.

> [!IMPORTANT]
> **Mandatory Settlement Requirement: TransCode (Transaction Code / Certificate)**
> Both `deduct` and `auto completion` operations generate and verify a **`transCode`** (Transaction Certificate Code & Transaction Counter). This `transCode` is written to the card APDU record and cryptographically verified by the SAM module. During bank acquirer settlement, `transCode` is the primary key used to validate transaction authenticity; without a valid `transCode`, the transaction is flagged as **INVALID / SETTLEMENT_REJECTED**.

---

## Output Data Specifications for Each Step

### 1. Output Step 1: `readcardinfo` -> `BankCardInfo`
Setiap bank me-return objek **`BankCardInfo`**:
- **`cardUid`**: UID fisik chip RFID (contoh: `"04E21A88BC6180"`)
- **`bankIssuer`**: Enum `BankIssuer` (`MANDIRI_EMONEY`, `BCA_FLAZZ`, `BRI_BRIZZI`, `BNI_TAPCASH`, `BANK_DKI_JAKCARD`, `NOBU_EMONEY`)
- **`cardNumberFormatted`**: Nomor Kartu/CAN (Card Access Number 16 digit terformat, contoh: `"6032-7810-1234-5678"`)
- **`balance`**: Saldo terkini kartu dalam Rupiah (contoh: `50000L`)
- **`uncompletedTxState`**: Status perjalanan terpendam (`CLOSED`, `OPEN_TAP_IN`, `PENALTY_REQUIRED`)
- **`lastTransactionTimestamp`**: Timestamp Unix ms transaksi terakhir pada kartu
- **`lastTransCode`**: TransCode/Transaction Counter terakhir yang tercatat di kartu
- **`mandiriGracePeriodInfo`**: Data khusus Mandiri e-Money (bila bank Mandiri)

---

### 2. Output Step 2: `auto completion` -> `AutoCompletionResult`
Mengatasi status kartu gantung (misal tap-in di bus/stasiun sebelumnya belum tertutup):
- **`wasApplied`**: `Boolean` (`true` jika kartu terdeteksi `OPEN_TAP_IN` dan APDU auto completion berhasil dieksekusi)
- **`openJourneyId`**: ID Trip atau Halte/Bus awal tap-in sebelumnya
- **`penaltyOrFlatFare`**: Jumlah Rupiah yang dipotong untuk penyelesaian perjalanan gantung (contoh: `3500L` / `5000L`)
- **`balanceAfterCompletion`**: Saldo kartu setelah auto completion berhasil dilakukan
- **`autoCompletionTransCode`**: **TransCode resmi yang di-generate dari APDU SAM/Card untuk settlement transaksi penyelesaian gantung** (contoh: `"TRX-AUTO-98420114"`)
- **`transactionCounter`**: Counter transaksi kartu yang bertambah setelah auto completion
- **`updatedTxState`**: State baru kartu (berubah menjadi `CLOSED`)
- **`status`**: `AutoCompletionStatus` (`SUCCESS`, `NOT_NEEDED`, `FAILED`)

---

### 3. Output Step 3: `grace period` (Khusus Mandiri e-Money) -> `MandiriGracePeriodResult`
Mengevaluasi aturan grace period Mandiri e-Money:
- **`isGracePeriodActive`**: `Boolean` (`true` jika tap-out terjadi dalam rentang grace period, misal < 15 menit dari tap-in atau di bus/rute yang sama)
- **`originalFare`**: Tarip asli yang seharusnya dipotong (contoh: `4000L`)
- **`adjustedGraceFare`**: Tarip setelah penyesuaian grace period (contoh: `0L` untuk free tap-out atau `2000L`)
- **`graceDiscountAmount`**: Besarnya diskon grace period (contoh: `4000L`)
- **`explanation`**: Keterangan log (contoh: `"Mandiri e-Money Grace Period Applied: Same Station Tap-Out within 15 mins -> Fare Rp 0"`)

---

### 4. Output Step 4: `deduct` -> `ApduDeductResult`
Eksekusi pemotongan saldo APDU atomic ISO-7816 / ISO-14443-4:
- **`isSuccess`**: `Boolean` (`true` jika SW APDU me-return `90 00` / `91 00`)
- **`transCode`**: **TransCode/Transaction Certificate sah hasil APDU deduct dari SAM & Card yang wajib disetor saat settlement** (contoh: `"TC-MANDIRI-20260801-0004921"`)
- **`transactionCounter`**: Card Transaction Counter (contoh: `142`)
- **`amountDeducted`**: Saldo yang berhasil dipotong (contoh: `3500L`)
- **`initialBalance`**: Saldo kartu sebelum dipotong (contoh: `50000L`)
- **`finalBalance`**: Saldo akhir kartu di dalam chip (contoh: `46500L`)
- **`statusWordHex`**: SW response dari kartu (contoh: `"9000"`)
- **`samAuthSignature`**: Cryptographic MAC signature dari SAM module untuk settlement bank

---

### 5. Output Step 5: `QRIS tap` -> `QrisTapData` & `TransactionRecord`
Pemrosesan tap / scan QRIS dinamis (EMVCo & ASPI):
- **`qrisPayload`**: Full string payload QRIS (contoh: `"00020101021226670016ID.GO.QRIS.WWW..."`)
- **`merchantName`**: Nama Merchant Transindo / Operator (contoh: `"BISKITA BEKASI BUS 1049"`)
- **`merchantId`**: MID & TID terminal
- **`transactionId`**: ID unik transaksi QRIS
- **`transCode`**: QRIS Retrieval Reference Number (RRN) / Approval Code sebagai settlement identifier
- **`amount`**: Nominal pembayaran QRIS dalam Rupiah
- **`crcVerified`**: `Boolean` (validasi CRC16-CCITT)
- **`transactionRecord`**: Objek `TransactionRecord` dengan `bankIssuer = "QRIS_TAP"`

---

### 6. Output Final Payment Engine: `TransactionRecord`
Objek final yang di-commit ke Room Encrypted DB & di-emit ke UI:
```kotlin
data class TransactionRecord(
    val transactionId: String,          // e.g. "TX-1772545000000-5678"
    val transCode: String,              // MANDATORY: Bank Settlement TransCode / Certificate Code
    val transactionCounter: Int,        // e.g. 142
    val cardUid: String,                // e.g. "04E21A88BC6180"
    val bankIssuer: String,             // e.g. "MANDIRI_EMONEY", "BCA_FLAZZ", "QRIS_TAP"
    val amountDeducted: Long,           // e.g. 3500L
    val initialBalance: Long,          // e.g. 50000L
    val finalBalance: Long,            // e.g. 46500L
    val timestampUtc: Long,            // UTC Epoch ms
    val tapMode: TapMode,              // TAP_IN_OUT
    val passengerProfile: PassengerProfile, // GENERAL / STUDENT / SENIOR_CITIZEN
    val status: TransactionStatus,      // SUCCESS
    val recordSignature: String         // HMAC SHA-256 Signature untuk audit & anti-tamper
)
```

---

## Proposed Changes

### Core Models & Database (`:core:model` & `:core:database`)

#### [MODIFY] [DomainModels.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/model/src/main/java/com/enterprise/busvalidator/core/model/DomainModels.kt)
- Update `TransactionRecord` to include `transCode: String` and `transactionCounter: Int`.
- Add `BankIssuer` enum (`MANDIRI_EMONEY`, `BCA_FLAZZ`, `BRI_BRIZZI`, `BNI_TAPCASH`, `BANK_DKI_JAKCARD`, `NOBU_EMONEY`, `QRIS_TAP`).
- Add `UncompletedTxState` enum (`CLOSED`, `OPEN_TAP_IN`, `PENALTY_REQUIRED`).
- Add `BankCardInfo`, `ApduDeductResult`, `AutoCompletionResult`, `MandiriGracePeriodResult`, `QrisTapData` with `transCode` fields.

#### [MODIFY] [ValidatorDatabase.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/database/src/main/java/com/enterprise/busvalidator/core/database/ValidatorDatabase.kt)
- Add `transCode` and `transactionCounter` columns to `TransactionEntity` for bank settlement exports.

#### [NEW] [ApduModels.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/payment/src/main/java/com/enterprise/busvalidator/core/payment/apdu/ApduModels.kt)
- Define APDU command bytes, response status words (`SW1`, `SW2`), and `TransCode` generators.

#### [NEW] [BankApduHandler.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/payment/src/main/java/com/enterprise/busvalidator/core/payment/apdu/BankApduHandler.kt)
- APDU contract interface returning `transCode` on `deduct` and `processAutoCompletion`.

#### [NEW] Bank APDU Driver Implementations:
- `MandiriEmoneyApdu.kt`: Mandiri e-Money APDU with `transCode` generation & Grace Period logic.
- `BcaFlazzApdu.kt`: BCA Flazz APDU with `transCode` generation.
- `BriBrizziApdu.kt`: BRI Brizzi APDU with `transCode` generation.
- `BniTapCashApdu.kt`: BNI TapCash APDU with `transCode` generation.
- `BankDkiJakCardApdu.kt`: Bank DKI JakCard APDU with `transCode` generation.
- `BankNobuApdu.kt`: Bank Nobu E-Money APDU with `transCode` generation.

#### [NEW] [BankApduManager.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/payment/src/main/java/com/enterprise/busvalidator/core/payment/apdu/BankApduManager.kt)
- Dispatches APDU operations and validates returned `transCode`.

#### [NEW] [QrisPaymentEngine.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/payment/src/main/java/com/enterprise/busvalidator/core/payment/qris/QrisPaymentEngine.kt)
- QRIS Tap & Dynamic QRIS processor generating RRN TransCode.

#### [MODIFY] [PaymentEngine.kt](file:///Volumes/Gorby/AndroidStudioProjects/DeviceApp/core/payment/src/main/java/com/enterprise/busvalidator/core/payment/PaymentEngine.kt)
- Integrates APDU and QRIS flows with `transCode` settlement persistence.

---

## Verification Plan

### Automated Unit Tests
- `BankApduManagerTest`: Validate TransCode generation and SAM MAC matching (`./gradlew test`).
- `MandiriGracePeriodTest`: Verify TransCode for grace period transaction adjustments.
- `AutoCompletionApduTest`: Verify TransCode generation for auto completion.
- `QrisPaymentEngineTest`: Test TransCode / RRN generation for QRIS Tap.
- `PaymentEngineApduIntegrationTest`: Test end-to-end payment flow with TransCode database persistence.
