package com.enterprise.busvalidator.core.payment

import com.enterprise.busvalidator.core.database.TransactionLedgerWriter
import com.enterprise.busvalidator.core.hardware.api.AudioDriver
import com.enterprise.busvalidator.core.hardware.api.LedDriver
import com.enterprise.busvalidator.core.hardware.api.SamDriver
import com.enterprise.busvalidator.core.hardware.api.SoundType
import com.enterprise.busvalidator.core.model.*
import com.enterprise.busvalidator.core.payment.apdu.BankApduManager
import com.enterprise.busvalidator.core.payment.qris.QrisPaymentEngine
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.enterprise.busvalidator.core.security.MultiSourceTimeSyncEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-First Zero-Loss Payment Engine integrating Bank APDU Handlers (Mandiri, BCA, BRI, BNI, DKI, Nobu),
 * Mandiri Grace Period evaluation, Auto Completion, QRIS Tap, and TransCode settlement ledger persistence.
 */
@Singleton
class PaymentEngine @Inject constructor(
    private val transactionLedgerWriter: TransactionLedgerWriter,
    private val timeSyncEngine: MultiSourceTimeSyncEngine,
    private val logger: EncryptedLogger,
    private val ledDriver: LedDriver,
    private val audioDriver: AudioDriver,
    private val bankApduManager: BankApduManager,
    private val qrisPaymentEngine: QrisPaymentEngine
) {
    // In-memory Anti-Passback LRU Cooldown Buffer (Card UID -> Last Tap Timestamp)
    private val antiPassbackCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val cooldownWindowMs = 10_000L // 10-second cooldown per card UID
    private val paymentMutex = Mutex()

    /**
     * Complete APDU Payment Flow for all 6 Banks (Mandiri, BCA, BRI, BNI, DKI, Nobu).
     * Pipeline: readcardinfo -> auto completion -> Mandiri grace period -> deduct -> commit TransCode.
     */
    suspend fun processCardApduFlow(
        cardUid: String,
        passengerProfile: PassengerProfile = PassengerProfile.GENERAL,
        tapMode: TapMode = TapMode.TAP_IN_OUT,
        fareRulePolicy: FareRulePolicy? = null,
        routeCode: String = "BK-01",
        terminalConfig: TerminalConfig? = null,
        samDriver: SamDriver? = null,
        transmitCardApdu: (ByteArray) -> ByteArray
    ): TransactionRecord = paymentMutex.withLock {
        timeSyncEngine.validateMonotonicVelocity()
        val nowMs = timeSyncEngine.currentValidatedUtcMillis()

        // Guard 1: Time Confidence Transaction Gate
        if (timeSyncEngine.timeConfidence.value == TimeConfidenceState.TIME_UNTRUSTED) {
            logger.log("PaymentEngine", "TRANSACTION REJECTED: Untrusted System Time!", isError = true)
            ledDriver.setLedFailed()
            audioDriver.playSound(SoundType.FAILED_BEEP)
            return@withLock buildRecord(cardUid, "UNKNOWN", "", 0, 0, 0, 0, nowMs, tapMode, passengerProfile, TransactionStatus.UNTRUSTED_TIME_REJECTED)
        }

        if (transactionLedgerWriter.hasCounterSyncConflict()) {
            logger.log("PaymentEngine", "TRANSACTION REJECTED: Counter sync conflict with backend", isError = true)
            ledDriver.setLedFailed()
            audioDriver.playSound(SoundType.FAILED_BEEP)
            return@withLock buildRecord(cardUid, "UNKNOWN", "", 0, 0, 0, 0, nowMs, tapMode, passengerProfile, TransactionStatus.COUNTER_SYNC_CONFLICT)
        }

        // Guard 2: Anti-Passback / Double Deduct Safeguard
        val lastTapTime = antiPassbackCache[cardUid]
        if (lastTapTime != null && (nowMs - lastTapTime) < cooldownWindowMs) {
            logger.log("PaymentEngine", "ANTI-PASSBACK TRIGGERED for card $cardUid. Re-tapped in ${nowMs - lastTapTime}ms")
            ledDriver.setLedFailed()
            audioDriver.playSound(SoundType.CARD_ALREADY_TAPPED_BEEP)
            return@withLock buildRecord(cardUid, "UNKNOWN", "", 0, 0, 0, 0, nowMs, tapMode, passengerProfile, TransactionStatus.CARD_ALREADY_TAPPED)
        }

        val targetFare = calculateDynamicFare("GENERIC", passengerProfile, fareRulePolicy)

        // Execute full APDU pipeline: readcardinfo -> auto completion -> Mandiri grace period -> deduct
        val apduPipelineResult = runCatching {
            bankApduManager.processFullCardApduPipeline(
                cardUid = cardUid,
                targetFare = targetFare,
                tapInTimestamp = nowMs - (5 * 60 * 1000L),
                routeCode = routeCode,
                terminalConfig = terminalConfig,
                samDriver = samDriver,
                transmitCardApdu = transmitCardApdu
            )
        }.getOrElse { error ->
            logger.log("PaymentEngine", "CARD READ PIPELINE FAILED: ${error.message}", isError = true)
            ledDriver.setLedFailed()
            audioDriver.playSound(SoundType.FAILED_BEEP)
            return@withLock buildRecord(
                cardUid,
                BankIssuer.UNKNOWN.name,
                "",
                0,
                0,
                0,
                0,
                nowMs,
                tapMode,
                passengerProfile,
                TransactionStatus.CARD_READ_ERROR
            )
        }

        val cardInfo = apduPipelineResult.cardInfo
        val deductResult = apduPipelineResult.deductResult

        // Guard 3: Balance check
        if (cardInfo.balance < targetFare && (apduPipelineResult.mandiriGracePeriodResult?.adjustedGraceFare ?: targetFare) > 0L) {
            logger.log("PaymentEngine", "INSUFFICIENT BALANCE: Card=$cardUid, Balance=${cardInfo.balance}, Required=$targetFare")
            ledDriver.setLedFailed()
            audioDriver.playSound(SoundType.INSUFFICIENT_BALANCE_BEEP)
            return@withLock buildRecord(cardUid, cardInfo.bankIssuer.name, cardInfo.lastTransCode, 0, 0, cardInfo.balance, cardInfo.balance, nowMs, tapMode, passengerProfile, TransactionStatus.INSUFFICIENT_BALANCE)
        }

        if (!deductResult.isSuccess) {
            logger.log("PaymentEngine", "ATOMIC ROLLBACK: Card APDU Debit Failed! Reason: ${deductResult.errorMessage}", isError = true)
            ledDriver.setLedFailed()
            audioDriver.playSound(SoundType.FAILED_BEEP)
            return@withLock buildRecord(cardUid, cardInfo.bankIssuer.name, "", 0, 0, cardInfo.balance, cardInfo.balance, nowMs, tapMode, passengerProfile, TransactionStatus.FAILED_WRITE_ROLLBACK)
        }

        // Success: Commit to memory & persistent Room DB
        antiPassbackCache[cardUid] = nowMs
        timeSyncEngine.updatePersistedCheckpoint(nowMs)

        val unsignedRecord = buildRecord(
            cardUid = cardUid,
            bankIssuer = cardInfo.bankIssuer.name,
            transCode = deductResult.transCode,
            txCounter = 0,
            deducted = deductResult.amountDeducted,
            initialBal = deductResult.initialBalance,
            finalBal = deductResult.finalBalance,
            timestamp = nowMs,
            tapMode = tapMode,
            profile = passengerProfile,
            status = TransactionStatus.SUCCESS
        )

        val record = commitSuccessfulTransaction(unsignedRecord)
        ledDriver.setLedSuccess()
        audioDriver.playSound(SoundType.SUCCESS_BEEP)
        logger.log("PaymentEngine", "CARD APDU TRANSACTION COMMITTED: TxId=${record.transactionId}, TransCode=${record.transCode}, Deducted=${record.amountDeducted}, FinalBal=${record.finalBalance}")

        return@withLock record
    }

    /**
     * QRIS Tap & Dynamic QRIS Payment Processing.
     */
    suspend fun processQrisTapFlow(
        qrPayload: String,
        passengerProfile: PassengerProfile = PassengerProfile.GENERAL,
        tapMode: TapMode = TapMode.TAP_IN_OUT,
        fareRulePolicy: FareRulePolicy? = null
    ): TransactionRecord = paymentMutex.withLock {
        timeSyncEngine.validateMonotonicVelocity()
        val nowMs = timeSyncEngine.currentValidatedUtcMillis()

        if (timeSyncEngine.timeConfidence.value == TimeConfidenceState.TIME_UNTRUSTED) {
            logger.log("PaymentEngine", "QRIS TRANSACTION REJECTED: Untrusted System Time!", isError = true)
            ledDriver.setLedFailed()
            audioDriver.playSound(SoundType.FAILED_BEEP)
            return@withLock buildRecord(
                cardUid = "QRIS-UNKNOWN",
                bankIssuer = BankIssuer.QRIS_TAP.name,
                transCode = "",
                txCounter = 0,
                deducted = 0,
                initialBal = 0,
                finalBal = 0,
                timestamp = nowMs,
                tapMode = tapMode,
                profile = passengerProfile,
                status = TransactionStatus.UNTRUSTED_TIME_REJECTED
            )
        }

        if (transactionLedgerWriter.hasCounterSyncConflict()) {
            logger.log("PaymentEngine", "QRIS TRANSACTION REJECTED: Counter sync conflict with backend", isError = true)
            ledDriver.setLedFailed()
            audioDriver.playSound(SoundType.FAILED_BEEP)
            return@withLock buildRecord(
                cardUid = "QRIS-UNKNOWN",
                bankIssuer = BankIssuer.QRIS_TAP.name,
                transCode = "",
                txCounter = 0,
                deducted = 0,
                initialBal = 0,
                finalBal = 0,
                timestamp = nowMs,
                tapMode = tapMode,
                profile = passengerProfile,
                status = TransactionStatus.COUNTER_SYNC_CONFLICT
            )
        }

        val fare = calculateDynamicFare("QRIS", passengerProfile, fareRulePolicy)

        val qrisData = qrisPaymentEngine.processQrisTapPayload(qrPayload, fareAmount = fare)

        val unsignedRecord = buildRecord(
            cardUid = "QRIS-${qrisData.terminalId}",
            bankIssuer = BankIssuer.QRIS_TAP.name,
            transCode = qrisData.transCode,
            txCounter = 0,
            deducted = qrisData.amount,
            initialBal = 0L,
            finalBal = 0L,
            timestamp = nowMs,
            tapMode = tapMode,
            profile = passengerProfile,
            status = TransactionStatus.SUCCESS
        )

        val record = commitSuccessfulTransaction(unsignedRecord)
        ledDriver.setLedSuccess()
        audioDriver.playSound(SoundType.SUCCESS_BEEP)
        logger.log("PaymentEngine", "QRIS TAP TRANSACTION COMMITTED: RRN TransCode=${record.transCode}, Amount=${record.amountDeducted}")

        return@withLock record
    }

    private suspend fun commitSuccessfulTransaction(unsignedRecord: TransactionRecord): TransactionRecord {
        return transactionLedgerWriter.commitSuccessfulTransaction(unsignedRecord) { record ->
            generateRecordSignature(record)
        }
    }

    private fun calculateDynamicFare(bankIssuer: String, profile: PassengerProfile, policy: FareRulePolicy?): Long {
        val baseFare = policy?.baseFare ?: 3500L
        return when (profile) {
            PassengerProfile.SENIOR_CITIZEN -> policy?.seniorCitizenFare ?: 0L
            PassengerProfile.DISABLED -> policy?.disabledFare ?: 0L
            PassengerProfile.STUDENT -> policy?.studentFare ?: 2000L
            PassengerProfile.GOVERNMENT_PNS -> policy?.pnsFare ?: 3000L
            PassengerProfile.GENERAL -> baseFare
        }
    }

    private fun buildRecord(
        cardUid: String,
        bankIssuer: String,
        transCode: String,
        txCounter: Int,
        deducted: Long,
        initialBal: Long,
        finalBal: Long,
        timestamp: Long,
        tapMode: TapMode,
        profile: PassengerProfile,
        status: TransactionStatus
    ): TransactionRecord {
        val txId = "TX-${timestamp}-${cardUid.takeLast(4)}"
        val sig = generateHmacSignature("$txId:$cardUid:$transCode:$txCounter:$deducted:$finalBal:$timestamp")
        return TransactionRecord(
            transactionId = txId,
            transCode = transCode,
            transactionCounter = txCounter,
            cardUid = cardUid,
            bankIssuer = bankIssuer,
            amountDeducted = deducted,
            initialBalance = initialBal,
            finalBalance = finalBal,
            timestampUtc = timestamp,
            tapMode = tapMode,
            passengerProfile = profile,
            status = status,
            recordSignature = sig
        )
    }

    private fun generateHmacSignature(rawString: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(rawString.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateRecordSignature(record: TransactionRecord): String {
        return generateHmacSignature(
            "${record.transactionId}:${record.cardUid}:${record.transCode}:${record.transactionCounter}:" +
                "${record.amountDeducted}:${record.finalBalance}:${record.timestampUtc}"
        )
    }
}
