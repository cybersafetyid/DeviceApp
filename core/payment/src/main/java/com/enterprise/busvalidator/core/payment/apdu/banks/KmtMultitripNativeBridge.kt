package com.enterprise.busvalidator.core.payment.apdu.banks

import com.enterprise.busvalidator.core.payment.apdu.toHexString
import com.enterprise.busvalidator.core.security.EncryptedLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class NativeKmtCardInfo(
    val serialNumberHex: String,
    val balance: Long,
    val rawData: ByteArray
)

internal data class NativeKmtDeductInfo(
    val isSuccess: Boolean,
    val transCodeHex: String,
    val errorMessage: String? = null
)

internal class KmtMultitripNativeBridge(
    private val logger: EncryptedLogger
) {
    private val nativeClass: Class<*>? = runCatching {
        Class.forName(NATIVE_LIB_CLASS_NAME)
    }.getOrNull()

    private val nativeLib: Any? = nativeClass?.let { klass ->
        runCatching { klass.getDeclaredConstructor().newInstance() }.getOrNull()
    }

    private var initialized = false
    private val idm = ByteArray(KMT_IDM_BYTES)
    private val commandApdu = ByteArray(KMT_COMMAND_BUFFER_BYTES)
    private var responseApdu = ByteArray(0)
    private var responseLength = 0

    val isAvailable: Boolean get() = nativeLib != null

    fun poll(transmitCardApdu: (ByteArray) -> ByteArray): Boolean {
        if (!ensureInitialized()) return false
        return runCatching {
            callInt("GetPoolingCommand", commandApdu).requireNativeOk("GetPoolingCommand")
            val response = transmitCardApdu(commandWithFelicaLength(commandLength()))
            callInt("SendPoolingResult", response, response.size, idm).requireNativeOk("SendPoolingResult")
            logger.log("KMTNative", "KMT polling OK, IDm=${idm.toHexString()}")
            true
        }.onFailure { error ->
            logger.log("KMTNative", "KMT polling failed: ${error.message}", isError = true)
        }.getOrDefault(false)
    }

    fun readCardInfo(transmitCardApdu: (ByteArray) -> ByteArray): NativeKmtCardInfo? {
        if (!poll(transmitCardApdu)) return null
        return runCatching {
            callInt("GetInfoCommand", idm, commandApdu).requireNativeOk("GetInfoCommand")
            val response = transmitCardApdu(commandWithFelicaLength(commandLength()))
            val serialNumber = ByteArray(KMT_SERIAL_BYTES)
            val combined = byteArrayOf(KMT_INFO_RESPONSE_PREFIX) + idm + response
            callInt(
                "SendInfoCommandResult",
                combined,
                combined.size,
                serialNumber
            ).requireNativeOk("SendInfoCommandResult")

            NativeKmtCardInfo(
                serialNumberHex = serialNumber.toHexString(),
                balance = callLong("Balance"),
                rawData = serialNumber.copyOf()
            )
        }.onFailure { error ->
            logger.log("KMTNative", "KMT readCardInfo failed: ${error.message}", isError = true)
        }.getOrNull()
    }

    fun deduct(
        amount: Long,
        timestampMs: Long,
        transmitCardApdu: (ByteArray) -> ByteArray,
        transmitSamApdu: ((ByteArray) -> ByteArray)?
    ): NativeKmtDeductInfo? {
        if (!ensureInitialized()) return null
        return runCatching {
            val dateTime = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date(timestampMs))
            callInt("SetTransactionTime", dateTime).requireNativeOk("SetTransactionTime")
            runMutualAuthentication(transmitCardApdu, transmitSamApdu)

            call("SetSequence", 0.toShort())
            do {
                val sequence = callInt("Sequence").toShort()
                callInt(
                    "DeductFelica",
                    sequence,
                    commandApdu,
                    responseApdu,
                    responseLength,
                    amount.toInt()
                ).requireNativeOk("DeductFelica[$sequence]")

                responseApdu = exchangeByNativeStatus(
                    commandLength(),
                    transmitCardApdu,
                    transmitSamApdu
                )
                responseLength = responseApdu.size
            } while (callInt("Sequence") != KMT_DEDUCT_DONE_SEQUENCE)

            val transCode = ByteArray(KMT_TRANSCODE_BYTES)
            val deductInfoResult = callInt("GetDeductInfo", responseApdu, responseLength, transCode)
            NativeKmtDeductInfo(
                isSuccess = deductInfoResult == 0,
                transCodeHex = transCode.toHexString().trimEnd('0'),
                errorMessage = if (deductInfoResult == 0) null else "GetDeductInfo failed: $deductInfoResult"
            )
        }.onFailure { error ->
            logger.log("KMTNative", "KMT deduct failed: ${error.message}", isError = true)
        }.getOrNull()
    }

    private fun runMutualAuthentication(
        transmitCardApdu: (ByteArray) -> ByteArray,
        transmitSamApdu: ((ByteArray) -> ByteArray)?
    ) {
        call("SetBalanceAfter", 0)
        repeat(KMT_MUTUAL_AUTH_STEPS) { step ->
            callInt(
                "MutualWithFelica",
                step.toShort(),
                commandApdu,
                responseApdu,
                responseLength
            ).requireNativeOk("MutualWithFelica[$step]")

            responseApdu = exchangeByNativeStatus(
                commandLengthAdjustment = if (step == 1 || step == 3) -2 else 0,
                transmitCardApdu = transmitCardApdu,
                transmitSamApdu = transmitSamApdu
            )
            responseApdu = when (step) {
                1 -> byteArrayOf(KMT_MUTUAL_STEP_1_PREFIX) + idm + responseApdu
                3 -> byteArrayOf(KMT_MUTUAL_STEP_3_PREFIX) + idm + responseApdu
                else -> responseApdu
            }
            responseLength = responseApdu.size
        }
    }

    private fun exchangeByNativeStatus(
        commandLengthAdjustment: Int = 0,
        transmitCardApdu: (ByteArray) -> ByteArray,
        transmitSamApdu: ((ByteArray) -> ByteArray)?
    ): ByteArray {
        return when (val status = callInt("Status")) {
            KMT_STATUS_CARD -> transmitCardApdu(commandWithFelicaLength(commandLength() + commandLengthAdjustment))
            KMT_STATUS_SAM -> {
                val samTransmit = transmitSamApdu
                    ?: error("KMT native requested SAM transport but SAM driver is unavailable")
                samTransmit(commandWithoutFelicaLength(commandLength()))
            }
            else -> error("Unsupported KMT native transport status: $status")
        }
    }

    private fun ensureInitialized(): Boolean {
        if (initialized) return true
        if (nativeLib == null) return false
        return runCatching {
            call("Init")
            initialized = true
            true
        }.onFailure { error ->
            logger.log("KMTNative", "KMT native init failed: ${error.message}", isError = true)
        }.getOrDefault(false)
    }

    private fun commandWithFelicaLength(actualLength: Int): ByteArray {
        val payload = commandWithoutFelicaLength(actualLength)
        return byteArrayOf(payload.size.toByte()) + payload
    }

    private fun commandWithoutFelicaLength(actualLength: Int): ByteArray {
        val safeLength = actualLength.coerceIn(0, commandApdu.size)
        return commandApdu.copyOf(safeLength)
    }

    private fun commandLength(): Int = callInt("CommandLength")

    private fun callInt(methodName: String, vararg args: Any): Int {
        return call(methodName, *args).asNumber(methodName).toInt()
    }

    private fun callLong(methodName: String, vararg args: Any): Long {
        return call(methodName, *args).asNumber(methodName).toLong()
    }

    private fun call(methodName: String, vararg args: Any): Any? {
        val target = nativeLib ?: error("Native KMT library is unavailable")
        val method = target.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.size == args.size
        } ?: error("Native KMT method not found: $methodName/${args.size}")
        return method.invoke(target, *args)
    }

    private fun Any?.asNumber(methodName: String): Number {
        return this as? Number ?: error("Native KMT method $methodName did not return a number")
    }

    private fun Int.requireNativeOk(operation: String) {
        check(this == 0) { "$operation returned $this" }
    }

    private companion object {
        const val NATIVE_LIB_CLASS_NAME = "com.example.multitripandroid.NativeLib"
        const val KMT_COMMAND_BUFFER_BYTES = 512
        const val KMT_IDM_BYTES = 8
        const val KMT_SERIAL_BYTES = 8
        const val KMT_TRANSCODE_BYTES = 128
        const val KMT_INFO_RESPONSE_PREFIX = 0x07.toByte()
        const val KMT_MUTUAL_STEP_1_PREFIX = 0x41.toByte()
        const val KMT_MUTUAL_STEP_3_PREFIX = 0x43.toByte()
        const val KMT_STATUS_CARD = 0x01
        const val KMT_STATUS_SAM = 0x02
        const val KMT_MUTUAL_AUTH_STEPS = 6
        const val KMT_DEDUCT_DONE_SEQUENCE = 3
    }
}
