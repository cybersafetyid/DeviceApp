package com.enterprise.busvalidator.core.model

/**
 * Supported Transit Operators.
 */
enum class OperatorBrand {
    BISKITA,
    CITRA,
    SURABAYA
}

/**
 * Sub-service / City variants and supported hardware matrix.
 */
enum class OperatorSubService(
    val brand: OperatorBrand,
    val displayName: String,
    val supportedModels: List<VendorDeviceModel>
) {
    BISKITA_BEKASI(OperatorBrand.BISKITA, "Biskita Bekasi", listOf(VendorDeviceModel.E60Q, VendorDeviceModel.E60V2)),
    BISKITA_DEPOK(OperatorBrand.BISKITA, "Biskita Depok", listOf(VendorDeviceModel.E60V2)),
    BISKITA_BOGOR(OperatorBrand.BISKITA, "Biskita Bogor", listOf(VendorDeviceModel.E60Q, VendorDeviceModel.E60V2)),

    CITRA_RAYA(OperatorBrand.CITRA, "Citra Raya", listOf(VendorDeviceModel.E60Q, VendorDeviceModel.E60V2)),
    CITRA_MAJA(OperatorBrand.CITRA, "Citra Maja", listOf(VendorDeviceModel.E60Q, VendorDeviceModel.E60V2)),

    SURABAYA_WARA_WIRI(OperatorBrand.SURABAYA, "Wara Wiri Surabaya", listOf(VendorDeviceModel.E60Q)),
    SURABAYA_BUS(OperatorBrand.SURABAYA, "Bus Surabaya", listOf(VendorDeviceModel.Q6))
}

/**
 * Operator specific fare rules and profile pricing.
 */
data class FareRulePolicy(
    val baseFare: Long,
    val studentFare: Long,
    val seniorCitizenFare: Long,
    val disabledFare: Long,
    val pnsFare: Long,
    val freeTransferWindowMinutes: Int = 0
)

/**
 * Complete Operator Configuration Profile.
 */
data class OperatorConfig(
    val brand: OperatorBrand,
    val subService: OperatorSubService,
    val operatorName: String,
    val baseUrl: String,
    val defaultRouteCode: String,
    val defaultRouteName: String,
    val supportedHardwareModels: List<VendorDeviceModel>,
    val fareRulePolicy: FareRulePolicy
)

/**
 * Predefined Operator Configuration Presets.
 */
object OperatorPresets {
    val BISKITA_BEKASI = OperatorConfig(
        brand = OperatorBrand.BISKITA,
        subService = OperatorSubService.BISKITA_BEKASI,
        operatorName = "BISKITA BEKASI",
        baseUrl = "https://api.biskita-bekasi.transindo.id/v1",
        defaultRouteCode = "BK-01",
        defaultRouteName = "Terminal Bekasi - Harapan Indah",
        supportedHardwareModels = listOf(VendorDeviceModel.E60Q, VendorDeviceModel.E60V2),
        fareRulePolicy = FareRulePolicy(
            baseFare = 4000L,
            studentFare = 2000L,
            seniorCitizenFare = 0L,
            disabledFare = 0L,
            pnsFare = 3000L
        )
    )

    val BISKITA_DEPOK = OperatorConfig(
        brand = OperatorBrand.BISKITA,
        subService = OperatorSubService.BISKITA_DEPOK,
        operatorName = "BISKITA DEPOK",
        baseUrl = "https://api.biskita-depok.transindo.id/v1",
        defaultRouteCode = "DP-01",
        defaultRouteName = "Terminal Margonda - Stasiun LRT Harjamukti",
        supportedHardwareModels = listOf(VendorDeviceModel.E60V2),
        fareRulePolicy = FareRulePolicy(
            baseFare = 3500L,
            studentFare = 2000L,
            seniorCitizenFare = 0L,
            disabledFare = 0L,
            pnsFare = 3000L
        )
    )

    val BISKITA_BOGOR = OperatorConfig(
        brand = OperatorBrand.BISKITA,
        subService = OperatorSubService.BISKITA_BOGOR,
        operatorName = "BISKITA BOGOR",
        baseUrl = "https://api.biskita-bogor.transindo.id/v1",
        defaultRouteCode = "BG-02",
        defaultRouteName = "Terminal Bubulak - Ciawi",
        supportedHardwareModels = listOf(VendorDeviceModel.E60Q, VendorDeviceModel.E60V2),
        fareRulePolicy = FareRulePolicy(
            baseFare = 4000L,
            studentFare = 2000L,
            seniorCitizenFare = 0L,
            disabledFare = 0L,
            pnsFare = 3000L
        )
    )

