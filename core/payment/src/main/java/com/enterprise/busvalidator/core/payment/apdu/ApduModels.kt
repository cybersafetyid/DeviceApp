package com.enterprise.busvalidator.core.payment.apdu

import com.enterprise.busvalidator.core.model.BankIssuer
import java.security.MessageDigest
import java.util.Locale

object ApduConstants {
    const val SW_OK = 0x9000
    const val SW_OK_DEBIT = 0x9100
    const val SW_FILE_NOT_FOUND = 0x6A82
    const val SW_SECURITY_STATUS_NOT_SATISFIED = 0x6982

    // Standard Select Command Prefix
    val CMD_SELECT_APP_PREFIX = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte())
}

/**
 * Extension helpers for byte array manipulation in APDU protocols.
 */
fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

fun String.hexToBytes(): ByteArray {
    val clean = this.replace(" ", "").replace(":", "")
    check(clean.length % 2 == 0) { "Hex string length must be even" }
    return ByteArray(clean.length / 2) { i ->
        clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

fun ByteArray.toIntBigEndian(offset: Int = 0, length: Int = 4): Int {
    var value = 0
    for (i in 0 until length) {
        value = (value shl 8) or (this[offset + i].toInt() and 0xFF)
    }
    return value
}

fun ByteArray.toLongBigEndian(offset: Int = 0, length: Int = 4): Long {
    var value = 0L
    for (i in 0 until length) {
        value = (value shl 8) or (this[offset + i].toLong() and 0xFF)
    }
    return value
}

fun Long.toByteArrayBigEndian(size: Int = 4): ByteArray {
    val bytes = ByteArray(size)
    for (i in 0 until size) {
        bytes[size - 1 - i] = ((this shr (i * 8)) and 0xFF).toByte()
    }
    return bytes
}

fun Int.toByteArrayBigEndian(size: Int = 2): ByteArray {
    val bytes = ByteArray(size)
    for (i in 0 until size) {
        bytes[size - 1 - i] = ((this shr (i * 8)) and 0xFF).toByte()
    }
    return bytes
}

/**
 * Generator helper for official Bank Settlement TransCode / Certificate Code.
 */
object TransCodeGenerator {
    fun generateTransCode(
        bankIssuer: BankIssuer,
        cardUid: String,
        transactionCounter: Int,
        timestampMs: Long,
        amount: Long
    ): String {
        val raw = "${bankIssuer.code}:$cardUid:$transactionCounter:$timestampMs:$amount"
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        val hexHash = hash.toHexString().take(8).uppercase(Locale.ROOT)
        return "TC-${bankIssuer.code}-${transactionCounter.toString().padStart(5, '0')}-$hexHash"
    }
}
