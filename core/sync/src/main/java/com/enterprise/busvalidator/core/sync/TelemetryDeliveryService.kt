package com.enterprise.busvalidator.core.sync

import com.enterprise.busvalidator.core.model.LocationTelemetryPayload
import com.enterprise.busvalidator.core.network.LocationTelemetryApiFallback
import com.enterprise.busvalidator.core.network.LocationTelemetryMqttPublisher
import com.enterprise.busvalidator.core.network.TelemetryTransport
import com.enterprise.busvalidator.core.security.EncryptedLogger
import javax.inject.Inject
import javax.inject.Singleton

data class TelemetryDeliveryOutcome(
    val isDelivered: Boolean,
    val transport: TelemetryTransport?,
    val errorMessage: String?
)

@Singleton
class TelemetryDeliveryService @Inject constructor(
    private val mqttPublisher: LocationTelemetryMqttPublisher,
    private val apiFallback: LocationTelemetryApiFallback,
    private val logger: EncryptedLogger
) {
    suspend fun deliver(payload: LocationTelemetryPayload): TelemetryDeliveryOutcome {
        val mqttResult = mqttPublisher.publishLocationTelemetry(payload)
        if (mqttResult.isSuccess) {
            return TelemetryDeliveryOutcome(
                isDelivered = true,
                transport = TelemetryTransport.MQTT,
                errorMessage = null
            )
        }

        logger.log(
            "TelemetryDelivery",
            "MQTT telemetry failed, using API fallback: ${mqttResult.reason ?: "unknown"}",
            isError = true
        )

        val apiSuccess = apiFallback.uploadLocationTelemetry(payload)
        if (apiSuccess) {
            return TelemetryDeliveryOutcome(
                isDelivered = true,
                transport = TelemetryTransport.API,
                errorMessage = null
            )
        }

        return TelemetryDeliveryOutcome(
            isDelivered = false,
            transport = null,
            errorMessage = "MQTT failed: ${mqttResult.reason ?: "unknown"}; API fallback failed"
        )
    }
}
