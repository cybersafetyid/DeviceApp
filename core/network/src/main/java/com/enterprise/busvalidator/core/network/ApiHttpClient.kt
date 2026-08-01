package com.enterprise.busvalidator.core.network

import com.enterprise.busvalidator.core.model.CardIssuerConfig
import com.enterprise.busvalidator.core.model.DeviceIdentity
import com.enterprise.busvalidator.core.model.LocationTelemetryPayload
import com.enterprise.busvalidator.core.model.OperatorConfig
import com.enterprise.busvalidator.core.model.TapMode
import com.enterprise.busvalidator.core.model.TerminalConfig
import com.enterprise.busvalidator.core.model.TransactionSyncItem
import com.enterprise.busvalidator.core.model.TransactionSyncResult
import com.enterprise.busvalidator.core.model.normalizedIssuerKey
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.enterprise.busvalidator.core.security.NativeSecurityVault
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ktor implementation of the legacy backend contract captured in docs/sample.
 *
 * Transport rules preserved from Network.java:
 * - Core validator API uses application/x-www-form-urlencoded for most endpoints.
 * - Raw telemetry and QRIS use application/json.
 * - Empty response is treated as a backend error, not a successful sync.
 */
@Singleton
class ApiHttpClient @Inject constructor(
    private val securityVault: NativeSecurityVault,
    private val logger: EncryptedLogger,
    private val runtimeConfigStore: OperatorRuntimeConfigStore
) : LocationTelemetryApiFallback,
    TransactionSyncApi,
    TerminalBootstrapApi,
    TransitOperationsApi,
    AppUpdateManifestApi {

    private val jsonParser = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
        install(ContentNegotiation) {
            json(jsonParser)
        }
    }

    suspend fun getTerminalConfig(operatorBaseUrl: String? = null): String {
        val baseUrl = securityVault.getSecureBaseUrl(operatorBaseUrl)
        val runtime = runtimeConfigStore.activeTerminalConfig
        return postLegacyForm(
            baseUrl = baseUrl,
            endpoint = LegacyTransitEndpoints.INIT_TERMINAL,
            fields = mapOf(
                "hwid" to runtime.hardwareId,
                "bus" to runtime.busCode,
                "date" to formatNow(MINUTE_DATE_FORMAT)
            )
        ).body
    }

    override suspend fun fetchTerminalBootstrap(operatorConfig: OperatorConfig): TerminalBootstrapResult {
        val baseUrl = securityVault.getSecureBaseUrl(operatorConfig.baseUrl)
        val runtime = runtimeConfigStore.activeTerminalConfig.copy(operatorConfig = operatorConfig)

        val configResponse = postLegacyForm(
            baseUrl = baseUrl,
            endpoint = LegacyTransitEndpoints.GET_CONFIG,
            fields = mapOf("hwid" to runtime.hardwareId)
        ).body
        val configRoot = LegacyApiResponseParser.parseObject(configResponse)
            ?: throw ApiContractException("Invalid get_config JSON response")
        if (!LegacyApiResponseParser.isSuccess(configRoot, successCodes = setOf("00"))) {
            throw ApiContractException(LegacyApiResponseParser.message(configRoot, "get_config rejected"))
        }

        val terminalResponse = postLegacyForm(
            baseUrl = baseUrl,
            endpoint = LegacyTransitEndpoints.INIT_TERMINAL,
            fields = mapOf(
                "hwid" to runtime.hardwareId,
                "bus" to runtime.busCode,
                "date" to formatNow(MINUTE_DATE_FORMAT)
            )
        ).body
        val terminalRoot = LegacyApiResponseParser.parseObject(terminalResponse)
            ?: throw ApiContractException("Invalid init_terminal JSON response")
        if (!LegacyApiResponseParser.isSuccess(terminalRoot)) {
            throw ApiContractException(LegacyApiResponseParser.message(terminalRoot, "init_terminal rejected"))
        }

        val fareResponse = runCatching {
            postLegacyForm(
                baseUrl = baseUrl,
                endpoint = LegacyTransitEndpoints.GET_FARE,
                fields = mapOf(
                    "hwid" to runtime.hardwareId,
                    "date" to formatNow(SECOND_DATE_FORMAT),
                    "lat" to 0.0,
                    "long" to 0.0
                )
            ).body
        }.getOrNull()
        val fareRoot = fareResponse?.let(LegacyApiResponseParser::parseObject)

        val terminalConfig = LegacyApiResponseParser.toTerminalConfig(
                operatorConfig = operatorConfig,
                currentRuntime = runtime,
                configRoot = configRoot,
                terminalRoot = terminalRoot,
                fareRoot = fareRoot
            )
        runtimeConfigStore.setTerminalConfig(terminalConfig)

        return TerminalBootstrapResult(
            terminalConfig = terminalConfig,
            serverTime = LegacyApiResponseParser.string(configRoot, "servertime", "server_time"),
            rawConfigResponse = configResponse,
            rawTerminalResponse = terminalResponse,
            rawFareResponse = fareResponse
        )
    }

    override suspend fun uploadLocationTelemetry(payload: LocationTelemetryPayload): Boolean {
        val runtime = runtimeConfigStore.activeTerminalConfig
        val baseUrl = securityVault.getSecureBaseUrl(runtime.operatorConfig.baseUrl)
        return try {
            val response = postLegacyForm(
                baseUrl = baseUrl,
                endpoint = LegacyTransitEndpoints.SENSOR_LOG,
                fields = payload.toLegacySensorLogFields(runtime)
            )
            val root = LegacyApiResponseParser.parseObject(response.body)
            response.statusCode in 200..299 && (root == null || LegacyApiResponseParser.isSuccess(root))
        } catch (e: Exception) {
            logger.log("ApiClient", "Telemetry fallback failed: ${e.message}", isError = true)
            false
        }
    }

    override suspend fun uploadTransactions(
        batchId: String,
        transactions: List<TransactionSyncItem>
    ): TransactionSyncResult {
        require(transactions.isNotEmpty()) { "Transaction sync batch must not be empty" }

        val runtime = runtimeConfigStore.activeTerminalConfig
        val baseUrl = securityVault.getSecureBaseUrl(runtime.operatorConfig.baseUrl)
        val acceptedIds = linkedSetOf<String>()
        var lastAcceptedCounter = 0

        for (transaction in transactions.sortedBy { it.transactionCounter }) {
            val result = runCatching {
                postLegacyForm(
                    baseUrl = baseUrl,
                    endpoint = LegacyTransitEndpoints.TRX_CARD,
                    fields = transaction.toLegacyTransactionFields(runtime)
                )
            }

            val response = result.getOrElse { error ->
                logger.log("ApiClient", "Transaction sync deferred: ${error.message}", isError = true)
                return TransactionSyncResult(
                    acceptedTransactionIds = acceptedIds,
                    backendLastCounter = lastAcceptedCounter,
                    retryableFailureReason = error.message ?: "Transaction sync failed"
                )
            }

            if (response.statusCode !in 200..299) {
                val retryable = response.statusCode >= 500
                return TransactionSyncResult(
                    acceptedTransactionIds = acceptedIds,
                    backendLastCounter = lastAcceptedCounter,
                    conflictReason = if (retryable) null else "HTTP_${response.statusCode}",
                    retryableFailureReason = if (retryable) "HTTP_${response.statusCode}" else null
                )
            }

            val root = LegacyApiResponseParser.parseObject(response.body)
            if (root == null) {
                return TransactionSyncResult(
                    acceptedTransactionIds = acceptedIds,
                    backendLastCounter = lastAcceptedCounter,
                    conflictReason = "Invalid trx_card JSON response"
                )
            }

            val modernAck = LegacyApiResponseParser.parseModernBatchAck(root)
            if (modernAck != null) {
                return modernAck
            }

            if (!LegacyApiResponseParser.isSuccess(root)) {
                return TransactionSyncResult(
                    acceptedTransactionIds = acceptedIds,
                    backendLastCounter = lastAcceptedCounter,
                    conflictReason = LegacyApiResponseParser.message(root, "trx_card rejected")
                )
            }

            acceptedIds += transaction.transactionId
            lastAcceptedCounter = transaction.transactionCounter
        }

        return TransactionSyncResult(
            acceptedTransactionIds = acceptedIds,
            backendLastCounter = lastAcceptedCounter
        )
    }

    override suspend fun changeTrip(operatorConfig: OperatorConfig, request: RouteChangeRequest): RouteChangeResult {
        val baseUrl = securityVault.getSecureBaseUrl(operatorConfig.baseUrl)
        val response = postLegacyForm(
            baseUrl = baseUrl,
            endpoint = LegacyTransitEndpoints.CHANGE_TRIP,
            fields = mapOf(
                "hwid" to request.hardwareId,
                "route_code" to request.routeCode,
                "operational" to request.operationalCode,
                "trip" to request.trip,
                "datetime" to request.dateTime,
                "in" to request.passengerIn,
                "out" to request.passengerOut,
                "on" to request.passengerOn,
                "lat" to request.latitude,
                "long" to request.longitude
            )
        )
        val root = LegacyApiResponseParser.parseObject(response.body)
            ?: throw ApiContractException("Invalid change_trip JSON response")
        return RouteChangeResult(
            isSuccess = response.statusCode in 200..299 && LegacyApiResponseParser.isSuccess(root),
            routeCode = LegacyApiResponseParser.string(root, "route_code"),
            routeName = LegacyApiResponseParser.string(root, "route", "route_name"),
            trip = LegacyApiResponseParser.string(root, "trip"),
            fare = LegacyApiResponseParser.long(root, "fare", "base_fare"),
            operationalCode = LegacyApiResponseParser.string(root, "operational"),
            operationalStart = LegacyApiResponseParser.string(root, "start"),
            operationalEnd = LegacyApiResponseParser.string(root, "end"),
            currentHalte = LegacyApiResponseParser.string(root, "n_halte_1", "current_halte"),
            startLatitude = LegacyApiResponseParser.double(root, "start_lat"),
            startLongitude = LegacyApiResponseParser.double(root, "start_lng"),
            endLatitude = LegacyApiResponseParser.double(root, "end_lat"),
            endLongitude = LegacyApiResponseParser.double(root, "end_lng"),
            rawResponse = response.body
        ).also { result ->
            if (result.isSuccess) {
                runtimeConfigStore.setTerminalConfig(
                    runtimeConfigStore.activeTerminalConfig.copy(
                        routeCode = result.routeCode ?: runtimeConfigStore.activeTerminalConfig.routeCode,
                        routeName = result.routeName ?: runtimeConfigStore.activeTerminalConfig.routeName,
                        baseFare = result.fare ?: runtimeConfigStore.activeTerminalConfig.baseFare,
                        trip = result.trip ?: runtimeConfigStore.activeTerminalConfig.trip,
                        operationalCode = result.operationalCode ?: runtimeConfigStore.activeTerminalConfig.operationalCode,
                        operationalStart = result.operationalStart ?: runtimeConfigStore.activeTerminalConfig.operationalStart,
                        operationalEnd = result.operationalEnd ?: runtimeConfigStore.activeTerminalConfig.operationalEnd,
                        currentHalte = result.currentHalte ?: runtimeConfigStore.activeTerminalConfig.currentHalte,
                        startLatitude = result.startLatitude ?: runtimeConfigStore.activeTerminalConfig.startLatitude,
                        startLongitude = result.startLongitude ?: runtimeConfigStore.activeTerminalConfig.startLongitude,
                        endLatitude = result.endLatitude ?: runtimeConfigStore.activeTerminalConfig.endLatitude,
                        endLongitude = result.endLongitude ?: runtimeConfigStore.activeTerminalConfig.endLongitude
                    )
                )
            }
        }
    }

    override suspend fun submitQrisPayment(
        operatorConfig: OperatorConfig,
        request: QrisPaymentRequest,
        authorization: String?,
        partnerId: String?,
        signature: String?
    ): QrisPaymentResult {
        val baseUrl = securityVault.getSecureBaseUrl(operatorConfig.baseUrl)
        val response = postJson(
            baseUrl = baseUrl,
            endpoint = request.endpoint.path,
            body = request.toJson(),
            headers = buildMap {
                authorization?.takeIf { it.isNotBlank() }?.let { put(HttpHeaders.Authorization, it) }
                partnerId?.takeIf { it.isNotBlank() }?.let { put("X-PARTNER", it) }
                signature?.takeIf { it.isNotBlank() }?.let { put("X-SIGNATURE", it) }
            }
        )
        val root = LegacyApiResponseParser.parseObject(response.body)
            ?: throw ApiContractException("Invalid QRIS JSON response")
        val code = LegacyApiResponseParser.string(root, "code")
        val data = LegacyApiResponseParser.objectOrNull(root["data"])
        return QrisPaymentResult(
            code = code,
            message = LegacyApiResponseParser.string(root, "message", "msg"),
            isSuccess = response.statusCode in 200..299 && code == "00",
            isPending = code == "10",
            isPreAuthSuccess = code == "00" && LegacyApiResponseParser.messageContainsAny(root, "N003", "N006"),
            isFareCaptureSuccess = code == "00" && LegacyApiResponseParser.messageContainsAny(root, "N001", "N004", "N005", "N007"),
            referenceNumber = data?.let { LegacyApiResponseParser.string(it, "ref_no") },
            invoiceNumber = data?.let { LegacyApiResponseParser.string(it, "invoice_number") },
            rawResponse = response.body
        )
    }

    override suspend fun checkAppUpdate(
        manifestUrl: String,
        installedVersionName: String,
        installedVersionCode: Long
    ): AppUpdateManifest {
        require(manifestUrl.isNotBlank()) { "App update manifest URL must not be blank" }

        val body = getRaw(manifestUrl)
        return parseAppUpdateManifest(
            body = body,
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode
        )
    }

    override suspend fun checkAppUpdateFromBaseUrl(
        baseUrl: String,
        installedVersionName: String,
        installedVersionCode: Long
    ): AppUpdateManifest {
        val secureBaseUrl = securityVault.getSecureBaseUrl(baseUrl)
        val manifestUrl = joinUrl(secureBaseUrl, LegacyTransitEndpoints.GET_VERSION)
        val body = getRaw(manifestUrl)
        return parseAppUpdateManifest(
            body = body,
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode
        )
    }

    private fun parseAppUpdateManifest(
        body: String,
        installedVersionName: String,
        installedVersionCode: Long
    ): AppUpdateManifest {
        val root = LegacyApiResponseParser.parseObject(body)
            ?: throw ApiContractException("Invalid app update JSON response")
        if (!LegacyApiResponseParser.isSuccess(root)) {
            return AppUpdateManifest(
                hasUpdate = false,
                apkUrl = null,
                versionCode = null,
                versionName = null,
                versionNotes = emptyList(),
                statusFare = "f",
                isMajorVersionChanged = false,
                expectedSha256 = null,
                rawResponse = body
            )
        }

        val data = LegacyApiResponseParser.objectOrNull(root["data"])
            ?: throw ApiContractException("App update response missing data object")
        val targetVersionCode = LegacyApiResponseParser.long(data, "versionCode", "version_code")
        val targetVersionName = LegacyApiResponseParser.string(data, "versionName", "version_name")
        val hasUpdate = targetVersionCode != null && targetVersionCode != installedVersionCode

        return AppUpdateManifest(
            hasUpdate = hasUpdate,
            apkUrl = LegacyApiResponseParser.string(data, "apkUrl", "apk_url", "downloadUrl", "url"),
            versionCode = targetVersionCode,
            versionName = targetVersionName,
            versionNotes = LegacyApiResponseParser.stringArray(data, "versionNotes", "version_notes"),
            statusFare = LegacyApiResponseParser.string(data, "status_fare") ?: "f",
            isMajorVersionChanged = isMajorVersionChanged(installedVersionName, targetVersionName),
            expectedSha256 = LegacyApiResponseParser.string(data, "sha256", "sha", "apkSha256"),
            rawResponse = body
        )
    }

    private fun runtimeDeviceId(): String {
        return runtimeConfigStore.activeTerminalConfig.hardwareId
            .takeIf { it.isNotBlank() && it != DeviceIdentity.DEFAULT_DEVICE_ID }
            ?: DeviceIdentity.DEFAULT_DEVICE_ID
    }

    private suspend fun postLegacyForm(
        baseUrl: String,
        endpoint: String,
        fields: Map<String, Any?>
    ): LegacyHttpResponse {
        val url = joinUrl(baseUrl, endpoint)
        logger.log("ApiClient", "POST form $url")
        val response: HttpResponse = client.submitForm(
            url = url,
            formParameters = fields.toParameters()
        ) {
            method = HttpMethod.Post
            accept(ContentType.Application.Json)
            headers {
                append("X-Device-Id", runtimeDeviceId())
            }
        }
        val body = response.bodyAsText()
        if (body.isBlank()) {
            throw ApiContractException("Empty response from $endpoint")
        }
        return LegacyHttpResponse(response.status.value, body)
    }

    private suspend fun postJson(
        baseUrl: String,
        endpoint: String,
        body: String,
        headers: Map<String, String> = emptyMap()
    ): LegacyHttpResponse {
        val url = joinUrl(baseUrl, endpoint)
        logger.log("ApiClient", "POST json $url")
        val response = client.post(url) {
            accept(ContentType.Application.Json)
            headers {
                append("X-Device-Id", runtimeDeviceId())
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                headers.forEach { (name, value) -> append(name, value) }
            }
            setBody(body)
        }
        val text = response.bodyAsText()
        if (text.isBlank()) {
            throw ApiContractException("Empty response from $endpoint")
        }
        return LegacyHttpResponse(response.status.value, text)
    }

    private suspend fun getRaw(url: String): String {
        val response = client.get(url) {
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            throw ApiContractException("GET rejected with HTTP ${response.status.value}")
        }
        return response.bodyAsText()
    }

    private fun LocationTelemetryPayload.toLegacySensorLogFields(runtime: TerminalConfig): Map<String, Any?> {
        return mapOf(
            "hwid" to runtime.hardwareId,
            "operational" to runtime.operationalCode,
            "route" to runtime.routeName,
            "trip" to runtime.trip,
            "in" to 0,
            "out" to 0,
            "on" to 0,
            "lat" to snapshot.latitude,
            "long" to snapshot.longitude,
            "date" to formatTimestamp(snapshot.recordedAtUtc, SECOND_DATE_FORMAT),
            "passenger_counter" to 0,
            "passenger_counter_kartu" to 0,
            "passenger_counter_qris" to 0,
            "passenger_counter_last" to pendingLocationLogCount
        )
    }

    private fun TransactionSyncItem.toLegacyTransactionFields(runtime: TerminalConfig): Map<String, Any?> {
        val issuerConfig = runtime.issuerConfigFor(bankIssuer)
        return mapOf(
            "hwid" to runtime.hardwareId,
            "operational" to runtime.operationalCode,
            "route" to runtime.routeName,
            "trip" to runtime.trip,
            "mid_partner" to (issuerConfig?.merchantId?.takeIf { it.isNotBlank() } ?: runtime.merchantId),
            "tid_partner" to (issuerConfig?.terminalId?.takeIf { it.isNotBlank() } ?: runtime.terminalId),
            "payment_method" to bankIssuer.toPaymentMethodCode(),
            "trx_code" to transCode,
            "trx_id" to transactionId,
            "trx_date" to formatTimestamp(timestampUtc, SECOND_DATE_FORMAT),
            "sn" to "",
            "uid" to cardUid,
            "balance_before" to initialBalance,
            "amount" to amountDeducted,
            "balance_after" to finalBalance,
            "lat" to 0.0,
            "long" to 0.0,
            "bank_name" to bankIssuer,
            "tapin" to "true",
            "card_type" to passengerProfile
        )
    }

    private fun QrisPaymentRequest.toJson(): String {
        return buildJsonObject {
            captureId?.let { put("trx_capture_id", JsonPrimitive(it)) }
            put("hwid", JsonPrimitive(hardwareId))
            put("operational", JsonPrimitive(operationalCode))
            put("trip", JsonPrimitive(trip))
            put("route", JsonPrimitive(route))
            put("trx_id", JsonPrimitive(transactionId))
            put("qrcontent", JsonPrimitive(qrContent))
            put("amount", JsonPrimitive(amount))
            put("trx_date", JsonPrimitive(transactionDate))
            put("lat", JsonPrimitive(latitude.toString()))
            put("long", JsonPrimitive(longitude.toString()))
            productIndicator?.let { put("product_indicator", JsonPrimitive(it)) }
            merchantId?.let { put("mid", JsonPrimitive(it)) }
            terminalId?.let { put("tid", JsonPrimitive(it)) }
            fee?.let { put("fee", JsonPrimitive(it)) }
            qrType?.let { put("qr_type", JsonPrimitive(it)) }
        }.toString()
    }

    private fun Map<String, Any?>.toParameters(): Parameters {
        return parameters {
            this@toParameters.forEach { (key, value) ->
                if (value != null) {
                    append(key, value.toString())
                }
            }
        }
    }

    private fun String.toPaymentMethodCode(): String {
        return when {
            contains("BCA", ignoreCase = true) -> "PM-0001"
            contains("BNI", ignoreCase = true) -> "PM-0002"
            contains("MANDIRI", ignoreCase = true) -> "PM-0006"
            contains("BRI", ignoreCase = true) -> "PM-0007"
            contains("QRIS", ignoreCase = true) -> "PM-QRIS"
            else -> "PM-UNKNOWN"
        }
    }

    private fun joinUrl(baseUrl: String, endpoint: String): String {
        val trimmedBase = baseUrl.trimEnd('/')
        val trimmedEndpoint = endpoint.trimStart('/')
        return "$trimmedBase/$trimmedEndpoint"
    }

    private fun isMajorVersionChanged(oldVersion: String, newVersion: String?): Boolean {
        val oldMajor = oldVersion.substringBefore('.').toIntOrNull()
        val newMajor = newVersion?.substringBefore('.')?.toIntOrNull()
        return oldMajor != null && newMajor != null && oldMajor != newMajor
    }

    private fun formatNow(pattern: String): String {
        return formatTimestamp(System.currentTimeMillis(), pattern)
    }

    private fun formatTimestamp(timestampUtc: Long, pattern: String): String {
        return SimpleDateFormat(pattern, Locale.US).format(Date(timestampUtc))
    }

    private data class LegacyHttpResponse(
        val statusCode: Int,
        val body: String
    )

    private companion object {
        const val REQUEST_TIMEOUT_MS = 60_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val SOCKET_TIMEOUT_MS = 60_000L
        const val MINUTE_DATE_FORMAT = "yyyy-MM-dd HH:mm"
        const val SECOND_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
    }
}

