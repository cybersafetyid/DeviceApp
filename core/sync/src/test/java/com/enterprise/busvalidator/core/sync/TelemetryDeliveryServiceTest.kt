package com.enterprise.busvalidator.core.sync

import com.enterprise.busvalidator.core.model.BusLocationSnapshot
import com.enterprise.busvalidator.core.model.LocationTelemetryPayload
import com.enterprise.busvalidator.core.network.LocationTelemetryApiFallback
import com.enterprise.busvalidator.core.network.LocationTelemetryMqttPublisher
import com.enterprise.busvalidator.core.network.MqttPublishResult
import com.enterprise.busvalidator.core.network.TelemetryTransport
import com.enterprise.busvalidator.core.security.EncryptedLogger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryDeliveryServiceTest {

    @Test
    fun deliver_usesMqttWhenPublishSucceeds() = runTest {
        val mqtt = FakeMqttPublisher(MqttPublishResult(isSuccess = true))
        val api = FakeApiFallback(result = false)
        val service = TelemetryDeliveryService(mqtt, api, NoopLogger())

        val outcome = service.deliver(testPayload())

        assertTrue(outcome.isDelivered)
        assertEquals(TelemetryTransport.MQTT, outcome.transport)
        assertEquals(1, mqtt.publishCount)
        assertEquals(0, api.uploadCount)
    }

    @Test
    fun deliver_usesApiFallbackWhenMqttFails() = runTest {
        val mqtt = FakeMqttPublisher(MqttPublishResult(isSuccess = false, reason = "broker unavailable"))
        val api = FakeApiFallback(result = true)
        val service = TelemetryDeliveryService(mqtt, api, NoopLogger())

        val outcome = service.deliver(testPayload())

        assertTrue(outcome.isDelivered)
        assertEquals(TelemetryTransport.API, outcome.transport)
        assertEquals(1, mqtt.publishCount)
        assertEquals(1, api.uploadCount)
    }

    @Test
    fun deliver_reportsFailureWhenBothTransportsFail() = runTest {
        val mqtt = FakeMqttPublisher(MqttPublishResult(isSuccess = false, reason = "offline"))
        val api = FakeApiFallback(result = false)
        val service = TelemetryDeliveryService(mqtt, api, NoopLogger())

        val outcome = service.deliver(testPayload())

        assertFalse(outcome.isDelivered)
        assertEquals(null, outcome.transport)
        assertTrue(outcome.errorMessage!!.contains("MQTT failed"))
        assertEquals(1, mqtt.publishCount)
        assertEquals(1, api.uploadCount)
    }

    private fun testPayload(): LocationTelemetryPayload {
        return LocationTelemetryPayload(
            locationLogId = 10L,
            snapshot = BusLocationSnapshot(
                recordedAtUtc = 1_800_000_000_000L,
                provider = "gps",
                latitude = -6.175392,
                longitude = 106.827153,
                altitudeMeters = 12.0,
                accuracyMeters = 4.5f,
                verticalAccuracyMeters = 8.0f,
                bearingDegrees = 90.0f,
                bearingAccuracyDegrees = 3.0f,
                speedMetersPerSecond = 7.2f,
                speedAccuracyMetersPerSecond = 0.7f,
                elapsedRealtimeNanos = 20_000L,
                satelliteCount = 9,
                isMock = false
            ),
            pendingLocationLogCount = 1,
            deliveryAttempt = 1
        )
    }

    private class FakeMqttPublisher(
        private val result: MqttPublishResult
    ) : LocationTelemetryMqttPublisher {
        var publishCount = 0

        override suspend fun publishLocationTelemetry(payload: LocationTelemetryPayload): MqttPublishResult {
            publishCount += 1
            return result
        }
    }

    private class FakeApiFallback(
        private val result: Boolean
    ) : LocationTelemetryApiFallback {
        var uploadCount = 0

        override suspend fun uploadLocationTelemetry(payload: LocationTelemetryPayload): Boolean {
            uploadCount += 1
            return result
        }
    }

    private class NoopLogger : EncryptedLogger() {
        override fun log(tag: String, message: String, isError: Boolean) = Unit
    }
}
