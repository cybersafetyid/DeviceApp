package com.enterprise.busvalidator.core.model

/**
 * Terminal configuration parameters fetched during splash initialization.
 */
data class TerminalConfig(
    val merchantId: String,
    val terminalId: String,
    val pinCode: String,
    val processingCode: String,
    val samId: String,
    val marriageCode: String,
    val tapMode: TapMode = TapMode.TAP_IN_OUT,
    val operatorName: String = "TransJakarta",
    val routeCode: String = "KOR1",
    val routeName: String = "Blok M - Kota",
    val busCode: String = "BUS-1049",
    val baseFare: Long = 3500L
)

enum class TapMode {
    TAP_IN_ONLY,
    TAP_OUT_ONLY,
    TAP_IN_OUT
}

enum class VendorDeviceModel {
    AUTO,
    E60Q,
    E60V2,
    Q6,
    Z90,
    A90,
    Z91,
    TELPO,
    MSI,
    GENERIC
}

enum class TimeConfidenceState {
    SECURE_SYNCED,
    MONOTONIC_VALIDATED,
    TIME_UNTRUSTED
}

enum class PassengerProfile {
    GENERAL,
    SENIOR_CITIZEN, // Lansia
    STUDENT,        // Pelajar
    GOVERNMENT_PNS, // PNS
    DISABLED
}

data class TransactionRecord(
    val transactionId: String,
    val cardUid: String,
    val bankIssuer: String,
    val amountDeducted: Long,
    val initialBalance: Long,
    val finalBalance: Long,
    val timestampUtc: Long,
    val tapMode: TapMode,
    val passengerProfile: PassengerProfile,
    val status: TransactionStatus,
    val recordSignature: String
)

enum class TransactionStatus {
    SUCCESS,
    CARD_ALREADY_TAPPED,
    INSUFFICIENT_BALANCE,
    FAILED_WRITE_ROLLBACK,
    UNTRUSTED_TIME_REJECTED,
    CARD_READ_ERROR
}

data class TelemetryStatus(
    val isOnline: Boolean = true,
    val signalDbm: Int = -75,
    val networkType: String = "4G",
    val isGpsFixed: Boolean = true,
    val gpsSatellites: Int = 11,
    val latitude: Double = -6.175392,
    val longitude: Double = 106.827153,
    val pendingSyncCount: Int = 0,
    val dailyTransactionCount: Int = 1420,
    val lastTransactionSummary: String = "Last: Rp 3.500 [Flazz *4920]",
    val isSerialOk: Boolean = true,
    val isSamOk: Boolean = true,
    val isNfcOk: Boolean = true,
    val isScannerOk: Boolean = true,
    val timeConfidence: TimeConfidenceState = TimeConfidenceState.SECURE_SYNCED
)
