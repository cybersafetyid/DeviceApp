package com.enterprise.busvalidator.core.network

import com.enterprise.busvalidator.core.model.LocationTelemetryPayload
import com.enterprise.busvalidator.core.model.TransactionSyncItem
import com.enterprise.busvalidator.core.model.TransactionSyncResult

enum class TelemetryTransport {
    MQTT,
    API
}

data class MqttPublishResult(
    val isSuccess: Boolean,
    val reason: String? = null
)

interface LocationTelemetryMqttPublisher {
    suspend fun publishLocationTelemetry(payload: LocationTelemetryPayload): MqttPublishResult
}

interface LocationTelemetryApiFallback {
    suspend fun uploadLocationTelemetry(payload: LocationTelemetryPayload): Boolean
}

interface TransactionSyncApi {
    suspend fun uploadTransactions(
        batchId: String,
        transactions: List<TransactionSyncItem>
    ): TransactionSyncResult
}