    val CITRA_RAYA = OperatorConfig(
        brand = OperatorBrand.CITRA,
        subService = OperatorSubService.CITRA_RAYA,
        operatorName = "CITRA RAYA SHUTTLE",
        baseUrl = "https://api.citraraya-shuttle.co.id/v1",
        defaultRouteCode = "CR-01",
        defaultRouteName = "Citra Raya Shuttle - EcoPlaza Loop",
        supportedHardwareModels = listOf(VendorDeviceModel.E60Q, VendorDeviceModel.E60V2),
        fareRulePolicy = FareRulePolicy(
            baseFare = 5000L,
            studentFare = 3000L,
            seniorCitizenFare = 2500L,
            disabledFare = 2500L,
            pnsFare = 5000L
        )
    )

    val CITRA_MAJA = OperatorConfig(
        brand = OperatorBrand.CITRA,
        subService = OperatorSubService.CITRA_MAJA,
        operatorName = "CITRA MAJA SHUTTLE",
        baseUrl = "https://api.citramaja-shuttle.co.id/v1",
        defaultRouteCode = "CM-01",
        defaultRouteName = "Stasiun Maja - Citra Maja City Loop",
        supportedHardwareModels = listOf(VendorDeviceModel.E60Q, VendorDeviceModel.E60V2),
        fareRulePolicy = FareRulePolicy(
            baseFare = 5000L,
            studentFare = 3000L,
            seniorCitizenFare = 2500L,
            disabledFare = 2500L,
            pnsFare = 5000L
        )
    )

    val SURABAYA_WARA_WIRI = OperatorConfig(
        brand = OperatorBrand.SURABAYA,
        subService = OperatorSubService.SURABAYA_WARA_WIRI,
        operatorName = "WARA WIRI SURABAYA",
        baseUrl = "https://api-warawiri.surabaya.go.id/v1",
        defaultRouteCode = "FD-01",
        defaultRouteName = "Wara Wiri Feeder Subang - Joyoboyo",
        supportedHardwareModels = listOf(VendorDeviceModel.E60Q),
        fareRulePolicy = FareRulePolicy(
            baseFare = 5000L,
            studentFare = 2500L,
            seniorCitizenFare = 0L,
            disabledFare = 0L,
            pnsFare = 5000L,
            freeTransferWindowMinutes = 30
        )
    )

    val SURABAYA_BUS = OperatorConfig(
        brand = OperatorBrand.SURABAYA,
        subService = OperatorSubService.SURABAYA_BUS,
        operatorName = "BUS SURABAYA (SUROBOYO BUS)",
        baseUrl = "https://api-suroboyobus.surabaya.go.id/v1",
        defaultRouteCode = "SB-01",
        defaultRouteName = "Suroboyo Bus Purabaya - Rajawali",
        supportedHardwareModels = listOf(VendorDeviceModel.Q6),
        fareRulePolicy = FareRulePolicy(
            baseFare = 5000L,
            studentFare = 2500L,
            seniorCitizenFare = 0L,
            disabledFare = 0L,
            pnsFare = 5000L,
            freeTransferWindowMinutes = 30
        )
    )

    fun getPreset(subService: OperatorSubService): OperatorConfig {
        return when (subService) {
            OperatorSubService.BISKITA_BEKASI -> BISKITA_BEKASI
            OperatorSubService.BISKITA_DEPOK -> BISKITA_DEPOK
            OperatorSubService.BISKITA_BOGOR -> BISKITA_BOGOR
            OperatorSubService.CITRA_RAYA -> CITRA_RAYA
            OperatorSubService.CITRA_MAJA -> CITRA_MAJA
            OperatorSubService.SURABAYA_WARA_WIRI -> SURABAYA_WARA_WIRI
            OperatorSubService.SURABAYA_BUS -> SURABAYA_BUS
        }
    }
}

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
    val operatorConfig: OperatorConfig = OperatorPresets.BISKITA_BEKASI,
    val routeCode: String = operatorConfig.defaultRouteCode,
    val routeName: String = operatorConfig.defaultRouteName,
    val busCode: String = "BUS-1049",
    val baseFare: Long = operatorConfig.fareRulePolicy.baseFare
) {
    val operatorName: String get() = operatorConfig.operatorName
}

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
