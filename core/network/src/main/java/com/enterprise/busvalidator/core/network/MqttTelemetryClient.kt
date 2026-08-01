package com.enterprise.busvalidator.core.network

import com.enterprise.busvalidator.core.model.TelemetryStatus
import com.enterprise.busvalidator.core.security.EncryptedLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Realtime TLS MQTT Telemetry & Push Notification Client.
 * Streams GPS position, handles incoming QRIS push notifications, and executes remote commands.
 */
@Singleton
class MqttTelemetryClient @Inject constructor(
    private val logger: EncryptedLogger
) {
    private var mqttClient: MqttClient? = null
    private val brokerUrl = "ssl://mqtt.busvalidator.enterprise.com:8883"
    private val deviceId = "BUS-1049-VAL01"

    private val _paymentPushFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val paymentPushFlow: SharedFlow<String> = _paymentPushFlow.asSharedFlow()

    private val _remoteCommandFlow = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)
    val remoteCommandFlow: SharedFlow<Pair<String, String>> = _remoteCommandFlow.asSharedFlow()

    fun connect() {
        try {
            mqttClient = MqttClient(brokerUrl, deviceId, MemoryPersistence())
            val options = MqttConnectOptions().apply {
                isCleanSession = false
                connectionTimeout = 10
                keepAliveInterval = 20
                isAutomaticReconnect = true
            }

            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    logger.log("MQTT", "Connected to MQTT broker (reconnect=$reconnect)")
                    subscribeToTopics()
                }

                override fun connectionLost(cause: Throwable?) {
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
            })

            mqttClient?.connect(options)
        } catch (e: Exception) {
            logger.log("MQTT", "Connection error: ${e.message}", isError = true)
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

    fun publishTelemetry(telemetry: TelemetryStatus) {
        if (mqttClient?.isConnected != true) return
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
}
