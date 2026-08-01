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

enum class ApiEnvironment {
    PRODUCTION,
    DEVELOPMENT
}

enum class MqttTransport(val scheme: String) {
    TCP("tcp"),
    SSL("ssl")
}

data class MqttBrokerConfig(
    val host: String,
    val port: Int,
    val transport: MqttTransport = MqttTransport.TCP,
    val username: String = "bv",
    val password: String = "1sampai8",
    val cleanSession: Boolean = true
) {
    val brokerUrl: String get() = "${transport.scheme}://$host:$port"
}

data class MqttTopicConfig(
    val regionName: String,
    val busRoot: String = "bv",
    val notificationRoot: String = "notif",
    val commandTopic: String = "bus/command",
    val statusTopic: String = "bus/status",
    val whitelistNotificationSuffix: String = "notif"
) {
    fun busTopic(busCode: String): String = "$busRoot/$regionName/${busCode.sanitizeMqttTopicPart()}"
    fun notificationTopic(hardwareId: String): String = "$notificationRoot/${hardwareId.sanitizeMqttTopicPart()}"
    fun whitelistTopic(): String = "whitelist/$regionName/$whitelistNotificationSuffix"
    fun clientId(busCode: String): String = "${regionName}_${busCode.sanitizeMqttClientIdPart()}"
}

data class OperatorApiBaseUrls(
    val production: String,
    val development: String
) {
    fun forEnvironment(environment: ApiEnvironment): String {
        return when (environment) {
            ApiEnvironment.PRODUCTION -> production
            ApiEnvironment.DEVELOPMENT -> development
        }
    }
}

data class OperatorMqttBrokerConfigs(
    val production: MqttBrokerConfig,
    val development: MqttBrokerConfig
) {
    fun forEnvironment(environment: ApiEnvironment): MqttBrokerConfig {
        return when (environment) {
            ApiEnvironment.PRODUCTION -> production
            ApiEnvironment.DEVELOPMENT -> development
        }
    }
}

/**
 * Complete Operator Configuration Profile.
 */
data class OperatorConfig(
    val brand: OperatorBrand,
    val subService: OperatorSubService,
    val operatorName: String,
    val legacyRegionName: String,
    val apiBaseUrls: OperatorApiBaseUrls,
    val mqttBrokerConfigs: OperatorMqttBrokerConfigs,
    val apiEnvironment: ApiEnvironment = ApiEnvironment.PRODUCTION,
    val defaultRouteCode: String,
    val defaultRouteName: String,
    val supportedHardwareModels: List<VendorDeviceModel>,
    val fareRulePolicy: FareRulePolicy
) {
    val baseUrl: String get() = apiBaseUrls.forEnvironment(apiEnvironment)
    val mqttBrokerConfig: MqttBrokerConfig get() = mqttBrokerConfigs.forEnvironment(apiEnvironment)

    fun withApiEnvironment(environment: ApiEnvironment): OperatorConfig {
        return copy(apiEnvironment = environment)
    }
}

/**
 * Predefined Operator Configuration Presets.
 */
object OperatorPresets {
    private const val DEV_BISKITA_BASE_URL = "https://dev-buskita.karcisku.id/c_bus/"
    private const val DEV_CITRA_RAYA_BASE_URL = "https://afc-citraraya.net2software.net/c_bus/"
    private const val DEV_SURABAYA_BASE_URL = "https://dev-suroboyo.net2software.net/c_bus/"

    private const val PROD_BISKITA_BEKASI_BASE_URL = "https://transpatriot.karcisku.id/c_bus/"
    private const val PROD_BISKITA_DEPOK_BASE_URL = "https://transdepok.karcisku.id/c_bus/"
    private const val PROD_BISKITA_BOGOR_BASE_URL = "https://kabbogor.karcisku.id/c_bus/"
    private const val PROD_CITRA_RAYA_BASE_URL = "https://buscitrarayatgr.karcisku.id/c_bus/"
    private const val PROD_CITRA_MAJA_BASE_URL = "https://buscitra.karcisku.id/c_bus/"
    private const val PROD_SURABAYA_BASE_URL = "https://suroboyo-bus.jaring.host/c_bus/"
    private val LEGACY_MQTT_BROKERS = OperatorMqttBrokerConfigs(
        production = MqttBrokerConfig(host = "mqtt.jsa2.host", port = 12345),
        development = MqttBrokerConfig(host = "192.168.66.201", port = 1883)
    )

