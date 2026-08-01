package com.enterprise.busvalidator.core.network

import com.enterprise.busvalidator.core.model.OperatorConfig
import com.enterprise.busvalidator.core.model.TerminalConfig

data class TerminalBootstrapResult(
    val terminalConfig: TerminalConfig,
    val serverTime: String?,
    val rawConfigResponse: String,
    val rawTerminalResponse: String,
    val rawFareResponse: String?
)

interface TerminalBootstrapApi {
    suspend fun fetchTerminalBootstrap(operatorConfig: OperatorConfig): TerminalBootstrapResult
}

data class RouteChangeRequest(
    val hardwareId: String,
    val routeCode: String,
    val operationalCode: String,
    val trip: String,
    val passengerIn: Int,
    val passengerOut: Int,
    val passengerOn: Int,
    val latitude: Double,
    val longitude: Double,
    val dateTime: String
)

data class RouteChangeResult(
    val isSuccess: Boolean,
    val routeCode: String?,
    val routeName: String?,
    val trip: String?,
    val fare: Long?,
    val operationalCode: String?,
    val operationalStart: String?,
    val operationalEnd: String?,
    val currentHalte: String?,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val endLatitude: Double?,
    val endLongitude: Double?,
    val rawResponse: String
)

data class QrisPaymentRequest(
    val endpoint: QrisEndpoint,
    val transactionId: String,
    val qrContent: String,
    val amount: Long,
    val hardwareId: String,
    val operationalCode: String,
    val trip: String,
    val route: String,
    val transactionDate: String,
    val latitude: Double,
    val longitude: Double,
    val productIndicator: String? = null,
    val merchantId: String? = null,
    val terminalId: String? = null,
    val fee: Long? = null,
    val qrType: String? = null,
    val captureId: String? = null
)

enum class QrisEndpoint(val path: String) {
    PAYMENT("payment"),
    CAPTURE("capture")
}

data class QrisPaymentResult(
    val code: String?,
    val message: String?,
    val isSuccess: Boolean,
    val isPending: Boolean,
    val isPreAuthSuccess: Boolean,
    val isFareCaptureSuccess: Boolean,
    val referenceNumber: String?,
    val invoiceNumber: String?,
    val rawResponse: String
)

interface TransitOperationsApi {
    suspend fun changeTrip(operatorConfig: OperatorConfig, request: RouteChangeRequest): RouteChangeResult
    suspend fun submitQrisPayment(
        operatorConfig: OperatorConfig,
        request: QrisPaymentRequest,
        authorization: String? = null,
        partnerId: String? = null,
        signature: String? = null
    ): QrisPaymentResult
}

data class AppUpdateManifest(
    val hasUpdate: Boolean,
    val apkUrl: String?,
    val versionCode: Long?,
    val versionName: String?,
    val versionNotes: List<String>,
    val statusFare: String,
    val isMajorVersionChanged: Boolean,
    val expectedSha256: String?,
    val rawResponse: String
)

interface AppUpdateManifestApi {
    suspend fun checkAppUpdate(
        manifestUrl: String,
        installedVersionName: String,
        installedVersionCode: Long
    ): AppUpdateManifest

    suspend fun checkAppUpdateFromBaseUrl(
        baseUrl: String,
        installedVersionName: String,
        installedVersionCode: Long
    ): AppUpdateManifest
}

internal object LegacyTransitEndpoints {
    const val GET_CONFIG = "get_config"
    const val INIT_TERMINAL = "init_terminal"
    const val GET_FARE = "get_fare"
    const val TRX_CARD = "trx_card"
    const val SENSOR_LOG = "sensor_log"
    const val CHANGE_TRIP = "change_trip"
    const val GET_VERSION = "get_version"
}
