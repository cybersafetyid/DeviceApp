package com.enterprise.busvalidator.core.sync

import com.enterprise.busvalidator.core.common.AppDispatchers
import com.enterprise.busvalidator.core.database.LocationLogDao
import com.enterprise.busvalidator.core.database.LocationLogEntity
import com.enterprise.busvalidator.core.location.BusLocationManager
import com.enterprise.busvalidator.core.model.BusLocationSnapshot
import com.enterprise.busvalidator.core.model.LocationTelemetryPayload
import com.enterprise.busvalidator.core.network.MqttTelemetryClient
import com.enterprise.busvalidator.core.security.EncryptedLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetrySyncManager @Inject constructor(
    private val locationManager: BusLocationManager,
    private val locationLogDao: LocationLogDao,
    private val mqttTelemetryClient: MqttTelemetryClient,
    private val deliveryService: TelemetryDeliveryService,
    private val logger: EncryptedLogger,
    private val dispatchers: AppDispatchers
) {
    private val started = AtomicBoolean(false)
    private val liveDeliveryQueue = Channel<Pair<Long, LocationTelemetryPayload>>(
        capacity = LIVE_DELIVERY_QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) {
            return
        }

        mqttTelemetryClient.start(scope)
        locationManager.startLocationTracking()

        scope.launch(dispatchers.io) {
            collectLiveLocations()
        }
        scope.launch(dispatchers.io) {
            processLiveDeliveryQueue()
        }
        scope.launch(dispatchers.io) {
            drainUndeliveredLocationLogs()
        }
        scope.launch(dispatchers.io) {
            pruneExpiredLocationLogs()
        }
    }

    private suspend fun collectLiveLocations() {
        locationManager.locationUpdates
            .distinctUntilChanged { old, new -> old.elapsedRealtimeNanos == new.elapsedRealtimeNanos }
            .collect { snapshot ->
                persistLocationForDelivery(snapshot)
            }
    }

    private suspend fun persistLocationForDelivery(snapshot: BusLocationSnapshot) = withContext(dispatchers.io) {
        val logId = locationLogDao.insertLocationLog(snapshot.toEntity())
        val payload = snapshot.toPayload(
            locationLogId = logId,
            pendingLocationLogCount = locationLogDao.getUndeliveredLocationLogCount(),
            deliveryAttempt = 1
        )
        liveDeliveryQueue.trySend(logId to payload)
    }

    private suspend fun processLiveDeliveryQueue() {
        for ((logId, payload) in liveDeliveryQueue) {
            deliverStoredPayload(logId, payload)
        }
    }

    private suspend fun drainUndeliveredLocationLogs() {
        while (kotlin.coroutines.coroutineContext.isActive) {
            try {
                val staleCutoffUtc = System.currentTimeMillis() - LIVE_DELIVERY_STALE_AFTER_MS
                val pendingLogs = locationLogDao.getUndeliveredLocationLogs(
                    limit = UNDELIVERED_DRAIN_LIMIT,
                    staleCutoffUtc = staleCutoffUtc
                )
                pendingLogs.forEach { log ->
                    val payload = log.toPayload(
                        pendingLocationLogCount = locationLogDao.getUndeliveredLocationLogCount(),
                        deliveryAttempt = log.deliveryAttemptCount + 1
                    )
                    deliverStoredPayload(log.id, payload)
                }
            } catch (e: Exception) {
                logger.log("TelemetrySync", "Undelivered drain failed: ${e.message}", isError = true)
            }

            delay(UNDELIVERED_DRAIN_INTERVAL_MS)
        }
    }

    private suspend fun pruneExpiredLocationLogs() {
        while (kotlin.coroutines.coroutineContext.isActive) {
            try {
                val cutoffUtc = System.currentTimeMillis() - LOCATION_LOG_RETENTION_MS
                val deleted = locationLogDao.pruneLocationLogsOlderThan(cutoffUtc)
                if (deleted > 0) {
                    logger.log("TelemetrySync", "Pruned $deleted location logs older than 7 days")
                }
            } catch (e: Exception) {
                logger.log("TelemetrySync", "Location log prune failed: ${e.message}", isError = true)
            }

            delay(LOCATION_LOG_PRUNE_INTERVAL_MS)
        }
    }

    private suspend fun deliverStoredPayload(logId: Long, payload: LocationTelemetryPayload) {
        val outcome = deliveryService.deliver(payload)
        if (outcome.isDelivered && outcome.transport != null) {
            locationLogDao.markLocationDelivered(
                id = logId,
                deliveredAtUtc = System.currentTimeMillis(),
                transport = outcome.transport.name
            )
            return
        }

        locationLogDao.markLocationDeliveryFailed(
            id = logId,
            error = outcome.errorMessage ?: "Unknown telemetry delivery failure"
        )
    }

    private fun BusLocationSnapshot.toEntity(): LocationLogEntity {
        return LocationLogEntity(
            deviceId = deviceId,
            recordedAtUtc = recordedAtUtc,
            provider = provider,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            accuracyMeters = accuracyMeters,
            verticalAccuracyMeters = verticalAccuracyMeters,
            bearingDegrees = bearingDegrees,
            bearingAccuracyDegrees = bearingAccuracyDegrees,
            speedMetersPerSecond = speedMetersPerSecond,
            speedAccuracyMetersPerSecond = speedAccuracyMetersPerSecond,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            satelliteCount = satelliteCount,
            isMock = isMock
        )
    }

    private fun BusLocationSnapshot.toPayload(
        locationLogId: Long,
        pendingLocationLogCount: Int,
        deliveryAttempt: Int
    ): LocationTelemetryPayload {
        return LocationTelemetryPayload(
            locationLogId = locationLogId,
            snapshot = this,
            pendingLocationLogCount = pendingLocationLogCount,
            deliveryAttempt = deliveryAttempt
        )
    }

    private fun LocationLogEntity.toPayload(
        pendingLocationLogCount: Int,
        deliveryAttempt: Int
    ): LocationTelemetryPayload {
        return LocationTelemetryPayload(
            locationLogId = id,
            snapshot = BusLocationSnapshot(
                deviceId = deviceId,
                recordedAtUtc = recordedAtUtc,
                provider = provider,
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters,
                accuracyMeters = accuracyMeters,
                verticalAccuracyMeters = verticalAccuracyMeters,
                bearingDegrees = bearingDegrees,
                bearingAccuracyDegrees = bearingAccuracyDegrees,
                speedMetersPerSecond = speedMetersPerSecond,
                speedAccuracyMetersPerSecond = speedAccuracyMetersPerSecond,
                elapsedRealtimeNanos = elapsedRealtimeNanos,
                satelliteCount = satelliteCount,
                isMock = isMock
            ),
            pendingLocationLogCount = pendingLocationLogCount,
            deliveryAttempt = deliveryAttempt
        )
    }

    private companion object {
        const val UNDELIVERED_DRAIN_LIMIT = 50
        const val UNDELIVERED_DRAIN_INTERVAL_MS = 30_000L
        const val LIVE_DELIVERY_QUEUE_CAPACITY = 512
        const val LIVE_DELIVERY_STALE_AFTER_MS = 60_000L
        const val LOCATION_LOG_PRUNE_INTERVAL_MS = 6 * 60 * 60 * 1_000L
        const val LOCATION_LOG_RETENTION_MS = 7 * 24 * 60 * 60 * 1_000L
    }
}
