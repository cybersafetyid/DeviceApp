package com.enterprise.busvalidator.core.network

import com.enterprise.busvalidator.core.common.AppDispatchers
import com.enterprise.busvalidator.core.model.DeviceIdentity
import com.enterprise.busvalidator.core.model.LocationTelemetryPayload
import com.enterprise.busvalidator.core.model.MqttBrokerConfig
import com.enterprise.busvalidator.core.model.MqttTransport
import com.enterprise.busvalidator.core.model.TelemetryStatus
import com.enterprise.busvalidator.core.model.TerminalConfig
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
    private val dispatchers: AppDispatchers,
    private val runtimeConfigStore: OperatorRuntimeConfigStore
) : LocationTelemetryMqttPublisher {
    private var mqttClient: MqttClient? = null
    @Volatile
    private var brokerConfig = DEFAULT_BROKER_CONFIG
    @Volatile
    private var runtimeConfig = runtimeConfigStore.activeTerminalConfig
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

    @Synchronized
    fun configureBroker(config: MqttBrokerConfig) {
        if (brokerConfig == config) return

        val oldBrokerUrl = brokerConfig.brokerUrl
        brokerConfig = config
        runCatching {
            mqttClient?.takeIf { it.isConnected }?.disconnect()
            mqttClient?.close()
        }.onFailure { error ->
            logger.log("MQTT", "Failed closing MQTT client for broker switch: ${error.message}", isError = true)
        }
        mqttClient = null
        _connectionState.value = false
        logger.log("MQTT", "Broker changed from $oldBrokerUrl to ${config.brokerUrl}")
    }

    @Synchronized
    fun configureRuntime(config: TerminalConfig) {
        runtimeConfigStore.setTerminalConfig(config)
        val oldRuntime = runtimeConfig
        runtimeConfig = config
        val topicChanged = oldRuntime.busCode != config.busCode ||
            oldRuntime.hardwareId != config.hardwareId ||
            oldRuntime.operatorConfig.legacyRegionName != config.operatorConfig.legacyRegionName
        if (!topicChanged) return

        runCatching {
            mqttClient?.takeIf { it.isConnected }?.disconnect()
            mqttClient?.close()
        }.onFailure { error ->
            logger.log("MQTT", "Failed closing MQTT client for runtime switch: ${error.message}", isError = true)
        }
        mqttClient = null
        _connectionState.value = false
        logger.log(
            "MQTT",
            "Runtime topic changed to region=${config.operatorConfig.legacyRegionName}, bus=${config.busCode}, hwid=${config.hardwareId}"
        )
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
        val activeBrokerUrl = brokerConfig.brokerUrl
        val clientId = runtimeConfig.mqttTopicConfig.clientId(runtimeConfig.busCode)
        val client = mqttClient ?: MqttClient(activeBrokerUrl, clientId, MemoryPersistence()).also { createdClient ->
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
        val config = brokerConfig
        val willPayload = """{"status":"offline","bus_code":"${runtimeConfig.busCode}"}"""
        return MqttConnectOptions().apply {
            userName = config.username
            password = config.password.toCharArray()
            isCleanSession = config.cleanSession
            connectionTimeout = CONNECTION_TIMEOUT_SECONDS
            keepAliveInterval = KEEP_ALIVE_SECONDS
            maxInflight = MAX_IN_FLIGHT_MESSAGES
            isAutomaticReconnect = false
            setWill(runtimeConfig.mqttTopicConfig.statusTopic, willPayload.toByteArray(), STATUS_QOS, true)
        }
    }

    private fun createCallback(): MqttCallbackExtended {
        return object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                _connectionState.value = true
                logger.log("MQTT", "Connected to MQTT broker ${serverURI ?: brokerConfig.brokerUrl} (reconnect=$reconnect)")
                subscribeToTopics()
                publishStatusOnline()
            }

            override fun connectionLost(cause: Throwable?) {
                _connectionState.value = false
                logger.log("MQTT", "Connection lost: ${cause?.message}", isError = true)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.payload?.let { String(it) } ?: return
                logger.log("MQTT", "Message arrived on [$topic]: $payload")

                when {
                    topic == runtimeConfig.mqttTopicConfig.busTopic(runtimeConfig.busCode) -> {
                        _paymentPushFlow.tryEmit(payload)
                    }
                    topic == runtimeConfig.mqttTopicConfig.notificationTopic(runtimeConfig.hardwareId) -> {
                        val action = extractRemoteAction(payload) ?: "notification"
                        _remoteCommandFlow.tryEmit(Pair(action, payload))
                    }
                    topic == runtimeConfig.mqttTopicConfig.commandTopic -> {
                        if (isCommandForActiveBus(payload)) {
                            val action = extractRemoteAction(payload) ?: "bus_command"
                            _remoteCommandFlow.tryEmit(Pair(action, payload))
                        }
                    }
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
            val topics = runtimeConfig.mqttTopicConfig
            mqttClient?.subscribe(topics.busTopic(runtimeConfig.busCode), LEGACY_TOPIC_QOS)
            mqttClient?.subscribe(topics.notificationTopic(runtimeConfig.hardwareId), LEGACY_TOPIC_QOS)
            mqttClient?.subscribe(topics.commandTopic, LEGACY_TOPIC_QOS)
            mqttClient?.subscribe(topics.whitelistTopic(), LEGACY_TOPIC_QOS)
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
            topic = runtimeConfig.mqttTopicConfig.busTopic(runtimeConfig.busCode),
            payload = payload.toJson()
        )
    }

    fun publishTelemetry(telemetry: TelemetryStatus) {
        if (mqttClient?.isConnected != true) {
            return
        }

        try {
            val topic = runtimeConfig.mqttTopicConfig.busTopic(runtimeConfig.busCode)
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

    private fun publishStatusOnline() {
        val client = mqttClient ?: return
        if (!client.isConnected) return
        runCatching {
            val payload = """{"status":"online","bus_code":"${runtimeConfig.busCode}"}"""
            val message = MqttMessage(payload.toByteArray()).apply {
                qos = STATUS_QOS
                isRetained = true
            }
            client.publish(runtimeConfig.mqttTopicConfig.statusTopic, message)
        }.onFailure { error ->
            logger.log("MQTT", "Publish online status failed: ${error.message}", isError = true)
        }
    }

    private fun extractRemoteAction(payload: String): String? {
        val actionPattern = Regex(""""(?:action|cmd|command)"\s*:\s*"([^"]+)"""")
        return actionPattern.find(payload)?.groupValues?.getOrNull(1)
            ?: payload.substringBefore(":", "").takeIf { it.isNotBlank() && it.length < payload.length }
    }

    private fun isCommandForActiveBus(payload: String): Boolean {
        val busCodePattern = Regex(""""bus_code"\s*:\s*"([^"]+)"""")
        val targetBusCode = busCodePattern.find(payload)?.groupValues?.getOrNull(1) ?: return true
        return targetBusCode == runtimeConfig.busCode
    }

    private companion object {
        val DEFAULT_BROKER_CONFIG = MqttBrokerConfig(
            host = "mqtt.jsa2.host",
            port = 12345,
            transport = MqttTransport.TCP
        )
        const val CONNECTION_TIMEOUT_SECONDS = 10
        const val KEEP_ALIVE_SECONDS = 20
        const val MAX_IN_FLIGHT_MESSAGES = 20
        const val LOCATION_QOS = 1
        const val LEGACY_TOPIC_QOS = 0
        const val STATUS_QOS = 1
        const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        const val MAX_RECONNECT_DELAY_MS = 30_000L
        const val CONNECTED_CHECK_INTERVAL_MS = 10_000L
    }
}
