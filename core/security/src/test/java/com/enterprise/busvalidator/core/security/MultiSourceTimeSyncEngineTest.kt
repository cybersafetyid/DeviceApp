package com.enterprise.busvalidator.core.security

import com.enterprise.busvalidator.core.model.TimeConfidenceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSourceTimeSyncEngineTest {

    @Test
    fun validateMonotonicVelocity_withoutAnchor_isUntrusted() {
        val engine = MultiSourceTimeSyncEngine(NoopLogger(), NoRootSuManager())

        val valid = engine.validateMonotonicVelocity()

        assertEquals(false, valid)
        assertEquals(TimeConfidenceState.TIME_UNTRUSTED, engine.timeConfidence.value)
    }

    @Test
    fun validateAndUpdateTime_withTrustedGpsTime_createsSecureAnchor() {
        val engine = MultiSourceTimeSyncEngine(NoopLogger(), NoRootSuManager())

        val accepted = engine.validateAndUpdateTime(
            trustedUtcMs = System.currentTimeMillis(),
            source = "GPS_NMEA"
        )

        assertTrue(accepted)
        assertEquals(TimeConfidenceState.SECURE_SYNCED, engine.timeConfidence.value)
    }

    @Test
    fun validateAndUpdateTime_rejectsTrustedSourceBehindPersistedCheckpoint() {
        val engine = MultiSourceTimeSyncEngine(NoopLogger(), NoRootSuManager())
        val now = System.currentTimeMillis()

        engine.validateAndUpdateTime(now, source = "GPS_NMEA")
        engine.updatePersistedCheckpoint(now + 60_000L)

        val accepted = engine.validateAndUpdateTime(now - 60_000L, source = "GPS_NMEA")

        assertEquals(false, accepted)
        assertEquals(TimeConfidenceState.TIME_UNTRUSTED, engine.timeConfidence.value)
    }

    @Test
    fun currentValidatedUtcMillis_neverMovesBehindPersistedCheckpoint() {
        val engine = MultiSourceTimeSyncEngine(NoopLogger(), NoRootSuManager())
        val now = System.currentTimeMillis()

        engine.validateAndUpdateTime(now, source = "GPS_NMEA")
        engine.updatePersistedCheckpoint(now + 30_000L)

        assertTrue(engine.currentValidatedUtcMillis() >= now + 30_000L)
    }

    private class NoopLogger : EncryptedLogger() {
        override fun log(tag: String, message: String, isError: Boolean) = Unit
    }

    private class NoRootSuManager : SuManager(NoopLogger()) {
        override fun isRootAvailable(): Boolean = false
    }
}
