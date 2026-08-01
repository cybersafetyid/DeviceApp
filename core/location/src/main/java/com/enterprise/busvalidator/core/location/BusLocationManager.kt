package com.enterprise.busvalidator.core.location

import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.location.LocationManager as AndroidLocationManager
import com.enterprise.busvalidator.core.model.BusLocationSnapshot
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.enterprise.busvalidator.core.security.MultiSourceTimeSyncEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GMS / Non-GMS Location Provider & GPS NMEA Atomic Time Listener.
 */
@Singleton
class BusLocationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: EncryptedLogger,
    private val timeSyncEngine: MultiSourceTimeSyncEngine
) {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _currentLocationSnapshot = MutableStateFlow<BusLocationSnapshot?>(null)
    val currentLocationSnapshot: StateFlow<BusLocationSnapshot?> = _currentLocationSnapshot.asStateFlow()

    private val _locationUpdates = MutableSharedFlow<BusLocationSnapshot>(
        extraBufferCapacity = LOCATION_UPDATE_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val locationUpdates: SharedFlow<BusLocationSnapshot> = _locationUpdates.asSharedFlow()

    private val androidLocationManager: AndroidLocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
    }

    @Volatile private var isTracking = false
    @Volatile private var lastSatelliteCount: Int? = null

    private var locationListener: LocationListener? = null
    private var gnssStatusCallback: GnssStatus.Callback? = null
    private var nmeaMessageListener: OnNmeaMessageListener? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun startLocationTracking() {
        if (isTracking) {
            return
        }

        try {
            logger.log("LocationManager", "Starting Location & NMEA tracking...")

            val listener = LocationListener { location ->
                _currentLocation.value = location
                val snapshot = location.toSnapshot(lastSatelliteCount)
                _currentLocationSnapshot.value = snapshot
                _locationUpdates.tryEmit(snapshot)
                logger.log(
                    "LocationManager",
                    "Location update: provider=${snapshot.provider}, lat=${snapshot.latitude}, lon=${snapshot.longitude}, " +
                        "accuracy=${snapshot.accuracyMeters}, satellites=${snapshot.satelliteCount}"
                )
            }
            locationListener = listener

            val enabledProviders = listOf(
                AndroidLocationManager.GPS_PROVIDER,
                AndroidLocationManager.NETWORK_PROVIDER
            ).filter { provider -> androidLocationManager.isProviderEnabled(provider) }

            if (enabledProviders.isEmpty()) {
                logger.log("LocationManager", "No enabled location provider available", isError = true)
                return
            }

            enabledProviders.forEach { provider ->
                androidLocationManager.requestLocationUpdates(
                    provider,
                    LOCATION_INTERVAL_MS,
                    LOCATION_MIN_DISTANCE_METERS,
                    listener,
                    Looper.getMainLooper()
                )
            }

            registerGnssStatusCallback()
            registerNmeaListener()
            isTracking = true
        } catch (e: SecurityException) {
            logger.log("LocationManager", "Location permission missing: ${e.message}", isError = true)
        } catch (e: Exception) {
            logger.log("LocationManager", "Error starting location: ${e.message}", isError = true)
        }
    }

    private fun registerGnssStatusCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || gnssStatusCallback != null) {
            return
        }

        gnssStatusCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var usedInFix = 0
                for (index in 0 until status.satelliteCount) {
                    if (status.usedInFix(index)) {
                        usedInFix += 1
                    }
                }
                lastSatelliteCount = usedInFix
            }
        }

        androidLocationManager.registerGnssStatusCallback(gnssStatusCallback!!, mainHandler)
    }

    private fun registerNmeaListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || nmeaMessageListener != null) {
            return
        }

        nmeaMessageListener = OnNmeaMessageListener { message, _ ->
            if (message.startsWith("\$GPRMC") || message.startsWith("\$GNRMC") || message.startsWith("\$GPZDA")) {
                timeSyncEngine.onGpsNmeaTimeReceived(message)
            }
        }

        androidLocationManager.addNmeaListener(nmeaMessageListener!!, mainHandler)
    }

    @Suppress("DEPRECATION")
    private fun Location.toSnapshot(satelliteCount: Int?): BusLocationSnapshot {
        return BusLocationSnapshot(
            recordedAtUtc = time,
            provider = provider ?: "unknown",
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = if (hasAltitude()) altitude else null,
            accuracyMeters = if (hasAccuracy()) accuracy else null,
            verticalAccuracyMeters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasVerticalAccuracy()) {
                verticalAccuracyMeters
            } else {
                null
            },
            bearingDegrees = if (hasBearing()) bearing else null,
            bearingAccuracyDegrees = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasBearingAccuracy()) {
                bearingAccuracyDegrees
            } else {
                null
            },
            speedMetersPerSecond = if (hasSpeed()) speed else null,
            speedAccuracyMetersPerSecond = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasSpeedAccuracy()) {
                speedAccuracyMetersPerSecond
            } else {
                null
            },
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            satelliteCount = satelliteCount,
            isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider
        )
    }

    private companion object {
        const val LOCATION_INTERVAL_MS = 2_000L
        const val LOCATION_MIN_DISTANCE_METERS = 5f
        const val LOCATION_UPDATE_BUFFER_CAPACITY = 4_096
    }
}