class ApiContractException(message: String) : IllegalStateException(message)

internal object LegacyApiResponseParser {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    fun parseObject(body: String): JsonObject? {
        return runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
    }

    fun isSuccess(root: JsonObject, successCodes: Set<String> = setOf("00", "1000")): Boolean {
        val status = string(root, "status")?.lowercase(Locale.US)
        if (status == "true" || status == "success") return true
        if (status == "false" || status == "failed") return false
        return string(root, "code") in successCodes
    }

    fun message(root: JsonObject, fallback: String): String {
        return string(root, "message", "msg", "desc", "error") ?: fallback
    }

    fun toTerminalConfig(
        operatorConfig: OperatorConfig,
        currentRuntime: TerminalConfig,
        configRoot: JsonObject,
        terminalRoot: JsonObject,
        fareRoot: JsonObject?
    ): TerminalConfig {
        val configObject = objectOrNull(configRoot["config"]) ?: configRoot
        val serverTime = string(configRoot, "servertime", "server_time").orEmpty()
        val issuerConfigs = parseIssuerConfigs(configObject)
        val primaryIssuer = issuerConfigs.values.firstOrNull {
            it.isEnabled && it.merchantId.isNotBlank() && it.terminalId.isNotBlank() && it.issuerName != "QRIS"
        } ?: issuerConfigs.values.firstOrNull {
            it.merchantId.isNotBlank() && it.terminalId.isNotBlank() && it.issuerName != "QRIS"
        }
        val qrisIssuer = issuerConfigs["QRIS"]
        return TerminalConfig(
            merchantId = firstString(terminalRoot, configObject, names = arrayOf("mid", "mid_partner", "merchant_id"))
                ?: primaryIssuer?.merchantId
                ?: currentRuntime.merchantId,
            terminalId = firstString(terminalRoot, configObject, names = arrayOf("tid", "tid_partner", "terminal_id"))
                ?: primaryIssuer?.terminalId
                ?: currentRuntime.terminalId,
            pinCode = firstString(terminalRoot, configObject, names = arrayOf("pincode", "pin_code", "pin"))
                ?: primaryIssuer?.pinCode
                ?: currentRuntime.pinCode,
            processingCode = firstString(terminalRoot, configObject, names = arrayOf("processing_code", "proc_code"))
                ?: primaryIssuer?.processingCode
                ?: currentRuntime.processingCode,
            samId = firstString(terminalRoot, configObject, names = arrayOf("sam_id", "samId"))
                ?: primaryIssuer?.samId
                ?: currentRuntime.samId,
            marriageCode = firstString(terminalRoot, configObject, names = arrayOf("marriage_code", "marriageCode"))
                ?: primaryIssuer?.marriageCodes?.firstOrNull()
                ?: currentRuntime.marriageCode,
            hardwareId = firstString(terminalRoot, configObject, fareRoot, names = arrayOf("hwid"))
                ?: currentRuntime.hardwareId,
            tapMode = parseTapMode(firstString(terminalRoot, configObject, names = arrayOf("tap_mode", "tapMode"))),
            operatorConfig = operatorConfig,
            routeCode = firstString(fareRoot, terminalRoot, configObject, names = arrayOf("route_code", "c_route"))
                ?: operatorConfig.defaultRouteCode,
            routeName = firstString(fareRoot, terminalRoot, configObject, names = arrayOf("route", "route_name", "n_route"))
                ?: operatorConfig.defaultRouteName,
            busCode = firstString(terminalRoot, configObject, names = arrayOf("bus", "bus_code", "c_bus"))
                ?: firstString(configObject, names = arrayOf("terminal"))
                ?: currentRuntime.busCode,
            baseFare = firstLong(fareRoot, terminalRoot, configObject, names = arrayOf("fare", "base_fare", "amount"))
                ?: operatorConfig.fareRulePolicy.baseFare,
            trip = firstString(fareRoot, terminalRoot, configObject, names = arrayOf("trip"))
                ?: currentRuntime.trip,
            operationalCode = firstString(fareRoot, terminalRoot, configObject, names = arrayOf("operational", "operational_code"))
                ?: currentRuntime.operationalCode,
            operationalStart = firstString(fareRoot, terminalRoot, configObject, names = arrayOf("start", "operational_start"))
                ?: currentRuntime.operationalStart,
            operationalEnd = firstString(fareRoot, terminalRoot, configObject, names = arrayOf("end", "operational_end"))
                ?: currentRuntime.operationalEnd,
            currentHalte = firstString(fareRoot, terminalRoot, configObject, names = arrayOf("n_halte_1", "current_halte"))
                ?: currentRuntime.currentHalte,
            startLatitude = firstDouble(fareRoot, terminalRoot, configObject, names = arrayOf("start_lat")),
            startLongitude = firstDouble(fareRoot, terminalRoot, configObject, names = arrayOf("start_lng")),
            endLatitude = firstDouble(fareRoot, terminalRoot, configObject, names = arrayOf("end_lat")),
            endLongitude = firstDouble(fareRoot, terminalRoot, configObject, names = arrayOf("end_lng")),
            qrisEnabled = firstBoolean(terminalRoot, configObject, names = arrayOf("statusQris", "is_qris"))
                ?: currentRuntime.qrisEnabled,
            rawQris = firstString(terminalRoot, configObject, names = arrayOf("rawQRIS", "raw_qris"))
                ?: currentRuntime.rawQris,
            qrisMerchantId = firstString(terminalRoot, configObject, names = arrayOf("mid_qris", "qris_mid"))
                ?: qrisIssuer?.merchantId
                ?: currentRuntime.qrisMerchantId,
            qrisTerminalId = firstString(terminalRoot, configObject, names = arrayOf("tid_qris", "qris_tid"))
                ?: qrisIssuer?.terminalId
                ?: currentRuntime.qrisTerminalId,
            terminalCode = firstString(configObject, terminalRoot, names = arrayOf("terminal_code", "terminal"))
                ?: currentRuntime.terminalCode,
            vehiclePlate = firstString(configObject, terminalRoot, names = arrayOf("nopol", "plat"))
                ?: currentRuntime.vehiclePlate,
            transportationType = firstString(configObject, terminalRoot, names = arrayOf("transportation_type"))
                ?: currentRuntime.transportationType,
            configVersion = firstString(configObject, names = arrayOf("version"))
                ?: currentRuntime.configVersion,
            fareFileName = firstString(fareRoot, names = arrayOf("filename"))
                ?: currentRuntime.fareFileName,
            fareFileUrl = firstString(fareRoot, names = arrayOf("filecard"))
                ?: currentRuntime.fareFileUrl,
            serverTime = serverTime,
            validationRadiusMeters = firstDouble(configObject, names = arrayOf("radius"))
                ?: currentRuntime.validationRadiusMeters,
            direction = firstString(configObject, terminalRoot, names = arrayOf("direction"))
                ?: currentRuntime.direction,
            doubleTapAllowed = firstBoolean(configObject, names = arrayOf("doubleTap", "double_tap"))
                ?: currentRuntime.doubleTapAllowed,
            locationLimitSeconds = firstInt(configObject, names = arrayOf("locationLimit", "location_limit"))
                ?: currentRuntime.locationLimitSeconds,
            cityCode = firstString(terminalRoot, configObject, names = arrayOf("c_city"))
                ?: currentRuntime.cityCode,
            cityName = firstString(terminalRoot, configObject, names = arrayOf("n_city"))
                ?: currentRuntime.cityName,
            corridorCode = firstString(terminalRoot, configObject, names = arrayOf("c_corridor"))
                ?: currentRuntime.corridorCode,
            corridorName = firstString(terminalRoot, configObject, names = arrayOf("n_corridor"))
                ?: currentRuntime.corridorName,
            stopCode = firstString(terminalRoot, configObject, names = arrayOf("c_stop"))
                ?: currentRuntime.stopCode,
            stopName = firstString(terminalRoot, configObject, names = arrayOf("n_stop"))
                ?: currentRuntime.stopName,
            cycleTimeSeconds = firstInt(terminalRoot, configObject, names = arrayOf("cycle_time", "cycleTime"))
                ?: currentRuntime.cycleTimeSeconds,
            cardIssuers = issuerConfigs.ifEmpty { currentRuntime.cardIssuers }
        )
    }

