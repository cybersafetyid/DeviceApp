package com.enterprise.busvalidator.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val transactionId: String,
    val transCode: String = "",
    val transactionCounter: Int = 0,
    val cardUid: String,
    val bankIssuer: String,
    val amountDeducted: Long,
    val initialBalance: Long,
    val finalBalance: Long,
    val timestampUtc: Long,
    val tapMode: String,
    val passengerProfile: String,
    val status: String,
    val isSynced: Boolean = false,
    val recordSignature: String
)

@Entity(
    tableName = "location_logs",
    indices = [
        Index(value = ["recordedAtUtc"]),
        Index(value = ["isDelivered", "recordedAtUtc"])
    ]
)
data class LocationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deviceId: String,
    val recordedAtUtc: Long,
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val verticalAccuracyMeters: Float?,
    val bearingDegrees: Float?,
    val bearingAccuracyDegrees: Float?,
    val speedMetersPerSecond: Float?,
    val speedAccuracyMetersPerSecond: Float?,
    val elapsedRealtimeNanos: Long,
    val satelliteCount: Int?,
    val isMock: Boolean,
    val isDelivered: Boolean = false,
    val deliveredAtUtc: Long? = null,
    val deliveryTransport: String? = null,
    val deliveryAttemptCount: Int = 0,
    val lastDeliveryError: String? = null
)

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE isSynced = 0 ORDER BY timestampUtc ASC")
    suspend fun getUnsyncedTransactions(): List<TransactionEntity>

    @Query("UPDATE transactions SET isSynced = 1 WHERE transactionId IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT COUNT(*) FROM transactions WHERE isSynced = 0")
    fun getPendingSyncCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE timestampUtc >= :startOfDayTimestamp")
    fun getDailyTransactionCountFlow(startOfDayTimestamp: Long): Flow<Int>

    @Query("SELECT * FROM transactions ORDER BY timestampUtc DESC LIMIT 1")
    suspend fun getLastTransaction(): TransactionEntity?
}

@Dao
interface LocationLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationLog(log: LocationLogEntity): Long

    @Query(
        """
        SELECT * FROM location_logs
        WHERE isDelivered = 0
          AND (deliveryAttemptCount > 0 OR recordedAtUtc <= :staleCutoffUtc)
        ORDER BY recordedAtUtc ASC
        LIMIT :limit
        """
    )
    suspend fun getUndeliveredLocationLogs(limit: Int, staleCutoffUtc: Long): List<LocationLogEntity>

    @Query("SELECT COUNT(*) FROM location_logs WHERE isDelivered = 0")
    suspend fun getUndeliveredLocationLogCount(): Int

    @Query(
        """
        UPDATE location_logs
        SET isDelivered = 1,
            deliveredAtUtc = :deliveredAtUtc,
            deliveryTransport = :transport,
            deliveryAttemptCount = deliveryAttemptCount + 1,
            lastDeliveryError = NULL
        WHERE id = :id
        """
    )
    suspend fun markLocationDelivered(id: Long, deliveredAtUtc: Long, transport: String)

    @Query(
        """
        UPDATE location_logs
        SET deliveryAttemptCount = deliveryAttemptCount + 1,
            lastDeliveryError = :error
        WHERE id = :id
        """
    )
    suspend fun markLocationDeliveryFailed(id: Long, error: String)

    @Query("DELETE FROM location_logs WHERE recordedAtUtc < :cutoffUtc")
    suspend fun pruneLocationLogsOlderThan(cutoffUtc: Long): Int
}

@Database(entities = [TransactionEntity::class, LocationLogEntity::class], version = 2, exportSchema = false)
abstract class ValidatorDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun locationLogDao(): LocationLogDao
}
