package com.enterprise.busvalidator.core.security

import android.os.Build
import android.os.SystemClock
import com.enterprise.busvalidator.core.model.TimeConfidenceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

/**
 * Multi-source time validation engine for settlement-critical timestamps.
 *
 * Architecture:
 * 1. Trusted UTC sources: GPS NMEA, Android network clock on API 33+, and SNTP fallback.
 * 2. Persisted trust anchor: trusted UTC paired with elapsedRealtime so process restarts do not reset trust.
 * 3. Monotonic projection: offline same-boot time is derived from trustedUtc + elapsedRealtime delta.
 */
@Singleton
open class MultiSourceTimeSyncEngine @Inject constructor(
    private val logger: EncryptedLogger,
    private val suManager: SuManager,
    private val anchorStore: TimeAnchorStore
) {
    constructor(
        logger: EncryptedLogger,
        suManager: SuManager
    ) : this(logger, suManager, InMemoryTimeAnchorStore())

    private val _timeConfidence = kotlinx.coroutines.flow.MutableStateFlow(TimeConfidenceState.TIME_UNTRUSTED)
    val timeConfidence: kotlinx.coroutines.flow.StateFlow<TimeConfidenceState> = _timeConfidence.asStateFlow()

    private var validationJob: Job? = null
    private var lastAnchor: TimeAnchor? = anchorStore.readAnchor()
    private var lastNetworkRefreshElapsedMs: Long = 0L
    private var lastPersistedTimestampMs: Long = lastAnchor?.lastKnownGoodUtcMs ?: 0L

    init {
        validateMonotonicVelocity()
    }

    fun startContinuousValidation(scope: CoroutineScope) {
        if (validationJob?.isActive == true) {
            return
        }

        validationJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                validateMonotonicVelocity()
                refreshNetworkTimeIfDue()
                delay(VALIDATION_INTERVAL_MS)
            }
        }
    }

    /**
     * Called when a raw NMEA sentence ($GPRMC/$GNRMC or $GPZDA) is received from GNSS.
     */
    fun onGpsNmeaTimeReceived(rawNmea: String) {
        try {
            val parsedUtcMs = parseNmeaTimestamp(rawNmea) ?: return
            validateAndUpdateTime(parsedUtcMs, source = "GPS_NMEA", maxUncertaintyMs = GPS_MAX_UNCERTAINTY_MS)
        } catch (e: Exception) {
            logger.log("TimeEngine", "Error parsing NMEA time: ${e.message}", isError = true)
        }
    }

    @Synchronized
    fun validateMonotonicVelocity(): Boolean {
        val currentWallMs = System.currentTimeMillis()
        val currentElapsedMs = elapsedRealtimeMs()
        val anchor = lastAnchor

        if (anchor == null) {
            _timeConfidence.value = TimeConfidenceState.TIME_UNTRUSTED
            logger.log("TimeEngine", "No trusted time anchor available; time is untrusted", isError = true)
            return false
        }

        if (currentElapsedMs < anchor.elapsedRealtimeMs) {
            _timeConfidence.value = TimeConfidenceState.TIME_UNTRUSTED
            logger.log(
                "TimeEngine",
                "Reboot/offline boundary detected. elapsedRealtime reset from ${anchor.elapsedRealtimeMs} to $currentElapsedMs; trusted UTC must be reacquired.",
                isError = true
            )
            return false
        }

        val projectedUtcMs = anchor.trustedUtcMs + (currentElapsedMs - anchor.elapsedRealtimeMs)
        val driftMs = currentWallMs - projectedUtcMs
        val lowerBoundUtcMs = max(anchor.lastKnownGoodUtcMs, lastPersistedTimestampMs)

        if (currentWallMs + BACKWARD_TOLERANCE_MS < lowerBoundUtcMs) {
            _timeConfidence.value = TimeConfidenceState.TIME_UNTRUSTED
            logger.log(
                "TimeEngine",
                "Backward time tamper detected. lowerBound=$lowerBoundUtcMs, wall=$currentWallMs, projected=$projectedUtcMs",
                isError = true
            )
            correctSystemClockIfPossible(projectedUtcMs, "MONOTONIC_BACKWARD_GUARD")
            return false
        }

        if (abs(driftMs) > WALL_DRIFT_TOLERANCE_MS) {
            _timeConfidence.value = TimeConfidenceState.TIME_UNTRUSTED
            logger.log(
                "TimeEngine",
                "Wall clock drift anomaly detected. drift=${driftMs}ms, wall=$currentWallMs, projected=$projectedUtcMs",
                isError = true
            )
            correctSystemClockIfPossible(projectedUtcMs, "MONOTONIC_DRIFT_GUARD")
            return false
        }

        _timeConfidence.value = if (currentElapsedMs - anchor.elapsedRealtimeMs <= OFFLINE_MONOTONIC_VALIDITY_MS) {
            TimeConfidenceState.MONOTONIC_VALIDATED
        } else {
            TimeConfidenceState.TIME_UNTRUSTED
        }

        if (_timeConfidence.value == TimeConfidenceState.TIME_UNTRUSTED) {
            logger.log(
                "TimeEngine",
                "Trusted anchor is too old for offline monotonic validation; source=${anchor.source}",
                isError = true
            )
            return false
        }

        return true
    }

    @Synchronized
    fun validateAndUpdateTime(
        trustedUtcMs: Long,
        source: String,
        maxUncertaintyMs: Long = DEFAULT_MAX_UNCERTAINTY_MS
    ): Boolean {
        val currentWallMs = System.currentTimeMillis()
        val currentElapsedMs = elapsedRealtimeMs()
        val lowerBoundUtcMs = max(lastPersistedTimestampMs, lastAnchor?.lastKnownGoodUtcMs ?: 0L)

        if (trustedUtcMs < MIN_REASONABLE_UTC_MS) {
            logger.log("TimeEngine", "Rejected implausible $source time: $trustedUtcMs", isError = true)
            _timeConfidence.value = TimeConfidenceState.TIME_UNTRUSTED
            return false
        }

        if (trustedUtcMs + BACKWARD_TOLERANCE_MS < lowerBoundUtcMs) {
            logger.log(
                "TimeEngine",
                "Rejected $source time because it moves behind persisted checkpoint. trusted=$trustedUtcMs, lowerBound=$lowerBoundUtcMs",
                isError = true
            )
            _timeConfidence.value = TimeConfidenceState.TIME_UNTRUSTED
            return false
        }

        val skewMs = abs(currentWallMs - trustedUtcMs)
        logger.log(
            "TimeEngine",
            "Time sync from $source: trusted=$trustedUtcMs, wall=$currentWallMs, skew=${skewMs}ms, uncertainty<=${maxUncertaintyMs}ms"
        )

        if (skewMs > ROOT_CORRECTION_THRESHOLD_MS) {
            correctSystemClockIfPossible(trustedUtcMs, source)
        }

        val anchor = TimeAnchor(
            trustedUtcMs = trustedUtcMs,
            elapsedRealtimeMs = currentElapsedMs,
            lastKnownGoodUtcMs = max(trustedUtcMs, lowerBoundUtcMs),
            source = source,
            uncertaintyMs = maxUncertaintyMs
        )
        lastAnchor = anchor
        lastPersistedTimestampMs = anchor.lastKnownGoodUtcMs
        anchorStore.writeAnchor(anchor)
        _timeConfidence.value = TimeConfidenceState.SECURE_SYNCED
        return true
    }

    fun updatePersistedCheckpoint(timestampMs: Long) {
        synchronized(this) {
            val anchor = lastAnchor
            val updatedCheckpoint = max(timestampMs, lastPersistedTimestampMs)
            lastPersistedTimestampMs = updatedCheckpoint

            if (anchor != null && updatedCheckpoint > anchor.lastKnownGoodUtcMs) {
                val updatedAnchor = anchor.copy(lastKnownGoodUtcMs = updatedCheckpoint)
                lastAnchor = updatedAnchor
                anchorStore.writeAnchor(updatedAnchor)
            }
        }
    }

    @Synchronized
    fun currentValidatedUtcMillis(): Long {
        validateMonotonicVelocity()
        val anchor = lastAnchor ?: return max(System.currentTimeMillis(), lastPersistedTimestampMs)
        val elapsedDeltaMs = (elapsedRealtimeMs() - anchor.elapsedRealtimeMs).coerceAtLeast(0L)
        val projectedUtcMs = anchor.trustedUtcMs + elapsedDeltaMs
        return max(projectedUtcMs, lastPersistedTimestampMs)
    }

    private suspend fun refreshNetworkTimeIfDue() {
        val nowElapsedMs = elapsedRealtimeMs()
        val intervalMs = if (_timeConfidence.value == TimeConfidenceState.TIME_UNTRUSTED) {
            NETWORK_RETRY_INTERVAL_UNTRUSTED_MS
        } else {
            NETWORK_REFRESH_INTERVAL_MS
        }

        if (nowElapsedMs - lastNetworkRefreshElapsedMs < intervalMs) {
            return
        }
        lastNetworkRefreshElapsedMs = nowElapsedMs

        readAndroidNetworkClock()?.let { reading ->
            validateAndUpdateTime(reading.utcMs, reading.source, reading.uncertaintyMs)
            return
        }

        querySntp("time.android.com")?.let { reading ->
            validateAndUpdateTime(reading.utcMs, reading.source, reading.uncertaintyMs)
        }
    }

    private fun readAndroidNetworkClock(): TrustedTimeReading? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null
        }

        return try {
            val utcMs = SystemClock.currentNetworkTimeClock().millis()
            TrustedTimeReading(
                utcMs = utcMs,
                source = "ANDROID_NETWORK_CLOCK",
                uncertaintyMs = ANDROID_NETWORK_CLOCK_UNCERTAINTY_MS
            )
        } catch (e: Exception) {
            logger.log("TimeEngine", "Android network clock unavailable: ${e.message}", isError = true)
            null
        }
    }

    private suspend fun querySntp(host: String): TrustedTimeReading? = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = SNTP_TIMEOUT_MS.toInt()
                val address = InetAddress.getByName(host)
                val buffer = ByteArray(NTP_PACKET_SIZE)
                buffer[0] = (NTP_MODE_CLIENT or NTP_VERSION_4).toByte()

                val requestElapsedMs = elapsedRealtimeMs()
                writeNtpTimestamp(buffer, TRANSMIT_TIME_OFFSET, System.currentTimeMillis())

                val request = DatagramPacket(buffer, buffer.size, address, NTP_PORT)
                socket.send(request)

                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val responseElapsedMs = elapsedRealtimeMs()

                val serverTransmitUtcMs = readNtpTimestamp(buffer, TRANSMIT_TIME_OFFSET)
                if (serverTransmitUtcMs <= 0L) {
                    return@withContext null
                }

                val roundTripMs = (responseElapsedMs - requestElapsedMs).coerceAtLeast(0L)
                TrustedTimeReading(
                    utcMs = serverTransmitUtcMs + (roundTripMs / 2L),
                    source = "SNTP:$host",
                    uncertaintyMs = (roundTripMs / 2L).coerceAtLeast(1L)
                )
            }
        } catch (e: Exception) {
            logger.log("TimeEngine", "SNTP refresh failed: ${e.message}", isError = true)
            null
        }
    }

    private fun correctSystemClockIfPossible(utcMillis: Long, source: String) {
        if (!suManager.isRootAvailable()) {
            logger.log("TimeEngine", "Root unavailable; cannot correct system RTC using $source", isError = true)
            return
        }

        logger.log("TimeEngine", "Correcting system RTC via root using $source time")
        suManager.setSystemTime(utcMillis)
    }

    private fun parseNmeaTimestamp(nmea: String): Long? {
        val tokens = nmea.split(",")
        return try {
            when {
                (nmea.startsWith("\$GPRMC") || nmea.startsWith("\$GNRMC")) && tokens.size >= 10 && tokens[2] == "A" -> {
                    val timeStr = tokens[1]
                    val dateStr = tokens[9]
                    parseUtc(dateStr, timeStr)
                }
                nmea.startsWith("\$GPZDA") && tokens.size >= 5 -> {
                    val timeStr = tokens[1]
                    val day = tokens[2].padStart(2, '0')
                    val month = tokens[3].padStart(2, '0')
                    val year = tokens[4]
                    parseUtc("$day$month${year.takeLast(2)}", timeStr)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseUtc(dateDdMmYy: String, timeHhMmSs: String): Long? {
        val rawTime = timeHhMmSs.substringBefore(".").padEnd(6, '0')
        val formatter = SimpleDateFormat("ddMMyyHHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        return formatter.parse("$dateDdMmYy$rawTime")?.time
    }

    private fun elapsedRealtimeMs(): Long = try {
        SystemClock.elapsedRealtime()
    } catch (e: Throwable) {
        System.currentTimeMillis()
    }

    private fun readNtpTimestamp(buffer: ByteArray, offset: Int): Long {
        val seconds = readUnsignedInt(buffer, offset)
        val fraction = readUnsignedInt(buffer, offset + 4)
        if (seconds == 0L && fraction == 0L) {
            return 0L
        }
        return ((seconds - NTP_TO_UNIX_EPOCH_SECONDS) * 1000L) + ((fraction * 1000L) / 0x1_0000_0000L)
    }

    private fun writeNtpTimestamp(buffer: ByteArray, offset: Int, utcMillis: Long) {
        val seconds = (utcMillis / 1000L) + NTP_TO_UNIX_EPOCH_SECONDS
        val milliseconds = utcMillis % 1000L
        val fraction = (milliseconds * 0x1_0000_0000L) / 1000L
        writeUnsignedInt(buffer, offset, seconds)
        writeUnsignedInt(buffer, offset + 4, fraction)
    }

    private fun readUnsignedInt(buffer: ByteArray, offset: Int): Long {
        return ((buffer[offset].toLong() and 0xffL) shl 24) or
            ((buffer[offset + 1].toLong() and 0xffL) shl 16) or
            ((buffer[offset + 2].toLong() and 0xffL) shl 8) or
            (buffer[offset + 3].toLong() and 0xffL)
    }

    private fun writeUnsignedInt(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = (value shr 24).toByte()
        buffer[offset + 1] = (value shr 16).toByte()
        buffer[offset + 2] = (value shr 8).toByte()
        buffer[offset + 3] = value.toByte()
    }

    private data class TrustedTimeReading(
        val utcMs: Long,
        val source: String,
        val uncertaintyMs: Long
    )

    private companion object {
        val MIN_REASONABLE_UTC_MS: Long = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli()
        const val VALIDATION_INTERVAL_MS = 10_000L
        const val NETWORK_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1_000L
        const val NETWORK_RETRY_INTERVAL_UNTRUSTED_MS = 60_000L
        const val OFFLINE_MONOTONIC_VALIDITY_MS = 72 * 60 * 60 * 1_000L
        const val WALL_DRIFT_TOLERANCE_MS = 5_000L
        const val BACKWARD_TOLERANCE_MS = 1_000L
        const val ROOT_CORRECTION_THRESHOLD_MS = 3_000L
        const val DEFAULT_MAX_UNCERTAINTY_MS = 2_500L
        const val GPS_MAX_UNCERTAINTY_MS = 1_000L
        const val ANDROID_NETWORK_CLOCK_UNCERTAINTY_MS = 2_500L
        const val SNTP_TIMEOUT_MS = 5_000L
        const val NTP_PACKET_SIZE = 48
        const val NTP_PORT = 123
        const val TRANSMIT_TIME_OFFSET = 40
        const val NTP_TO_UNIX_EPOCH_SECONDS = 2_208_988_800L
        const val NTP_MODE_CLIENT = 3
        const val NTP_VERSION_4 = 0x20
    }
}