    fun parseModernBatchAck(root: JsonObject): TransactionSyncResult? {
        val acceptedIds = root["acceptedTransactionIds"]
            ?.runCatchingArray()
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
        val backendLastCounter = root["backendLastCounter"]?.jsonPrimitive?.intOrNull
        val conflictReason = string(root, "conflictReason")

        if (acceptedIds == null && backendLastCounter == null && conflictReason == null) {
            return null
        }

        return TransactionSyncResult(
            acceptedTransactionIds = acceptedIds.orEmpty(),
            backendLastCounter = backendLastCounter ?: 0,
            conflictReason = conflictReason
        )
    }

    fun string(root: JsonObject, vararg names: String): String? {
        return names.firstNotNullOfOrNull { name ->
            root[name]?.jsonPrimitiveOrNull()?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        }
    }

    fun long(root: JsonObject, vararg names: String): Long? {
        return names.firstNotNullOfOrNull { name ->
            root[name]?.jsonPrimitiveOrNull()?.contentOrNull?.legacyNumberString()?.toLongOrNull()
        }
    }

    fun int(root: JsonObject, vararg names: String): Int? {
        return names.firstNotNullOfOrNull { name ->
            root[name]?.jsonPrimitiveOrNull()?.contentOrNull?.legacyNumberString()?.toIntOrNull()
        }
    }

