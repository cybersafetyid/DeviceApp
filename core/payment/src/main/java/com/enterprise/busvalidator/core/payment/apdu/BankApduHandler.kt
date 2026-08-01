package com.enterprise.busvalidator.core.payment.apdu

import com.enterprise.busvalidator.core.model.*

/**
 * Base interface contract for bank APDU card communication protocols.
 * Implementations process card reading (readcardinfo), auto completion, Mandiri grace period, and debit (deduct).
 */
interface BankApduHandler {
    val bankIssuer: BankIssuer

    /**
     * Attempts to select application AID. Returns true if card matches this bank's AID.
     */
    fun selectApplication(transmitCardApdu: (ByteArray) -> ByteArray): Boolean

    /**
     * Reads card information (CAN, balance, last transaction, uncompleted journey state, grace period info).
     */
    fun readCardInfo(
        cardUid: String,
        transmitCardApdu: (ByteArray) -> ByteArray,
        transmitSamApdu: ((apdu: ByteArray) -> ByteArray)?
    ): BankCardInfo

    /**
     * Executes auto-completion APDU for uncompleted tap-in journey states.
     * Generates a valid autoCompletionTransCode for bank acquirer settlement.
     */
    fun processAutoCompletion(
        cardInfo: BankCardInfo,
        penaltyAmount: Long,
        transmitCardApdu: (ByteArray) -> ByteArray,
        transmitSamApdu: ((apdu: ByteArray) -> ByteArray)?
    ): AutoCompletionResult

    /**
     * Evaluates Mandiri e-Money grace period rules (free tap-out, discount, same-station window).
     */
    fun processMandiriGracePeriod(
        cardInfo: BankCardInfo,
        standardFare: Long,
        tapInTimestamp: Long,
        routeCode: String
    ): MandiriGracePeriodResult

    /**
     * Executes atomic APDU deduction pipeline with SAM mutual authentication and TransCode generation.
     */
    fun deduct(
        cardInfo: BankCardInfo,
        amountToDeduct: Long,
        transmitCardApdu: (ByteArray) -> ByteArray,
        transmitSamApdu: ((apdu: ByteArray) -> ByteArray)?
    ): ApduDeductResult
}
