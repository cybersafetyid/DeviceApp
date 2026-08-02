package com.enterprise.busvalidator.core.payment.apdu

import android.content.Context
import com.enterprise.busvalidator.core.model.*
import com.enterprise.busvalidator.core.security.EncryptedLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val NETLIBS_OK = 1000

sealed class LegacyNetlibsPipelineOutcome {
    data class Processed(val result: CardApduPipelineResult) : LegacyNetlibsPipelineOutcome()
    data object Fallback : LegacyNetlibsPipelineOutcome()
}

private data class LegacyNetlibsResponse(
    val code: Int,
    val message: String,
    val data: ByteArray,
    val optionalData: String
) {
    val isOk: Boolean get() = code == NETLIBS_OK
}

private data class LegacyTerminalBytes(
    val mid: ByteArray,
    val tid: ByteArray,
    val pinCode: ByteArray,
    val processingCode: String
)

private data class LegacyCardState(
    val cardNumber: String,
    val balance: Long,
    val balanceAfter: Long?,
    val transactionCounter: Int,
    val rawData: ByteArray
)

@Singleton
class LegacyNetlibsCardBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: EncryptedLogger
) {
    private val operationLock = Any()
    private val cardHelperClass by lazyClass(CARD_HELPER_CLASS)
    private val cardIssuerClass by lazyClass(CARD_ISSUER_CLASS)
    private val onConnectedClass by lazyClass(ON_CONNECTED_CLASS)
    private val listenerClass by lazyClass(LISTENER_CLASS)

    fun processFullCardPipeline(
        cardUid: String,
        targetFare: Long,
        routeCode: String,
        terminalConfig: TerminalConfig?,
        transmitCardApdu: (ByteArray) -> ByteArray,
        transmitSamApdu: ((ByteArray) -> ByteArray)?
    ): LegacyNetlibsPipelineOutcome = synchronized(operationLock) {
        runCatching {
            processLocked(
                cardUid = cardUid,
                targetFare = targetFare,
                routeCode = routeCode,
                terminalConfig = terminalConfig,
                transmitCardApdu = transmitCardApdu,
                transmitSamApdu = transmitSamApdu
            )
        }.getOrElse { error ->
            logger.log("LegacyNetlibsBridge", "NETLibs bridge unavailable: ${error.message}", isError = true)
            LegacyNetlibsPipelineOutcome.Fallback
        }
    }

    private fun processLocked(
        cardUid: String,
        targetFare: Long,
        routeCode: String,
        terminalConfig: TerminalConfig?,
        transmitCardApdu: (ByteArray) -> ByteArray,
        transmitSamApdu: ((ByteArray) -> ByteArray)?
    ): LegacyNetlibsPipelineOutcome {
        val cardHelperType = cardHelperClass ?: return LegacyNetlibsPipelineOutcome.Fallback
        val issuerType = cardIssuerClass ?: return LegacyNetlibsPipelineOutcome.Fallback
        val onConnectedType = onConnectedClass ?: return LegacyNetlibsPipelineOutcome.Fallback
        val listenerType = listenerClass ?: return LegacyNetlibsPipelineOutcome.Fallback

        val callback = createOnConnectedProxy(onConnectedType, transmitCardApdu, transmitSamApdu)
        val listener = Proxy.newProxyInstance(
            listenerType.classLoader,
            arrayOf(listenerType)
        ) { _, _, _ -> null }
        val noneIssuer = enumValue(issuerType, "NONE")
        val helper = cardHelperType
            .getConstructor(Context::class.java, issuerType, onConnectedType, listenerType)
            .newInstance(context.applicationContext, noneIssuer, callback, listener)

        val detectedIssuer = helper.invoke("checkIsMyCard", Boolean::class.javaPrimitiveType, false)
        val issuerName = (detectedIssuer as? Enum<*>)?.name ?: return LegacyNetlibsPipelineOutcome.Fallback
        val bankIssuer = issuerName.toBankIssuer() ?: return LegacyNetlibsPipelineOutcome.Fallback
        if (bankIssuer == BankIssuer.UNKNOWN) return LegacyNetlibsPipelineOutcome.Fallback

        logger.log("LegacyNetlibsBridge", "NETLibs detected issuer=$issuerName")
        helper.invoke("setCardIssuer", issuerType, detectedIssuer)
        val terminalBytes = terminalBytesFor(terminalConfig, bankIssuer)
        configureStaticModels(terminalBytes)

        val isNativeDesfire = false
        val readResponse = helper.invokeResponse(
            "getCardInfo",
            arrayOf(ByteArray::class.java, ByteArray::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType),
            terminalBytes.mid,
            terminalBytes.tid,
            true,
            isNativeDesfire
        )

        if (readResponse == null || !readResponse.isOk) {
            logger.log(
                "LegacyNetlibsBridge",
                "NETLibs readcardinfo did not complete for $bankIssuer: ${readResponse?.message ?: "no response"}"
            )
            return LegacyNetlibsPipelineOutcome.Fallback
        }

        val initialState = readCardState(bankIssuer, cardUid, readResponse.data)
        val deductResult = if (targetFare > 0L) {
            deductWithNetlibs(
                helper = helper,
                amount = targetFare,
                routeCode = routeCode,
                bankIssuer = bankIssuer,
                cardUid = cardUid,
                initialState = initialState,
                terminalBytes = terminalBytes,
                isNativeDesfire = isNativeDesfire
            )
        } else {
            val transCode = TransCodeGenerator.generateTransCode(bankIssuer, cardUid, initialState.transactionCounter, System.currentTimeMillis(), 0L)
            ApduDeductResult(
                isSuccess = true,
                transCode = transCode,
                transactionCounter = initialState.transactionCounter,
                amountDeducted = 0L,
                initialBalance = initialState.balance,
                finalBalance = initialState.balance,
                statusWordHex = "NETLIBS-0000"
            )
        }

        logger.log(
            "LegacyNetlibsBridge",
            "NETLibs deduct result issuer=$bankIssuer success=${deductResult.isSuccess} finalBalance=${deductResult.finalBalance}"
        )

        return LegacyNetlibsPipelineOutcome.Processed(
            CardApduPipelineResult(
                cardInfo = BankCardInfo(
                    cardUid = cardUid,
                    bankIssuer = bankIssuer,
                    cardNumberFormatted = initialState.cardNumber,
                    balance = initialState.balance,
                    uncompletedTxState = UncompletedTxState.CLOSED,
                    lastTransactionTimestamp = System.currentTimeMillis(),
                    lastTransCode = deductResult.transCode,
                    rawApplicationData = initialState.rawData
                ),
                autoCompletionResult = null,
                mandiriGracePeriodResult = null,
                deductResult = deductResult
            )
        )
    }

    private fun deductWithNetlibs(
        helper: Any,
        amount: Long,
        routeCode: String,
        bankIssuer: BankIssuer,
        cardUid: String,
        initialState: LegacyCardState,
        terminalBytes: LegacyTerminalBytes,
        isNativeDesfire: Boolean
    ): ApduDeductResult {
        val amountInt = amount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val response = helper.invokeResponse(
            "doDeduct",
            arrayOf(
                Int::class.javaPrimitiveType,
                ByteArray::class.java,
                ByteArray::class.java,
                Long::class.javaPrimitiveType,
                String::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ),
            amountInt,
            terminalBytes.mid,
            terminalBytes.tid,
            System.currentTimeMillis(),
            deductTimestamp(),
            false,
            isNativeDesfire
        )

        if (response == null) {
            return failedDeduct(initialState, "NETLibs deduct returned no response", "NETLIBS-NULL")
        }

        val finalState = readCardState(bankIssuer, cardUid, response.data)
        val transactionCounter = finalState.transactionCounter.takeIf { it > 0 } ?: initialState.transactionCounter
        val generatedTransCode = TransCodeGenerator.generateTransCode(
            bankIssuer = bankIssuer,
            cardUid = cardUid,
            transactionCounter = transactionCounter,
            timestampMs = System.currentTimeMillis(),
            amount = amount
        )

        if (!response.isOk) {
            return failedDeduct(
                initialState = initialState,
                reason = response.message.ifBlank { "NETLibs deduct rejected card" },
                statusWord = "NETLIBS-${response.code}"
            )
        }

        val finalBalance = finalState.balanceAfter ?: (initialState.balance - amount).coerceAtLeast(0L)
        return ApduDeductResult(
            isSuccess = true,
            transCode = response.optionalData.ifBlank { generatedTransCode },
            transactionCounter = transactionCounter,
            amountDeducted = amount,
            initialBalance = initialState.balance,
            finalBalance = finalBalance,
            statusWordHex = "NETLIBS-${response.code}",
            samAuthSignature = "NETLIBS-${terminalBytes.processingCode.ifBlank { routeCode }}"
        )
    }

    private fun failedDeduct(
        initialState: LegacyCardState,
        reason: String,
        statusWord: String
    ): ApduDeductResult {
        return ApduDeductResult(
            isSuccess = false,
            transCode = "",
            transactionCounter = initialState.transactionCounter,
            amountDeducted = 0L,
            initialBalance = initialState.balance,
            finalBalance = initialState.balance,
            statusWordHex = statusWord,
            errorMessage = reason
        )
    }

    private fun createOnConnectedProxy(
        onConnectedType: Class<*>,
        transmitCardApdu: (ByteArray) -> ByteArray,
        transmitSamApdu: ((ByteArray) -> ByteArray)?
    ): Any {
        return Proxy.newProxyInstance(
            onConnectedType.classLoader,
            arrayOf(onConnectedType)
        ) { _, method, args ->
            if (method.name == "Send" && args != null && args.size == 3) {
                val channel = (args[0] as? Enum<*>)?.name.orEmpty()
                val command = args[1] as? ByteArray ?: byteArrayOf()
                val callback = args[2]
                val response = runCatching {
                    when (channel) {
                        "SAM" -> transmitSamApdu?.invoke(command) ?: NETLIBS_TRANSPORT_ERROR
                        "CARD", "CARDDF", "CARDF" -> transmitCardApdu(command)
                        else -> NETLIBS_TRANSPORT_ERROR
                    }
                }.getOrDefault(NETLIBS_TRANSPORT_ERROR)
                callback?.javaClass?.methods
                    ?.firstOrNull { it.name == "onResponse" && it.parameterTypes.contentEquals(arrayOf(ByteArray::class.java)) }
                    ?.invoke(callback, response)
            }
            null
        }
    }

    private fun configureStaticModels(terminalBytes: LegacyTerminalBytes) {
        setStaticField("com.net2software.mobile.netlibs.core.chipbase.card.MANDIRI.EmoneyModel", "MID", terminalBytes.mid)
        setStaticField("com.net2software.mobile.netlibs.core.chipbase.card.MANDIRI.EmoneyModel", "TID", terminalBytes.tid)
        setStaticField("com.net2software.mobile.netlibs.core.chipbase.card.MANDIRI.EmoneyModel", "PINCODE", terminalBytes.pinCode)
        setStaticField("com.net2software.mobile.netlibs.core.chipbase.card.BRI.BrizziModel", "MID", terminalBytes.mid)
        setStaticField("com.net2software.mobile.netlibs.core.chipbase.card.BRI.BrizziModel", "TID", terminalBytes.tid)
    }

    private fun readCardState(bankIssuer: BankIssuer, cardUid: String, rawData: ByteArray): LegacyCardState {
        val descriptor = when (bankIssuer) {
            BankIssuer.MANDIRI_EMONEY -> ModelDescriptor(
                "com.net2software.mobile.netlibs.core.chipbase.card.MANDIRI.EmoneyModel",
                "cardSerialNumber",
                "currentBalanceInInteger",
                "afterBalanceInInteger",
                "currentBatchNumber"
            )
            BankIssuer.BRI_BRIZZI -> ModelDescriptor(
                "com.net2software.mobile.netlibs.core.chipbase.card.BRI.BrizziModel",
                "cardSerialNumber",
                "currentBalanceInInteger",
                "afterBalanceInInteger",
                "currentBatchNumber"
            )
            BankIssuer.BNI_TAPCASH -> ModelDescriptor(
                "com.net2software.mobile.netlibs.core.chipbase.card.BNI.TapCashModel",
                "serialNumber",
                "purseBalance",
                "purseBalanceAfter",
                "trxCounter"
            )
            BankIssuer.BCA_FLAZZ -> ModelDescriptor(
                "com.net2software.mobile.netlibs.core.chipbase.card.BCA.FlazzModel",
                "serialNumber",
                "purseBalance",
                "purseBalanceAfter",
                "trxCounter"
            )
            BankIssuer.BANK_DKI_JAKCARD -> ModelDescriptor(
                "com.net2software.mobile.netlibs.core.chipbase.card.DKI.JakCardModel",
                "serialNumber",
                "purseBalance",
                "purseBalanceAfter",
                "trxCounter"
            )
            BankIssuer.NOBU_EMONEY -> ModelDescriptor(
                "com.net2software.mobile.netlibs.core.chipbase.card.NOBU.NobuEmoneyModel",
                "cardSerialNumber",
                "purseBalance",
                "purseAfterBalance",
                "currentTrxCounter"
            )
            BankIssuer.KMT_FELICA -> {
                val serial = staticString("com.net2software.mobile.netlibs.core.chipbase.card.KMT.KmtModel", "serialNumber")
                val balance = staticInt("com.net2software.mobile.netlibs.core.chipbase.card.KMT.KmtModel", "balance") ?: 0
                val balanceAfter = staticInt("com.net2software.mobile.netlibs.core.chipbase.card.KMT.KmtModel", "balanceAfter")
                return LegacyCardState(
                    cardNumber = serial?.takeIf { it.isNotBlank() } ?: cardUid,
                    balance = balance.toLong(),
                    balanceAfter = balanceAfter?.toLong()?.takeIf { it >= 0L },
                    transactionCounter = 0,
                    rawData = rawData
                )
            }
            else -> null
        } ?: return LegacyCardState(cardUid, 0L, null, 0, rawData)

        val serialBytes = staticByteArray(descriptor.className, descriptor.serialField)
        val cardNumber = serialBytes?.toHexString()?.chunked(4)?.joinToString("-") ?: cardUid
        val balance = staticInt(descriptor.className, descriptor.balanceField)?.toLong() ?: 0L
        val balanceAfter = staticInt(descriptor.className, descriptor.balanceAfterField)?.toLong()?.takeIf { it >= 0L }
        val counterValue = staticField(descriptor.className, descriptor.counterField)
        return LegacyCardState(
            cardNumber = cardNumber,
            balance = balance,
            balanceAfter = balanceAfter,
            transactionCounter = counterValue.toCounterInt(),
            rawData = rawData
        )
    }

    private fun terminalBytesFor(config: TerminalConfig?, bankIssuer: BankIssuer): LegacyTerminalBytes {
        val issuerConfig = config?.issuerConfigFor(bankIssuer.code)
        val merchantId = issuerConfig?.merchantId?.takeIf { it.isNotBlank() } ?: config?.merchantId.orEmpty()
        val terminalId = issuerConfig?.terminalId?.takeIf { it.isNotBlank() } ?: config?.terminalId.orEmpty()
        val pinCode = issuerConfig?.pinCode?.takeIf { it.isNotBlank() } ?: config?.pinCode.orEmpty()
        val processingCode = issuerConfig?.processingCode?.takeIf { it.isNotBlank() } ?: config?.processingCode.orEmpty()
        return LegacyTerminalBytes(
            mid = merchantId.toLegacyBytes(8),
            tid = terminalId.toLegacyBytes(4),
            pinCode = pinCode.toLegacyBytes(8),
            processingCode = processingCode
        )
    }

    private fun Any.invoke(name: String, vararg parameterAndArgs: Any?): Any? {
        val parameterTypes = parameterAndArgs.filterIndexed { index, _ -> index % 2 == 0 }.map { it as Class<*> }.toTypedArray()
        val args = parameterAndArgs.filterIndexed { index, _ -> index % 2 == 1 }.toTypedArray()
        return javaClass.getMethod(name, *parameterTypes).invoke(this, *args)
    }

    private fun Any.invokeResponse(name: String, parameterTypes: Array<Class<*>?>, vararg args: Any?): LegacyNetlibsResponse? {
        val method = javaClass.getMethod(name, *parameterTypes.filterNotNull().toTypedArray())
        return method.invoke(this, *args)?.toLegacyResponse()
    }

    private fun Any.toLegacyResponse(): LegacyNetlibsResponse {
        val type = javaClass
        return LegacyNetlibsResponse(
            code = type.noArgMethod("getCode")?.invoke(this) as? Int ?: -1,
            message = type.noArgMethod("getMessage")?.invoke(this) as? String ?: "",
            data = type.noArgMethod("getData")?.invoke(this) as? ByteArray ?: byteArrayOf(),
            optionalData = type.noArgMethod("getOptionalData")?.invoke(this) as? String ?: ""
        )
    }

    private fun Class<*>.noArgMethod(name: String): Method? {
        return runCatching { getMethod(name) }.getOrNull()
    }

    private fun String.toBankIssuer(): BankIssuer? = when (this) {
        "EMONEY" -> BankIssuer.MANDIRI_EMONEY
        "FLAZZ" -> BankIssuer.BCA_FLAZZ
        "BRIZZI" -> BankIssuer.BRI_BRIZZI
        "TAPCASH" -> BankIssuer.BNI_TAPCASH
        "JAKCARD" -> BankIssuer.BANK_DKI_JAKCARD
        "NOBU_EMONEY" -> BankIssuer.NOBU_EMONEY
        "KMT" -> BankIssuer.KMT_FELICA
        "NONE", "ANDROIDJSA" -> BankIssuer.UNKNOWN
        else -> null
    }

    private fun enumValue(type: Class<*>, name: String): Any? {
        @Suppress("UNCHECKED_CAST")
        return java.lang.Enum.valueOf(type as Class<out Enum<*>>, name)
    }

    private fun staticField(className: String, fieldName: String): Any? {
        return runCatching {
            val type = Class.forName(className)
            type.getDeclaredField(fieldName).apply { isAccessible = true }.get(null)
        }.getOrNull()
    }

    private fun setStaticField(className: String, fieldName: String, value: Any) {
        runCatching {
            val type = Class.forName(className)
            type.getDeclaredField(fieldName).apply { isAccessible = true }.set(null, value)
        }
    }

    private fun staticByteArray(className: String, fieldName: String): ByteArray? {
        return staticField(className, fieldName) as? ByteArray
    }

    private fun staticInt(className: String, fieldName: String): Int? {
        return staticField(className, fieldName) as? Int
    }

    private fun staticString(className: String, fieldName: String): String? {
        return staticField(className, fieldName) as? String
    }

    private fun Any?.toCounterInt(): Int {
        return when (this) {
            is Int -> this
            is ByteArray -> fold(0) { acc, byte -> ((acc shl 8) or (byte.toInt() and 0xFF)) }.coerceAtLeast(0)
            else -> 0
        }
    }

    private fun String.toLegacyBytes(size: Int): ByteArray {
        val clean = trim().replace(" ", "").replace(":", "")
        val source = if (clean.isNotEmpty() && clean.length % 2 == 0 && clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            runCatching { clean.hexToBytes() }.getOrElse { toByteArray(Charsets.US_ASCII) }
        } else {
            toByteArray(Charsets.US_ASCII)
        }
        return source.copyOf(size)
    }

    private fun deductTimestamp(): String {
        return SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
    }

    private fun lazyClass(className: String) = lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching { Class.forName(className) }.getOrNull()
    }

    private data class ModelDescriptor(
        val className: String,
        val serialField: String,
        val balanceField: String,
        val balanceAfterField: String,
        val counterField: String
    )

    private companion object {
        const val CARD_HELPER_CLASS = "com.net2software.mobile.netlibs.core.chipbase.CardHelper"
        const val CARD_ISSUER_CLASS = "com.net2software.mobile.netlibs.core.chipbase.CardIssuer"
        const val ON_CONNECTED_CLASS = "com.net2software.mobile.netlibs.core.chipbase.OnConnected"
        const val LISTENER_CLASS = "com.net2software.mobile.netlibs.core.chipbase.Listener"
        val NETLIBS_TRANSPORT_ERROR = byteArrayOf(0x6F.toByte(), 0x00.toByte())
    }
}