    fun double(root: JsonObject, vararg names: String): Double? {
        return names.firstNotNullOfOrNull { name ->
            root[name]?.jsonPrimitiveOrNull()?.let { primitive ->
                primitive.doubleOrNull ?: primitive.contentOrNull?.legacyNumberString()?.toDoubleOrNull()
            }
        }
    }

    fun stringArray(root: JsonObject, vararg names: String): List<String> {
        val element = names.firstNotNullOfOrNull { root[it] } ?: return emptyList()
        return element.runCatchingArray()
            ?.mapNotNull { it.jsonPrimitiveOrNull()?.contentOrNull }
            ?: emptyList()
    }

    private fun firstString(vararg roots: JsonObject?, names: Array<String>): String? {
        return roots.firstNotNullOfOrNull { root ->
            root?.let { string(it, *names) }
        }
    }

    private fun firstLong(vararg roots: JsonObject?, names: Array<String>): Long? {
        return roots.firstNotNullOfOrNull { root ->
            root?.let { long(it, *names) }
        }
    }

    private fun firstInt(vararg roots: JsonObject?, names: Array<String>): Int? {
        return roots.firstNotNullOfOrNull { root ->
            root?.let { int(it, *names) }
        }
    }

    private fun firstDouble(vararg roots: JsonObject?, names: Array<String>): Double? {
        return roots.firstNotNullOfOrNull { root ->
            root?.let { double(it, *names) }
        }
    }

