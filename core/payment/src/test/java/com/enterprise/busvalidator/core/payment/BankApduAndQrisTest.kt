package com.enterprise.busvalidator.core.payment

import android.content.Context
import com.enterprise.busvalidator.core.database.TransactionDao
import com.enterprise.busvalidator.core.database.TransactionEntity
import com.enterprise.busvalidator.core.hardware.api.AudioDriver
import com.enterprise.busvalidator.core.hardware.api.LedDriver
import com.enterprise.busvalidator.core.hardware.api.SoundType
import com.enterprise.busvalidator.core.model.*
import com.enterprise.busvalidator.core.payment.apdu.*
import com.enterprise.busvalidator.core.payment.apdu.banks.*
import com.enterprise.busvalidator.core.payment.qris.QrisPaymentEngine
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.enterprise.busvalidator.core.security.MultiSourceTimeSyncEngine
import com.enterprise.busvalidator.core.security.SuManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class BankApduAndQrisTest {

    private lateinit var logger: EncryptedLogger
    private lateinit var mandiriApdu: MandiriEmoneyApdu
    private lateinit var bcaApdu: BcaFlazzApdu
    private lateinit var briApdu: BriBrizziApdu
    private lateinit var bniApdu: BniTapCashApdu
    private lateinit var dkiApdu: BankDkiJakCardApdu
    private lateinit var nobuApdu: BankNobuApdu
    private lateinit var kmtApdu: KmtFelicaApdu

    private lateinit var bankApduManager: BankApduManager
    private lateinit var qrisPaymentEngine: QrisPaymentEngine
    private lateinit var timeSyncEngine: MultiSourceTimeSyncEngine
    private lateinit var paymentEngine: PaymentEngine

    private val fakeInsertedTransactions = mutableListOf<TransactionEntity>()
    private var lastSoundPlayed: SoundType? = null
    private var isLedSuccessSet = false

    private val fakeTransactionDao = object : TransactionDao {
        override suspend fun insertTransaction(transaction: TransactionEntity) {
            fakeInsertedTransactions.add(transaction)
        }
        override suspend fun getUnsyncedTransactions(): List<TransactionEntity> = fakeInsertedTransactions
        override suspend fun markSynced(ids: List<String>) {}
        override fun getPendingSyncCountFlow(): Flow<Int> = flowOf(fakeInsertedTransactions.size)
        override fun getDailyTransactionCountFlow(startOfDayTimestamp: Long): Flow<Int> = flowOf(fakeInsertedTransactions.size)
        override suspend fun getLastTransaction(): TransactionEntity? = fakeInsertedTransactions.lastOrNull()
    }

    private val fakeLedDriver = object : LedDriver {
        override fun setLedSuccess() { isLedSuccessSet = true }
        override fun setLedFailed() {}
        override fun setLedProcessing() {}
        override fun turnOffLeds() {}
    }

    private val fakeAudioDriver = object : AudioDriver {
        override fun playSound(soundType: SoundType) {
            lastSoundPlayed = soundType
        }
    }

    private class TestLogger : EncryptedLogger() {
        override fun log(tag: String, message: String, isError: Boolean) {}
    }

    @Before
    fun setup() {
        logger = TestLogger()

        mandiriApdu = MandiriEmoneyApdu(logger)
        bcaApdu = BcaFlazzApdu(logger)
        briApdu = BriBrizziApdu(logger)
        bniApdu = BniTapCashApdu(logger)
        dkiApdu = BankDkiJakCardApdu(logger)
        nobuApdu = BankNobuApdu(logger)
        kmtApdu = KmtFelicaApdu(logger)

        bankApduManager = BankApduManager(
            logger, mandiriApdu, bcaApdu, briApdu, bniApdu, dkiApdu, nobuApdu, kmtApdu
        )
        qrisPaymentEngine = QrisPaymentEngine(logger)
        timeSyncEngine = MultiSourceTimeSyncEngine(logger, SuManager(logger))

        fakeInsertedTransactions.clear()
        lastSoundPlayed = null
        isLedSuccessSet = false

        paymentEngine = PaymentEngine(
            fakeTransactionDao,
            timeSyncEngine,
            logger,
            fakeLedDriver,
            fakeAudioDriver,
            bankApduManager,
            qrisPaymentEngine
        )
    }

    @Test
    fun testMandiriGracePeriodApplied_returnsZeroFare() {
        val cardInfo = BankCardInfo(
            cardUid = "04E21A88BC6180",
            bankIssuer = BankIssuer.MANDIRI_EMONEY,
            cardNumberFormatted = "6032-7810-1234-5678",
            balance = 50000L,
            uncompletedTxState = UncompletedTxState.OPEN_TAP_IN
        )

        val tapInTime = System.currentTimeMillis() - (5 * 60 * 1000L) // 5 minutes ago (within 15-min window)
        val graceResult = mandiriApdu.processMandiriGracePeriod(cardInfo, 4000L, tapInTime, "BK-01")

        assertTrue(graceResult.isGracePeriodActive)
        assertEquals(0L, graceResult.adjustedGraceFare)
        assertEquals(4000L, graceResult.graceDiscountAmount)
    }

    @Test
    fun testAutoCompletionGeneratesTransCode() {
        val cardInfo = BankCardInfo(
            cardUid = "04E21A88BC6180",
            bankIssuer = BankIssuer.MANDIRI_EMONEY,
            cardNumberFormatted = "6032-7810-1234-5678",
            balance = 50000L,
            uncompletedTxState = UncompletedTxState.OPEN_TAP_IN
        )

        val mockTransmit: (ByteArray) -> ByteArray = { byteArrayOf(0x90.toByte(), 0x00.toByte()) }

        val autoCompResult = mandiriApdu.processAutoCompletion(cardInfo, 3500L, mockTransmit, null)

        assertTrue(autoCompResult.wasApplied)
        assertTrue(autoCompResult.autoCompletionTransCode.startsWith("TC-MANDIRI-"))
        assertEquals(46500L, autoCompResult.balanceAfterCompletion)
        assertEquals(UncompletedTxState.CLOSED, autoCompResult.updatedTxState)
    }

    @Test
    fun testBankIssuerDetectionAndDeductForBcaFlazz() {
        val bcaSelectResponse: (ByteArray) -> ByteArray = { cmd ->
            val isBcaAid = cmd.size >= 12 && cmd[5] == 0xA0.toByte() && cmd[9] == 0x04.toByte() && cmd[10] == 0x10.toByte()
            if (isBcaAid) {
                byteArrayOf(0x90.toByte(), 0x00.toByte()) // Matching BCA AID
            } else if (cmd.size == 5 || cmd.size == 9) {
                byteArrayOf(0x90.toByte(), 0x00.toByte()) // APDU Read / Debit response
            } else {
                byteArrayOf(0x6A.toByte(), 0x82.toByte()) // Rejected (e.g. Mandiri, Brizzi AIDs)
            }
        }

        val detectedHandler = bankApduManager.detectBankHandler(bcaSelectResponse)
        assertNotNull(detectedHandler)
        assertEquals(BankIssuer.BCA_FLAZZ, detectedHandler?.bankIssuer)

        val cardInfo = detectedHandler!!.readCardInfo("FLAZZ-8899", bcaSelectResponse, null)
        assertEquals(BankIssuer.BCA_FLAZZ, cardInfo.bankIssuer)

        val deductResult = detectedHandler.deduct(cardInfo, 3500L, bcaSelectResponse, null)
        assertTrue(deductResult.isSuccess)
        assertTrue(deductResult.transCode.startsWith("TC-BCA-"))
        assertEquals(3500L, deductResult.amountDeducted)
    }

    @Test
    fun testQrisTapPayloadProcessingAndCrcValidation() {
        val dynamicQris = qrisPaymentEngine.generateDynamicQrisPayload(
            merchantName = "BISKITA BEKASI BUS 1049",
            amount = 4000L
        )

        assertTrue(dynamicQris.contains("BISKITA BEKASI BUS 1049"))

        val qrisData = qrisPaymentEngine.processQrisTapPayload(dynamicQris, fareAmount = 4000L)

        assertTrue(qrisData.crcVerified)
        assertTrue(qrisData.transCode.startsWith("RRN-QRIS-"))
        assertEquals(4000L, qrisData.amount)
    }

    @Test
    fun testProcessCardApduFlowEndToEnd() = runBlocking {
        val mockCardApdu: (ByteArray) -> ByteArray = { byteArrayOf(0x90.toByte(), 0x00.toByte()) }

        val record = paymentEngine.processCardApduFlow(
            cardUid = "04E21A88BC6180",
            passengerProfile = PassengerProfile.GENERAL,
            tapMode = TapMode.TAP_IN_OUT,
            transmitCardApdu = mockCardApdu
        )

        assertEquals(TransactionStatus.SUCCESS, record.status)
        assertTrue(record.transCode.isNotEmpty())
        assertTrue(record.transCode.startsWith("TC-"))
        assertEquals(1, fakeInsertedTransactions.size)
        assertTrue(isLedSuccessSet)
        assertEquals(SoundType.SUCCESS_BEEP, lastSoundPlayed)
    }

    @Test
    fun testProcessQrisTapFlowEndToEnd() = runBlocking {
        val dynamicQris = qrisPaymentEngine.generateDynamicQrisPayload(amount = 4000L)

        val record = paymentEngine.processQrisTapFlow(
            qrPayload = dynamicQris,
            passengerProfile = PassengerProfile.GENERAL
        )

        assertEquals(TransactionStatus.SUCCESS, record.status)
        assertEquals("QRIS_TAP", record.bankIssuer)
        assertTrue(record.transCode.startsWith("RRN-QRIS-"))
        assertEquals(1, fakeInsertedTransactions.size)
        assertTrue(isLedSuccessSet)
        assertEquals(SoundType.SUCCESS_BEEP, lastSoundPlayed)
    }

    @Test
    fun testKmtFelicaCardDetectionReadAndDeduct() {
        val felicaMockTransmit: (ByteArray) -> ByteArray = { cmd ->
            if (cmd.size >= 6 && cmd[2] == 0xFE.toByte() && cmd[3] == 0x00.toByte()) {
                // FeliCa Polling response (Length 18, Response Code 01, IDm 8-bytes, PMm 8-bytes)
                ByteArray(18).apply {
                    this[0] = 0x12.toByte()
                    this[1] = 0x01.toByte()
                }
            } else {
                byteArrayOf(0x0B.toByte(), 0x09.toByte()) // FeliCa Write Response OK
            }
        }

        val cardInfo = kmtApdu.readCardInfo("012E3F4A5B6C7D8E", felicaMockTransmit, null)
        assertEquals(BankIssuer.KMT_FELICA, cardInfo.bankIssuer)
        assertTrue(cardInfo.cardNumberFormatted.startsWith("KMT-"))

        val deductResult = kmtApdu.deduct(cardInfo, 4000L, felicaMockTransmit, null)
        assertTrue(deductResult.isSuccess)
        assertTrue(deductResult.transCode.startsWith("TC-KMT-"))
        assertEquals(4000L, deductResult.amountDeducted)
    }
}