    val BISKITA_BEKASI = OperatorConfig(
        brand = OperatorBrand.BISKITA,
        subService = OperatorSubService.BISKITA_BEKASI,
        operatorName = "BISKITA BEKASI",
        legacyRegionName = "bekasi",
        apiBaseUrls = OperatorApiBaseUrls(
            production = PROD_BISKITA_BEKASI_BASE_URL,
            development = DEV_BISKITA_BASE_URL
        ),
        mqttBrokerConfigs = LEGACY_MQTT_BROKERS,
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
        legacyRegionName = "depok",
        apiBaseUrls = OperatorApiBaseUrls(
            production = PROD_BISKITA_DEPOK_BASE_URL,
            development = DEV_BISKITA_BASE_URL
        ),
        mqttBrokerConfigs = LEGACY_MQTT_BROKERS,
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
        legacyRegionName = "bogor",
        apiBaseUrls = OperatorApiBaseUrls(
            production = PROD_BISKITA_BOGOR_BASE_URL,
            development = DEV_BISKITA_BASE_URL
        ),
        mqttBrokerConfigs = LEGACY_MQTT_BROKERS,
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
        legacyRegionName = "citraraya",
        apiBaseUrls = OperatorApiBaseUrls(
            production = PROD_CITRA_RAYA_BASE_URL,
            development = DEV_CITRA_RAYA_BASE_URL
        ),
        mqttBrokerConfigs = LEGACY_MQTT_BROKERS,
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
        legacyRegionName = "citramaja",
        apiBaseUrls = OperatorApiBaseUrls(
            production = PROD_CITRA_MAJA_BASE_URL,
            development = DEV_BISKITA_BASE_URL
        ),
        mqttBrokerConfigs = LEGACY_MQTT_BROKERS,
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
        legacyRegionName = "sby",
        apiBaseUrls = OperatorApiBaseUrls(
            production = PROD_SURABAYA_BASE_URL,
            development = DEV_SURABAYA_BASE_URL
        ),
        mqttBrokerConfigs = LEGACY_MQTT_BROKERS,
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
        legacyRegionName = "sby",
        apiBaseUrls = OperatorApiBaseUrls(
            production = PROD_SURABAYA_BASE_URL,
            development = DEV_SURABAYA_BASE_URL
        ),
        mqttBrokerConfigs = LEGACY_MQTT_BROKERS,
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

    fun getPreset(
        subService: OperatorSubService,
        apiEnvironment: ApiEnvironment = ApiEnvironment.PRODUCTION
    ): OperatorConfig {
        return when (subService) {
            OperatorSubService.BISKITA_BEKASI -> BISKITA_BEKASI
            OperatorSubService.BISKITA_DEPOK -> BISKITA_DEPOK
            OperatorSubService.BISKITA_BOGOR -> BISKITA_BOGOR
            OperatorSubService.CITRA_RAYA -> CITRA_RAYA
            OperatorSubService.CITRA_MAJA -> CITRA_MAJA
            OperatorSubService.SURABAYA_WARA_WIRI -> SURABAYA_WARA_WIRI
            OperatorSubService.SURABAYA_BUS -> SURABAYA_BUS
        }.withApiEnvironment(apiEnvironment)
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
    val hardwareId: String = DeviceIdentity.DEFAULT_DEVICE_ID,
    val tapMode: TapMode = TapMode.TAP_IN_OUT,
    val operatorConfig: OperatorConfig = OperatorPresets.BISKITA_BEKASI,
    val routeCode: String = operatorConfig.defaultRouteCode,
    val routeName: String = operatorConfig.defaultRouteName,
    val busCode: String = DeviceIdentity.UNCONFIGURED_BUS_CODE,
    val baseFare: Long = operatorConfig.fareRulePolicy.baseFare,
    val trip: String = "1",
    val operationalCode: String = "",
    val operationalStart: String = "",
    val operationalEnd: String = "",
    val currentHalte: String = "",
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val qrisEnabled: Boolean = false,
    val rawQris: String = "",
    val qrisMerchantId: String = "",
    val qrisTerminalId: String = "",
    val terminalCode: String = "",
    val vehiclePlate: String = "",
    val transportationType: String = "",
    val configVersion: String = "",
    val fareFileName: String = "",
    val fareFileUrl: String = "",
    val serverTime: String = "",
    val validationRadiusMeters: Double? = null,
    val direction: String = "",
    val doubleTapAllowed: Boolean? = null,
    val locationLimitSeconds: Int? = null,
    val cityCode: String = "",
    val cityName: String = "",
    val corridorCode: String = "",
    val corridorName: String = "",
    val stopCode: String = "",
    val stopName: String = "",
    val cycleTimeSeconds: Int? = null,
    val cardIssuers: Map<String, CardIssuerConfig> = emptyMap()
) {
    val operatorName: String get() = operatorConfig.operatorName
    val mqttTopicConfig: MqttTopicConfig
        get() = MqttTopicConfig(regionName = operatorConfig.legacyRegionName)

    fun issuerConfigFor(bankIssuer: String): CardIssuerConfig? {
        return cardIssuers[bankIssuer.normalizedIssuerKey()]
    }
}

data class CardIssuerConfig(
    val issuerName: String,
    val merchantId: String = "",
    val terminalId: String = "",
    val slot: String = "",
    val isEnabled: Boolean = false,
    val pinCode: String = "",
    val processingCode: String = "",
    val samId: String = "",
    val marriageCodes: List<String> = emptyList(),
    val checkTimeSeconds: Int? = null,
    val validTimeSeconds: Int? = null
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

object DeviceIdentity {
    const val UNCONFIGURED_BUS_CODE = "UNCONFIGURED"
    const val DEFAULT_DEVICE_ID = "UNCONFIGURED-DEVICE"
}

private fun String.sanitizeMqttTopicPart(): String {
    return replace(Regex("[\\u0000-\\u001F#+]"), "_")
}

private fun String.sanitizeMqttClientIdPart(): String {
    return replace(Regex("[^a-zA-Z0-9_]"), "")
        .ifBlank { DeviceIdentity.DEFAULT_DEVICE_ID.replace(Regex("[^a-zA-Z0-9_]"), "") }
}

fun String.normalizedIssuerKey(): String = trim().uppercase().replace(Regex("[^A-Z0-9]"), "")

data class BusLocationSnapshot(
    val deviceId: String = DeviceIdentity.DEFAULT_DEVICE_ID,
    val recordedAtUtc: Long,
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val verticalAccuracyMeters: Float?,
    val bearingDegrees: Float?,
    val bearingAccuracyDegrees: Float?,
    val speedMetersPerSecond: Float?,
    val speedAccuracyMetersPerSecond: Float?,
    val elapsedRealtimeNanos: Long,
    val satelliteCount: Int?,
    val isMock: Boolean
)

data class LocationTelemetryPayload(
    val locationLogId: Long?,
    val snapshot: BusLocationSnapshot,
    val pendingLocationLogCount: Int,
    val deliveryAttempt: Int
)

enum class PassengerProfile {
    GENERAL,
    SENIOR_CITIZEN, // Lansia
    STUDENT,        // Pelajar
    GOVERNMENT_PNS, // PNS
    DISABLED
}

enum class BankIssuer(val code: String, val displayName: String, val defaultAid: String) {
    MANDIRI_EMONEY("MANDIRI", "Mandiri e-Money", "F000000001"),
    BCA_FLAZZ("BCA", "BCA Flazz", "A0000000041010"),
    BRI_BRIZZI("BRI", "BRI Brizzi", "D360000001"),
    BNI_TAPCASH("BNI", "BNI TapCash", "D360000002"),
    BANK_DKI_JAKCARD("DKI", "Bank DKI JakCard", "D360000003"),
    NOBU_EMONEY("NOBU", "Bank Nobu E-Money", "D360000004"),
    KMT_FELICA("KMT", "KCI Kartu Multi Trip (FeliCa)", "FE00"),
    QRIS_TAP("QRIS", "QRIS Tap / Dynamic QR", "000201"),
    UNKNOWN("UNKNOWN", "Unknown Card Issuer", "")
}

enum class UncompletedTxState {
    CLOSED,
    OPEN_TAP_IN,
    PENALTY_REQUIRED
}

data class MandiriGracePeriodInfo(
    val isGracePeriodActive: Boolean = false,
    val tapInTimestamp: Long = 0L,
    val graceWindowMinutes: Int = 15,
    val isEligibleForGraceDiscount: Boolean = false,
    val graceFare: Long = 0L
)

data class BankCardInfo(
    val cardUid: String,
    val bankIssuer: BankIssuer,
    val cardNumberFormatted: String,
    val balance: Long,
    val uncompletedTxState: UncompletedTxState = UncompletedTxState.CLOSED,
    val lastTransactionTimestamp: Long = 0L,
    val lastTransCode: String = "",
    val mandiriGracePeriodInfo: MandiriGracePeriodInfo? = null,
    val rawApplicationData: ByteArray = byteArrayOf()
)

data class ApduDeductResult(
    val isSuccess: Boolean,
    val transCode: String,
    val transactionCounter: Int,
    val amountDeducted: Long,
    val initialBalance: Long,
    val finalBalance: Long,
    val statusWordHex: String = "9000",
    val samAuthSignature: String = "",
    val errorMessage: String? = null
)

enum class AutoCompletionStatus {
    SUCCESS,
    NOT_NEEDED,
    FAILED
}

data class AutoCompletionResult(
    val wasApplied: Boolean,
    val openJourneyId: String = "",
    val penaltyOrFlatFare: Long = 0L,
    val balanceAfterCompletion: Long = 0L,
    val autoCompletionTransCode: String = "",
    val transactionCounter: Int = 0,
    val updatedTxState: UncompletedTxState = UncompletedTxState.CLOSED,
    val status: AutoCompletionStatus = AutoCompletionStatus.NOT_NEEDED
)

data class MandiriGracePeriodResult(
    val isGracePeriodActive: Boolean,
    val originalFare: Long,
    val adjustedGraceFare: Long,
    val graceDiscountAmount: Long,
    val explanation: String
)

data class QrisTapData(
    val qrisPayload: String,
    val merchantName: String,
    val merchantId: String,
    val terminalId: String,
    val transactionId: String,
    val transCode: String,
    val amount: Long,
    val crcVerified: Boolean
)

data class TransactionRecord(
    val transactionId: String,
    val transCode: String,
    val transactionCounter: Int,
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
    CARD_READ_ERROR,
    AUTO_COMPLETION_FAILED,
    COUNTER_SYNC_CONFLICT
}

data class TransactionSyncItem(
    val transactionId: String,
    val transactionCounter: Int,
    val transCode: String,
    val cardUid: String,
    val bankIssuer: String,
    val amountDeducted: Long,
    val initialBalance: Long,
    val finalBalance: Long,
    val timestampUtc: Long,
    val tapMode: String,
    val passengerProfile: String,
    val status: String,
    val recordSignature: String
)

data class TransactionSyncResult(
    val acceptedTransactionIds: Set<String>,
    val backendLastCounter: Int,
    val conflictReason: String? = null,
    val retryableFailureReason: String? = null
) {
    val hasConflict: Boolean get() = conflictReason != null
    val shouldRetry: Boolean get() = retryableFailureReason != null
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
