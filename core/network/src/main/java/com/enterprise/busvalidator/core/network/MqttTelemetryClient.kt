package com.enterprise.busvalidator.core.network

import com.enterprise.busvalidator.core.common.AppDispatchers
import com.enterprise.busvalidator.core.model.DeviceIdentity
import com.enterprise.busvalidator.core.model.LocationTelemetryPayload
import com.enterprise.busvalidator.core.model.TelemetryStatus
import com.enterprise.busvalidator.core.security.EncryptedLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Realtime TLS MQTT Telemetry & Push Notification Client.
 * Streams GPS position, handles incoming QRIS push notifications, and executes remote commands.
 */
@Singleton
class MqttTelemetryClient @Inject constructor(
    private val logger: EncryptedLogger,
    private val dispatchers: AppDispatchers
) : LocationTelemetryMqttPublisher {
    private var mqttClient: MqttClient? = null
    private val brokerUrl = "ssl://mqtt.busvalidator.enterprise.com:8883"
    private val deviceId = DeviceIdentity.DEFAULT_DEVICE_ID
    private val started = AtomicBoolean(false)
    private val connectMutex = Mutex()
    private val publishMutex = Mutex()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _paymentPushFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val paymentPushFlow: SharedFlow<String> = _paymentPushFlow.asSharedFlow()

    private val _remoteCommandFlow = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)
    val remoteCommandFlow: SharedFlow<Pair<String, String>> = _remoteCommandFlow.asSharedFlow()

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) {
            return
        }

        scope.launch(dispatchers.io) {
            reconnectLoop()
        }
    }

    fun connect() {
        if (mqttClient?.isConnected == true) {
            return
        }

        try {
            connectBlocking()
        } catch (e: Exception) {
            logger.log("MQTT", "Connection error: ${e.message}", isError = true)
        }
    }

    private suspend fun reconnectLoop() {
        var retryDelayMs = INITIAL_RECONNECT_DELAY_MS
        while (kotlin.coroutines.coroutineContext.isActive) {
            val connected = tryConnect()
            retryDelayMs = if (connected) {
                CONNECTED_CHECK_INTERVAL_MS
            } else {
                (retryDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            }
            delay(retryDelayMs)
        }
    }

    private suspend fun tryConnect(): Boolean {
        return connectMutex.withLock {
            withContext(dispatchers.io) {
                if (mqttClient?.isConnected == true) {
                    _connectionState.value = true
                    return@withContext true
                }

                try {
                    connectBlocking()
                    true
                } catch (e: Exception) {
                    _connectionState.value = false
                    logger.log("MQTT", "Reconnect attempt failed: ${e.message}", isError = true)
                    false
                }
            }
        }
    }

    private fun connectBlocking() {
        val client = mqttClient ?: MqttClient(brokerUrl, deviceId, MemoryPersistence()).also { createdClient ->
            mqttClient = createdClient
            createdClient.setCallback(createCallback())
        }

        if (client.isConnected) {
            _connectionState.value = true
            return
        }

        client.connect(createConnectOptions())
        _connectionState.value = true
    }

    private fun createConnectOptions(): MqttConnectOptions {
        return MqttConnectOptions().apply {
            isCleanSession = false
            connectionTimeout = CONNECTION_TIMEOUT_SECONDS
            keepAliveInterval = KEEP_ALIVE_SECONDS
            maxInflight = MAX_IN_FLIGHT_MESSAGES
            isAutomaticReconnect = true
        }
    }

    private fun createCallback(): MqttCallbackExtended {
        return object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                _connectionState.value = true
                logger.log("MQTT", "Connected to MQTT broker (reconnect=$reconnect)")
                subscribeToTopics()
            }

            override fun connectionLost(cause: Throwable?) {
                _connectionState.value = false
                logger.log("MQTT", "Connection lost: ${cause?.message}", isError = true)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.payload?.let { String(it) } ?: return
                logger.log("MQTT", "Message arrived on [$topic]: $payload")

                when {
                    topic?.contains("/payment/response") == true -> {
                        _paymentPushFlow.tryEmit(payload)
                    }
                    topic?.contains("/command") == true -> {
                        val action = payload.substringBefore(":")
                        val params = payload.substringAfter(":", "")
                        _remoteCommandFlow.tryEmit(Pair(action, params))
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        }
    }

    private fun subscribeToTopics() {
        try {
            mqttClient?.subscribe("bus/validator/$deviceId/payment/response", 1)
            mqttClient?.subscribe("bus/validator/$deviceId/command", 1)
            logger.log("MQTT", "Subscribed to MQTT topics successfully")
        } catch (e: Exception) {
            logger.log("MQTT", "Subscription error: ${e.message}", isError = true)
        }
    }

    override suspend fun publishLocationTelemetry(payload: LocationTelemetryPayload): MqttPublishResult {
        val connected = tryConnect()
        if (!connected) {
            return MqttPublishResult(isSuccess = false, reason = "MQTT disconnected")
        }

        return publishJson(
            topic = "bus/validator/$deviceId/location",
            payload = payload.toJson()
        )
    }

    fun publishTelemetry(telemetry: TelemetryStatus) {
        if (mqttClient?.isConnected != true) {
            return
        }

        try {
            val topic = "bus/validator/$deviceId/telemetry"
            val payload = """
                {
                    "lat": ${telemetry.latitude},
                    "lon": ${telemetry.longitude},
                    "sats": ${telemetry.gpsSatellites},
                    "signal": ${telemetry.signalDbm},
                    "pending": ${telemetry.pendingSyncCount},
                    "dailyTx": ${telemetry.dailyTransactionCount},
                    "timestamp": ${System.currentTimeMillis()}
                }
            """.trimIndent()
            val message = MqttMessage(payload.toByteArray()).apply { qos = 1 }
            mqttClient?.publish(topic, message)
        } catch (e: Exception) {
            logger.log("MQTT", "Publish error: ${e.message}", isError = true)
        }
    }

    private suspend fun publishJson(topic: String, payload: String): MqttPublishResult {
        return publishMutex.withLock {
            withContext(dispatchers.io) {
                try {
                    val client = mqttClient
                    if (client?.isConnected != true) {
                        _connectionState.value = false
                        return@withContext MqttPublishResult(isSuccess = false, reason = "MQTT disconnected before publish")
                    }

                    val message = MqttMessage(payload.toByteArray()).apply {
                        qos = LOCATION_QOS
                        isRetained = false
                    }
                    client.publish(topic, message)
                    MqttPublishResult(isSuccess = true)
                } catch (e: Exception) {
                    _connectionState.value = false
                    logger.log("MQTT", "Location publish error: ${e.message}", isError = true)
                    MqttPublishResult(isSuccess = false, reason = e.message)
                }
            }
        }
    }

    private fun LocationTelemetryPayload.toJson(): String {
        return buildJsonObject {
            put("locationLogId", locationLogId?.let(::JsonPrimitive) ?: JsonNull)
            put("deviceId", JsonPrimitive(snapshot.deviceId))
            put("recordedAtUtc", JsonPrimitive(snapshot.recordedAtUtc))
            put("provider", JsonPrimitive(snapshot.provider))
            put("latitude", JsonPrimitive(snapshot.latitude))
            put("longitude", JsonPrimitive(snapshot.longitude))
            put("altitudeMeters", snapshot.altitudeMeters?.let(::JsonPrimitive) ?: JsonNull)
            put("accuracyMeters", snapshot.accuracyMeters?.let(::JsonPrimitive) ?: JsonNull)
            put("verticalAccuracyMeters", snapshot.verticalAccuracyMeters?.let(::JsonPrimitive) ?: JsonNull)
            put("bearingDegrees", snapshot.bearingDegrees?.let(::JsonPrimitive) ?: JsonNull)
            put("bearingAccuracyDegrees", snapshot.bearingAccuracyDegrees?.let(::JsonPrimitive) ?: JsonNull)
            put("speedMetersPerSecond", snapshot.speedMetersPerSecond?.let(::JsonPrimitive) ?: JsonNull)
            put("speedAccuracyMetersPerSecond", snapshot.speedAccuracyMetersPerSecond?.let(::JsonPrimitive) ?: JsonNull)
            put("elapsedRealtimeNanos", JsonPrimitive(snapshot.elapsedRealtimeNanos))
            put("satelliteCount", snapshot.satelliteCount?.let(::JsonPrimitive) ?: JsonNull)
            put("isMock", JsonPrimitive(snapshot.isMock))
            put("pendingLocationLogCount", JsonPrimitive(pendingLocationLogCount))
            put("deliveryAttempt", JsonPrimitive(deliveryAttempt))
            put("sentAtUtc", JsonPrimitive(System.currentTimeMillis()))
        }.toString()
    }

    private companion object {
        const val CONNECTION_TIMEOUT_SECONDS = 10
        const val KEEP_ALIVE_SECONDS = 20
        const val MAX_IN_FLIGHT_MESSAGES = 20
        const val LOCATION_QOS = 1
        const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        const val MAX_RECONNECT_DELAY_MS = 30_000L
        const val CONNECTED_CHECK_INTERVAL_MS = 10_000L
    }
}
