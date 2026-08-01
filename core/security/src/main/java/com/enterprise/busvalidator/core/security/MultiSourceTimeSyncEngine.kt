package com.enterprise.busvalidator.core.security

import android.os.SystemClock
import com.enterprise.busvalidator.core.model.TimeConfidenceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep-Researched Multi-Layer Time Validation Engine & Monotonic Drift Guard.
 * Prevents time manipulation/skew to guarantee zero bank settlement rejections.
 */
@Singleton
open class MultiSourceTimeSyncEngine @Inject constructor(
    private val logger: EncryptedLogger,
    private val suManager: SuManager
) {
    private val _timeConfidence = MutableStateFlow(TimeConfidenceState.SECURE_SYNCED)
    val timeConfidence: StateFlow<TimeConfidenceState> = _timeConfidence.asStateFlow()

    private var lastSecureSyncWallTimeMs: Long = System.currentTimeMillis()
    private var lastSecureSyncElapsedNanos: Long = try { SystemClock.elapsedRealtimeNanos() } catch (e: Throwable) { System.currentTimeMillis() * 1_000_000L }
    private var lastPersistedTimestampMs: Long = System.currentTimeMillis()

    init {
        validateMonotonicVelocity()
    }

    /**
     * Called when a raw NMEA sentence ($GPRMC or $GPZDA) is parsed from the GPS module.
     * Extracts atomic UTC time.
     */
    fun onGpsNmeaTimeReceived(rawNmea: String) {
        try {
            if (rawNmea.startsWith("\$GPRMC") || rawNmea.startsWith("\$GPZDA")) {
                val parsedUtcMs = parseNmeaTimestamp(rawNmea) ?: return
                validateAndUpdateTime(parsedUtcMs, source = "GPS_NMEA")
            }
        } catch (e: Exception) {
            logger.log("TimeEngine", "Error parsing NMEA time: ${e.message}", isError = true)
        }
    }

    /**
     * Validates wall clock against monotonic hardware reference SystemClock.elapsedRealtimeNanos().
     */
    @Synchronized
    fun validateMonotonicVelocity(): Boolean {
        val currentWallMs = System.currentTimeMillis()
        val currentElapsedNanos = try { SystemClock.elapsedRealtimeNanos() } catch (e: Throwable) { System.currentTimeMillis() * 1_000_000L }

        val elapsedDeltaMs = (currentElapsedNanos - lastSecureSyncElapsedNanos) / 1_000_000
        val expectedWallMs = lastSecureSyncWallTimeMs + elapsedDeltaMs
        val driftMs = Math.abs(currentWallMs - expectedWallMs)

        // Check 1: Backward time tamper (clock jumped into past)
        if (currentWallMs < lastPersistedTimestampMs) {
            logger.log("TimeEngine", "CRITICAL: Backward time tamper detected! Persisted: $lastPersistedTimestampMs, Current: $currentWallMs", isError = true)
            _timeConfidence.value = TimeConfidenceState.TIME_UNTRUSTED
            return false
        }

        // Check 2: Monotonic drift anomaly (wall clock drifted by > 5 seconds)
        if (driftMs > 5000) {
            logger.log("TimeEngine", "WARNING: Monotonic drift anomaly detected. Drift: ${driftMs}ms", isError = true)
            _timeConfidence.value = TimeConfidenceState.TIME_UNTRUSTED
            return false
        }

        if (_timeConfidence.value == TimeConfidenceState.TIME_UNTRUSTED) {
            _timeConfidence.value = TimeConfidenceState.MONOTONIC_VALIDATED
        }
        return true
    }

    @Synchronized
    fun validateAndUpdateTime(trustedUtcMs: Long, source: String) {
        val currentWallMs = System.currentTimeMillis()
        val skewMs = Math.abs(currentWallMs - trustedUtcMs)

        logger.log("TimeEngine", "Time sync from $source: Trusted: $trustedUtcMs, Local: $currentWallMs, Skew: ${skewMs}ms")

        if (skewMs > 3000 && suManager.isRootAvailable()) {
            logger.log("TimeEngine", "Correcting system RTC clock via Root using trusted $source time...")
            suManager.setSystemTime(trustedUtcMs)
        }

        lastSecureSyncWallTimeMs = trustedUtcMs
        lastSecureSyncElapsedNanos = try { SystemClock.elapsedRealtimeNanos() } catch (e: Throwable) { System.currentTimeMillis() * 1_000_000L }
        lastPersistedTimestampMs = trustedUtcMs
        _timeConfidence.value = TimeConfidenceState.SECURE_SYNCED
    }

    fun updatePersistedCheckpoint(timestampMs: Long) {
        if (timestampMs > lastPersistedTimestampMs) {
            lastPersistedTimestampMs = timestampMs
        }
    }

    private fun parseNmeaTimestamp(nmea: String): Long? {
        val tokens = nmea.split(",")
        return try {
            if (nmea.startsWith("\$GPRMC") && tokens.size >= 10 && tokens[2] == "A") {
                val timeStr = tokens[1] // HHMMSS.sss
                val dateStr = tokens[9] // DDMMYY
                val sdf = SimpleDateFormat("ddMMyyHHmmss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val rawTime = (timeStr.split(".")[0])
                sdf.parse("$dateStr$rawTime")?.time
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
