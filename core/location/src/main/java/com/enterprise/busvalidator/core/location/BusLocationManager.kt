package com.enterprise.busvalidator.core.location

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.enterprise.busvalidator.core.security.MultiSourceTimeSyncEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val androidLocationManager: AndroidLocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
    }

    fun startLocationTracking() {
        try {
            logger.log("LocationManager", "Starting Location & NMEA tracking...")

            val listener = LocationListener { location ->
                _currentLocation.value = location
                logger.log("LocationManager", "Location update: Lat=${location.latitude}, Lon=${location.longitude}")
            }

            if (androidLocationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)) {
                androidLocationManager.requestLocationUpdates(
                    AndroidLocationManager.GPS_PROVIDER,
                    2000L,
                    5f,
                    listener
                )
            }
        } catch (e: SecurityException) {
            logger.log("LocationManager", "Location permission missing: ${e.message}", isError = true)
        } catch (e: Exception) {
            logger.log("LocationManager", "Error starting location: ${e.message}", isError = true)
        }
    }
}
