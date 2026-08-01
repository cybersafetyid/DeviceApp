package com.enterprise.busvalidator.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val transactionId: String,
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

@Database(entities = [TransactionEntity::class], version = 1, exportSchema = false)
abstract class ValidatorDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
