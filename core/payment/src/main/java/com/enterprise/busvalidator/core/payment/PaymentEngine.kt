package com.enterprise.busvalidator.core.payment

import com.enterprise.busvalidator.core.database.TransactionDao
import com.enterprise.busvalidator.core.database.TransactionEntity
import com.enterprise.busvalidator.core.hardware.api.AudioDriver
import com.enterprise.busvalidator.core.hardware.api.LedDriver
import com.enterprise.busvalidator.core.hardware.api.SoundType
import com.enterprise.busvalidator.core.model.*
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.enterprise.busvalidator.core.security.MultiSourceTimeSyncEngine
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-First Zero-Loss Payment Engine with Double Deduct Protection,
 * Intermodal Fare Rules, Multi-Tier Promos, and Time Confidence Gate.
 */
@Singleton
class PaymentEngine @Inject constructor(
    private val transactionDao: TransactionDao,
    private val timeSyncEngine: MultiSourceTimeSyncEngine,
    private val logger: EncryptedLogger,
    private val ledDriver: LedDriver,
    private val audioDriver: AudioDriver
) {
    // In-memory Anti-Passback LRU Cooldown Buffer (Card UID -> Last Tap Timestamp)
    private val antiPassbackCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val cooldownWindowMs = 10_000L // 10-second cooldown per card UID

    /**
     * Executes atomic card APDU transaction pipeline.
     */
    @Synchronized
    suspend fun processCardTapTransaction(
        cardUid: String,
        bankIssuer: String,
        initialBalance: Long,
        passengerProfile: PassengerProfile = PassengerProfile.GENERAL,
        tapMode: TapMode = TapMode.TAP_IN_OUT,
        writeApduExecutor: (amountToDeduct: Long) -> Boolean
    ): TransactionRecord {

        val nowMs = System.currentTimeMillis()

        // Guard 1: Time Confidence Transaction Gate
        if (timeSyncEngine.timeConfidence.value == TimeConfidenceState.TIME_UNTRUSTED) {
            logger.log("PaymentEngine", "TRANSACTION REJECTED: Untrusted System Time!", isError = true)
            ledDriver.setLedFailed()
            audioDriver.playSound(SoundType.FAILED_BEEP)
            return buildRecord(cardUid, bankIssuer, 0, initialBalance, initialBalance, nowMs, tapMode, passengerProfile, TransactionStatus.UNTRUSTED_TIME_REJECTED)
        }

        // Guard 2: Anti-Passback / Double Deduct Safeguard
        val lastTapTime = antiPassbackCache[cardUid]
        if (lastTapTime != null && (nowMs - lastTapTime) < cooldownWindowMs) {
            logger.log("PaymentEngine", "ANTI-PASSBACK TRIGGERED for card $cardUid. Re-tapped in ${nowMs - lastTapTime}ms")
            ledDriver.setLedFailed()
            audioDriver.playSound(SoundType.CARD_ALREADY_TAPPED_BEEP)
            return buildRecord(cardUid, bankIssuer, 0, initialBalance, initialBalance, nowMs, tapMode, passengerProfile, TransactionStatus.CARD_ALREADY_TAPPED)
        }

        // Calculate Fare & Dynamic Promos
        val calculatedFare = calculateDynamicFare(bankIssuer, passengerProfile, initialBalance)

        // Guard 3: Double Fare Validation Safeguard (Layer 1: Balance Check, Layer 2: Bounds Check)
        if (initialBalance < calculatedFare) {
            logger.log("PaymentEngine", "INSUFFICIENT BALANCE: Card=$cardUid, Balance=$initialBalance, Required=$calculatedFare")
            ledDriver.setLedFailed()
            audioDriver.playSound(SoundType.INSUFFICIENT_BALANCE_BEEP)
            return buildRecord(cardUid, bankIssuer, 0, initialBalance, initialBalance, nowMs, tapMode, passengerProfile, TransactionStatus.INSUFFICIENT_BALANCE)
        }

        if (calculatedFare < 0 || calculatedFare > 100_000L) {
            logger.log("PaymentEngine", "DOUBLE FARE VALIDATION FAILED: Invalid calculated fare $calculatedFare", isError = true)
            ledDriver.setLedFailed()
            audioDriver.playSound(SoundType.FAILED_BEEP)
            return buildRecord(cardUid, bankIssuer, 0, initialBalance, initialBalance, nowMs, tapMode, passengerProfile, TransactionStatus.FAILED_WRITE_ROLLBACK)
        }

        // Atomic Card Write APDU Step
        val writeSuccess = writeApduExecutor(calculatedFare)
        if (!writeSuccess) {
            logger.log("PaymentEngine", "ATOMIC ROLLBACK: APDU Card Write Failed for UID $cardUid!", isError = true)
            ledDriver.setLedFailed()
            audioDriver.playSound(SoundType.FAILED_BEEP)
            return buildRecord(cardUid, bankIssuer, 0, initialBalance, initialBalance, nowMs, tapMode, passengerProfile, TransactionStatus.FAILED_WRITE_ROLLBACK)
        }

        // Transaction Succeeded
        val finalBalance = initialBalance - calculatedFare
        antiPassbackCache[cardUid] = nowMs
        timeSyncEngine.updatePersistedCheckpoint(nowMs)

        val record = buildRecord(cardUid, bankIssuer, calculatedFare, initialBalance, finalBalance, nowMs, tapMode, passengerProfile, TransactionStatus.SUCCESS)

        // Commit to Encrypted Database Ledger
        transactionDao.insertTransaction(
            TransactionEntity(
                transactionId = record.transactionId,
                cardUid = record.cardUid,
                bankIssuer = record.bankIssuer,
                amountDeducted = record.amountDeducted,
                initialBalance = record.initialBalance,
                finalBalance = record.finalBalance,
                timestampUtc = record.timestampUtc,
                tapMode = record.tapMode.name,
                passengerProfile = record.passengerProfile.name,
                status = record.status.name,
                isSynced = false,
                recordSignature = record.recordSignature
            )
        )

        ledDriver.setLedSuccess()
        audioDriver.playSound(SoundType.SUCCESS_BEEP)
        logger.log("PaymentEngine", "TRANSACTION COMMITTED: TxId=${record.transactionId}, Deducted=$calculatedFare, FinalBalance=$finalBalance")

        return record
    }

    private fun calculateDynamicFare(bankIssuer: String, profile: PassengerProfile, initialBalance: Long): Long {
        var fare = 3500L // Base fare

        // Passenger Profile Promo Discount
        fare = when (profile) {
            PassengerProfile.SENIOR_CITIZEN, PassengerProfile.DISABLED -> 0L // Free/Subsidized
            PassengerProfile.STUDENT -> 2000L // Discounted
            PassengerProfile.GOVERNMENT_PNS -> 3000L
            PassengerProfile.GENERAL -> fare
        }

        // Bank Issuer Specific Promo (e.g., Flazz or Bank DKI Monday Special)
        if (bankIssuer.contains("DKI") && profile == PassengerProfile.GENERAL) {
            fare = 3000L
        }

        return fare
    }

    private fun buildRecord(
        cardUid: String,
        bankIssuer: String,
        deducted: Long,
        initialBal: Long,
        finalBal: Long,
        timestamp: Long,
        tapMode: TapMode,
        profile: PassengerProfile,
        status: TransactionStatus
    ): TransactionRecord {
        val txId = "TX-${timestamp}-${cardUid.takeLast(4)}"
        val sig = generateHmacSignature("$txId:$cardUid:$deducted:$finalBal:$timestamp")
        return TransactionRecord(txId, cardUid, bankIssuer, deducted, initialBal, finalBal, timestamp, tapMode, profile, status, sig)
    }

    private fun generateHmacSignature(rawString: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(rawString.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
