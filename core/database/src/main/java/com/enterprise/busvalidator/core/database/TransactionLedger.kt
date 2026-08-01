package com.enterprise.busvalidator.core.database

import androidx.room.withTransaction
import com.enterprise.busvalidator.core.model.TransactionRecord
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

interface TransactionLedgerWriter {
    suspend fun hasCounterSyncConflict(): Boolean

    suspend fun commitSuccessfulTransaction(
        unsignedRecord: TransactionRecord,
        sign: (TransactionRecord) -> String
    ): TransactionRecord
}

@Singleton
class RoomTransactionLedgerWriter @Inject constructor(
    private val database: ValidatorDatabase
) : TransactionLedgerWriter {
    override suspend fun hasCounterSyncConflict(): Boolean {
        return database.transactionCounterDao().getCounterState()?.syncConflictReason != null
    }

    override suspend fun commitSuccessfulTransaction(
        unsignedRecord: TransactionRecord,
        sign: (TransactionRecord) -> String
    ): TransactionRecord {
        require(unsignedRecord.status.name == "SUCCESS") {
            "Only successful transactions are allowed to allocate a ledger counter"
        }

        return database.withTransaction {
            val transactionDao = database.transactionDao()
            val counterDao = database.transactionCounterDao()
            val nowUtc = System.currentTimeMillis()
            val currentState = counterDao.getCounterState()
            val maxExistingCounter = transactionDao.getMaxSuccessfulTransactionCounter()
            val nextCounter = max(currentState?.lastSuccessCounter ?: 0, maxExistingCounter) + 1

            val recordWithCounter = unsignedRecord.copy(transactionCounter = nextCounter)
            val signedRecord = recordWithCounter.copy(recordSignature = sign(recordWithCounter))

            counterDao.insertAllocation(
                TransactionCounterAllocationEntity(
                    transactionCounter = nextCounter,
                    transactionId = signedRecord.transactionId,
                    allocatedAtUtc = nowUtc
                )
            )
            transactionDao.insertTransaction(signedRecord.toEntity())
            counterDao.upsertCounterState(
                DeviceCounterStateEntity(
                    lastSuccessCounter = nextCounter,
                    lastBackendAckCounter = currentState?.lastBackendAckCounter ?: 0,
                    syncConflictReason = currentState?.syncConflictReason,
                    updatedAtUtc = nowUtc
                )
            )
            signedRecord
        }
    }

    private fun TransactionRecord.toEntity(): TransactionEntity {
        return TransactionEntity(
            transactionId = transactionId,
            transCode = transCode,
            transactionCounter = transactionCounter,
            cardUid = cardUid,
            bankIssuer = bankIssuer,
            amountDeducted = amountDeducted,
            initialBalance = initialBalance,
            finalBalance = finalBalance,
            timestampUtc = timestampUtc,
            tapMode = tapMode.name,
            passengerProfile = passengerProfile.name,
            status = status.name,
            isSynced = false,
            recordSignature = recordSignature
        )
    }
}