    private fun firstBoolean(vararg roots: JsonObject?, names: Array<String>): Boolean? {
        return roots.firstNotNullOfOrNull { root ->
            root?.let {
                names.firstNotNullOfOrNull { name ->
                    it[name]?.jsonPrimitiveOrNull()?.contentOrNull?.let { value ->
                        when (value.lowercase(Locale.US)) {
                            "true", "1", "yes", "y" -> true
                            "false", "0", "no", "n" -> false
                            else -> null
                        }
                    }
                }
            }
        }
    }

    private fun parseTapMode(value: String?): TapMode {
        val normalized = value?.uppercase(Locale.US)?.replace("-", "_").orEmpty()
        return when {
            normalized.contains("TAP_IN_ONLY") || normalized == "IN" -> TapMode.TAP_IN_ONLY
            normalized.contains("TAP_OUT_ONLY") || normalized == "OUT" -> TapMode.TAP_OUT_ONLY
            else -> TapMode.TAP_IN_OUT
        }
    }

    private fun parseIssuerConfigs(configObject: JsonObject): Map<String, CardIssuerConfig> {
        val issuerEntries = configObject["issuers"]?.runCatchingArray().orEmpty()
        val configs = linkedMapOf<String, CardIssuerConfig>()
        issuerEntries.forEach { issuerEntry ->
            objectOrNull(issuerEntry)?.forEach { (issuerName, issuerElement) ->
                val issuerObject = objectOrNull(issuerElement) ?: return@forEach
                val marriages = issuerObject["marriages"]
                    ?.runCatchingArray()
                    ?.mapNotNull { marriage ->
                        objectOrNull(marriage)?.let { string(it, "code", "marriage_code", "marriageCode") }
                    }
                    .orEmpty()
                val issuerConfig = CardIssuerConfig(
                    issuerName = issuerName.normalizedIssuerKey(),
                    merchantId = string(issuerObject, "mid", "merchant_id").orEmpty(),
                    terminalId = string(issuerObject, "tid", "terminal_id").orEmpty(),
                    slot = string(issuerObject, "slot").orEmpty(),
                    isEnabled = boolean(issuerObject, "status") ?: false,
                    pinCode = string(issuerObject, "pinCode", "pincode", "pin_code", "pin").orEmpty(),
                    processingCode = string(issuerObject, "processingCode", "processing_code", "proc_code").orEmpty(),
                    samId = string(issuerObject, "samId", "sam_id").orEmpty(),
                    marriageCodes = marriages,
                    checkTimeSeconds = int(issuerObject, "checktime", "check_time"),
                    validTimeSeconds = int(issuerObject, "validtime", "valid_time")
                )
                configs[issuerConfig.issuerName] = issuerConfig
            }
        }
        return configs
    }

    private fun boolean(root: JsonObject, vararg names: String): Boolean? {
        return names.firstNotNullOfOrNull { name ->
            root[name]?.jsonPrimitiveOrNull()?.contentOrNull?.let { value ->
                when (value.lowercase(Locale.US)) {
                    "true", "1", "yes", "y" -> true
                    "false", "0", "no", "n" -> false
                    else -> null
                }
            }
        }
    }

    fun messageContainsAny(root: JsonObject, vararg needles: String): Boolean {
        val message = message(root, fallback = "")
        return needles.any { needle -> message.contains(needle, ignoreCase = true) }
    }

    private fun JsonElement.runCatchingArray() = runCatching { jsonArray }.getOrNull()

    fun objectOrNull(element: JsonElement?) = element?.let { runCatching { it.jsonObject }.getOrNull() }

    private fun JsonElement.jsonPrimitiveOrNull() = when (this) {
        JsonNull -> null
        is JsonPrimitive -> this
        else -> null
    }

    private fun String.legacyNumberString(): String {
        return trim().removeSuffix("f").removeSuffix("F")
    }
}
