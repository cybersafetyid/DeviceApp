package com.enterprise.busvalidator.core.payment.apdu

import com.enterprise.busvalidator.core.hardware.api.SamDriver
import com.enterprise.busvalidator.core.model.*
import com.enterprise.busvalidator.core.payment.apdu.banks.*
import com.enterprise.busvalidator.core.security.EncryptedLogger
import javax.inject.Inject
import javax.inject.Singleton

data class CardApduPipelineResult(
    val cardInfo: BankCardInfo,
    val autoCompletionResult: AutoCompletionResult?,
    val mandiriGracePeriodResult: MandiriGracePeriodResult?,
    val deductResult: ApduDeductResult
)

/**
 * Central APDU Dispatcher for Indonesian Bank Electronic Money Cards.
 * Manages bank probing, card info extraction, auto completion, Mandiri grace period, and SAM-backed APDU deduction.
 */
@Singleton
class BankApduManager @Inject constructor(
    private val logger: EncryptedLogger,
    private val mandiriApdu: MandiriEmoneyApdu,
    private val bcaApdu: BcaFlazzApdu,
    private val briApdu: BriBrizziApdu,
    private val bniApdu: BniTapCashApdu,
    private val dkiApdu: BankDkiJakCardApdu,
    private val nobuApdu: BankNobuApdu,
    private val kmtApdu: KmtFelicaApdu
) {
    private val handlers: List<BankApduHandler> by lazy {
        listOf(mandiriApdu, bcaApdu, briApdu, bniApdu, dkiApdu, nobuApdu, kmtApdu)
    }

    /**
     * Probes card APDU to detect matching bank issuer.
     */
    fun detectBankHandler(transmitCardApdu: (ByteArray) -> ByteArray): BankApduHandler? {
        for (handler in handlers) {
            try {
                if (handler.selectApplication(transmitCardApdu)) {
                    logger.log("BankApduManager", "Detected Bank Issuer: ${handler.bankIssuer.displayName}")
                    return handler
                }
            } catch (e: Exception) {
                logger.log("BankApduManager", "AID probe check failed for ${handler.bankIssuer}: ${e.message}")
            }
        }
        logger.log("BankApduManager", "Card AID did not match any known bank issuer, falling back to Mandiri e-Money driver")
        return mandiriApdu // Fallback to Mandiri e-Money protocol
    }

    /**
     * Executes atomic APDU pipeline: readCardInfo -> auto completion -> Mandiri grace period -> deduct.
     */
    fun processFullCardApduPipeline(
        cardUid: String,
        targetFare: Long,
        tapInTimestamp: Long,
        routeCode: String,
        samDriver: SamDriver?,
        transmitCardApdu: (ByteArray) -> ByteArray
    ): CardApduPipelineResult {
        val samTransmitLambda: ((ByteArray) -> ByteArray)? = if (samDriver != null) {
            { apdu -> samDriver.transmitSamApdu(apdu, 0) }
        } else null

        val handler = detectBankHandler(transmitCardApdu) ?: mandiriApdu

        // Step 1: Read Card Info
        val cardInfo = handler.readCardInfo(cardUid, transmitCardApdu, samTransmitLambda)
        logger.log("BankApduManager", "Step 1 (readcardinfo) OK: Card=${cardInfo.cardNumberFormatted}, Issuer=${cardInfo.bankIssuer}, Bal=${cardInfo.balance}")

        // Step 2: Process Auto Completion if uncompleted tap-in exists
        var autoCompResult: AutoCompletionResult? = null
        var currentBalance = cardInfo.balance

        if (cardInfo.uncompletedTxState == UncompletedTxState.OPEN_TAP_IN) {
            val penaltyFare = 3500L
            autoCompResult = handler.processAutoCompletion(cardInfo, penaltyFare, transmitCardApdu, samTransmitLambda)
            if (autoCompResult.wasApplied) {
                currentBalance = autoCompResult.balanceAfterCompletion
                logger.log("BankApduManager", "Step 2 (auto completion) OK: Deducted Penalty=$penaltyFare, New Bal=$currentBalance, AutoCompTransCode=${autoCompResult.autoCompletionTransCode}")
            }
        }

        // Step 3: Evaluate Mandiri Grace Period (if Mandiri e-Money)
        var finalFareToDeduct = targetFare
        var graceResult: MandiriGracePeriodResult? = null

        if (cardInfo.bankIssuer == BankIssuer.MANDIRI_EMONEY) {
            graceResult = handler.processMandiriGracePeriod(cardInfo, targetFare, tapInTimestamp, routeCode)
            if (graceResult.isGracePeriodActive) {
                finalFareToDeduct = graceResult.adjustedGraceFare
                logger.log("BankApduManager", "Step 3 (grace period) OK: Mandiri Grace Period Active -> Fare adjusted from $targetFare to $finalFareToDeduct")
            }
        }

        // Step 4: Execute Atomic Card Deduct APDU
        val updatedCardInfo = cardInfo.copy(balance = currentBalance)
        val deductResult = handler.deduct(updatedCardInfo, finalFareToDeduct, transmitCardApdu, samTransmitLambda)
        logger.log("BankApduManager", "Step 4 (deduct) OK: Success=${deductResult.isSuccess}, TransCode=${deductResult.transCode}, Counter=${deductResult.transactionCounter}, FinalBal=${deductResult.finalBalance}")

        return CardApduPipelineResult(
            cardInfo = updatedCardInfo,
            autoCompletionResult = autoCompResult,
            mandiriGracePeriodResult = graceResult,
            deductResult = deductResult
        )
    }
}
