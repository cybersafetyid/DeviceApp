package com.enterprise.busvalidator.core.sync

import com.enterprise.busvalidator.core.database.DeviceCounterStateEntity
import com.enterprise.busvalidator.core.database.TransactionCounterAllocationEntity
import com.enterprise.busvalidator.core.database.TransactionCounterDao
import com.enterprise.busvalidator.core.database.TransactionDao
import com.enterprise.busvalidator.core.database.TransactionEntity
import com.enterprise.busvalidator.core.model.TransactionSyncItem
import com.enterprise.busvalidator.core.model.TransactionSyncResult
import com.enterprise.busvalidator.core.network.TransactionSyncApi
import com.enterprise.busvalidator.core.security.EncryptedLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncManagerCounterTest {

    @Test
    fun syncPendingTransactions_marksSyncedOnlyWhenBackendAckMatchesLocalLastCounter() = runTest {
        val transactionDao = FakeTransactionDao(
            mutableListOf(
                transaction("tx-1", counter = 1),
                transaction("tx-2", counter = 2)
            )
        )
        val counterDao = FakeTransactionCounterDao(lastSuccessCounter = 2)
        val api = FakeTransactionSyncApi(
            result = TransactionSyncResult(
                acceptedTransactionIds = setOf("tx-1", "tx-2"),
                backendLastCounter = 2
            )
        )
        val manager = SyncManager(transactionDao, counterDao, api, NoopLogger())

        val synced = manager.syncPendingTransactions()

        assertTrue(synced)
        assertTrue(transactionDao.transactions.all { it.isSynced })
        assertEquals(2, counterDao.state.lastBackendAckCounter)
        assertEquals(null, counterDao.state.syncConflictReason)
    }

    @Test
    fun syncPendingTransactions_flagsConflictWhenBackendLastCounterDiffers() = runTest {
        val transactionDao = FakeTransactionDao(
            mutableListOf(
                transaction("tx-1", counter = 1),
                transaction("tx-2", counter = 2)
            )
        )
        val counterDao = FakeTransactionCounterDao(lastSuccessCounter = 2)
        val api = FakeTransactionSyncApi(
            result = TransactionSyncResult(
                acceptedTransactionIds = setOf("tx-1", "tx-2"),
                backendLastCounter = 1
            )
        )
        val manager = SyncManager(transactionDao, counterDao, api, NoopLogger())

        val synced = manager.syncPendingTransactions()

        assertFalse(synced)
        assertTrue(transactionDao.transactions.none { it.isSynced })
        assertTrue(counterDao.state.syncConflictReason!!.contains("Backend counter ACK mismatch"))
    }

    @Test
    fun syncPendingTransactions_flagsConflictWhenBackendDoesNotAckEveryTransaction() = runTest {
        val transactionDao = FakeTransactionDao(
            mutableListOf(
                transaction("tx-1", counter = 1),
                transaction("tx-2", counter = 2)
            )
        )
        val counterDao = FakeTransactionCounterDao(lastSuccessCounter = 2)
        val api = FakeTransactionSyncApi(
            result = TransactionSyncResult(
                acceptedTransactionIds = setOf("tx-1"),
                backendLastCounter = 2
            )
        )
        val manager = SyncManager(transactionDao, counterDao, api, NoopLogger())

        val synced = manager.syncPendingTransactions()

        assertFalse(synced)
        assertTrue(transactionDao.transactions.none { it.isSynced })
        assertTrue(counterDao.state.syncConflictReason!!.contains("Backend counter ACK mismatch"))
    }

    private class FakeTransactionDao(
        val transactions: MutableList<TransactionEntity>
    ) : TransactionDao {
        override suspend fun insertTransaction(transaction: TransactionEntity) {
            transactions.add(transaction)
        }

        override suspend fun getUnsyncedTransactions(): List<TransactionEntity> {
            return transactions.filter { !it.isSynced && it.status == "SUCCESS" }.sortedBy { it.transactionCounter }
        }

        override suspend fun markSynced(ids: List<String>) {
            val idSet = ids.toSet()
            transactions.replaceAll { tx ->
                if (tx.transactionId in idSet) tx.copy(isSynced = true) else tx
            }
        }

        override fun getPendingSyncCountFlow(): Flow<Int> = flowOf(transactions.count { !it.isSynced })

        override fun getDailyTransactionCountFlow(startOfDayTimestamp: Long): Flow<Int> = flowOf(transactions.size)

        override suspend fun getLastTransaction(): TransactionEntity? = transactions.lastOrNull()

        override suspend fun getMaxSuccessfulTransactionCounter(): Int {
            return transactions.filter { it.status == "SUCCESS" }.maxOfOrNull { it.transactionCounter } ?: 0
        }
    }

    private class FakeTransactionCounterDao(
        lastSuccessCounter: Int
    ) : TransactionCounterDao {
        var state = DeviceCounterStateEntity(
            lastSuccessCounter = lastSuccessCounter,
            lastBackendAckCounter = 0,
            syncConflictReason = null,
            updatedAtUtc = 0L
        )

        override suspend fun insertAllocation(allocation: TransactionCounterAllocationEntity) = Unit

        override suspend fun upsertCounterState(state: DeviceCounterStateEntity) {
            this.state = state
        }

        override suspend fun getCounterState(counterId: String): DeviceCounterStateEntity = state

        override suspend fun markBackendCounterAcknowledged(
            backendLastCounter: Int,
            updatedAtUtc: Long,
            counterId: String
        ) {
            state = state.copy(
                lastBackendAckCounter = backendLastCounter,
                syncConflictReason = null,
                updatedAtUtc = updatedAtUtc
            )
        }

        override suspend fun markCounterConflict(reason: String, updatedAtUtc: Long, counterId: String) {
            state = state.copy(syncConflictReason = reason, updatedAtUtc = updatedAtUtc)
        }
    }

    private class FakeTransactionSyncApi(
        private val result: TransactionSyncResult
    ) : TransactionSyncApi {
        override suspend fun uploadTransactions(
            batchId: String,
            transactions: List<TransactionSyncItem>
        ): TransactionSyncResult = result
    }

    private class NoopLogger : EncryptedLogger() {
        override fun log(tag: String, message: String, isError: Boolean) = Unit
    }

    private companion object {
        fun transaction(id: String, counter: Int): TransactionEntity {
            return TransactionEntity(
                transactionId = id,
                transCode = "TC-$counter",
                transactionCounter = counter,
                cardUid = "CARD-$counter",
                bankIssuer = "MANDIRI",
                amountDeducted = 4000L,
                initialBalance = 10_000L,
                finalBalance = 6_000L,
                timestampUtc = 1_800_000_000_000L + counter,
                tapMode = "TAP_IN_OUT",
                passengerProfile = "GENERAL",
                status = "SUCCESS",
                isSynced = false,
                recordSignature = "sig-$counter"
            )
        }
    }
}
