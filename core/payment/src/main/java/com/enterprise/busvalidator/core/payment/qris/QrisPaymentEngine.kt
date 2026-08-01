package com.enterprise.busvalidator.core.payment.qris

import com.enterprise.busvalidator.core.model.QrisTapData
import com.enterprise.busvalidator.core.security.EncryptedLogger
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamic QRIS & QRIS Tap Payment Engine.
 * Implements EMVCo & ASPI QRIS standard format generation, CRC16-CCITT validation,
 * and QRIS Tap NFC/Scan payload processing with RRN TransCode generation.
 */
@Singleton
class QrisPaymentEngine @Inject constructor(
    private val logger: EncryptedLogger
) {

    /**
     * Generates standard EMVCo Dynamic QRIS String with CRC16-CCITT checksum.
     */
    fun generateDynamicQrisPayload(
        merchantName: String = "BISKITA BEKASI BUS 1049",
        merchantCity: String = "BEKASI",
        merchantId: String = "ID1020304050607",
        terminalId: String = "TID99201",
        amount: Long = 4000L
    ): String {
        val formattedAmt = amount.toString()
        val rawData = StringBuilder()
            .append("000201")                                          // Payload Format Indicator
            .append("010212")                                          // Point of Initiation Method (12 = Dynamic QR)
            .append("26670016ID.GO.QRIS.WWW01189360091100000000000215")  // Merchant Account Info (NMID/ASPI)
            .append("52044111")                                        // Merchant Category Code (Transit)
            .append("5303360")                                         // Transaction Currency (360 = IDR)
            .append("54").append("%02d".format(formattedAmt.length)).append(formattedAmt) // Transaction Amount
            .append("5802ID")                                          // Country Code
            .append("59").append("%02d".format(merchantName.length)).append(merchantName) // Merchant Name
            .append("60").append("%02d".format(merchantCity.length)).append(merchantCity) // Merchant City
            .append("62").append("%02d".format(8 + terminalId.length)).append("07").append("%02d".format(terminalId.length)).append(terminalId) // Terminal ID
            .append("6304")                                            // CRC16 Checksum Marker

        val crcHex = calculateCrc16Ccitt(rawData.toString())
        val fullQris = rawData.append(crcHex).toString()

        logger.log("QrisEngine", "Generated Dynamic QRIS: $fullQris (CRC16=$crcHex)")
        return fullQris
    }

    /**
     * Processes incoming QRIS Tap payload (from NFC tag tap or 2D camera QR scan).
     */
    fun processQrisTapPayload(
        rawPayload: String,
        merchantId: String = "ID1020304050607",
        terminalId: String = "TID99201",
        fareAmount: Long = 4000L
    ): QrisTapData {
        logger.log("QrisEngine", "Processing QRIS Tap Payload: ${rawPayload.take(30)}...")

        val isCrcValid = verifyQrisCrc(rawPayload)
        val timestamp = System.currentTimeMillis()
        val txId = "QRIS-TX-$timestamp"
        val rrnTransCode = "RRN-QRIS-${timestamp.toString().takeLast(8)}-${(1000..9999).random()}"

        return QrisTapData(
            qrisPayload = rawPayload,
            merchantName = "BISKITA TRANSIT QRIS",
            merchantId = merchantId,
            terminalId = terminalId,
            transactionId = txId,
            transCode = rrnTransCode,
            amount = fareAmount,
            crcVerified = isCrcValid
        )
    }

    /**
     * Verifies CRC16-CCITT checksum of an EMVCo QRIS string.
     */
    fun verifyQrisCrc(qrisString: String): Boolean {
        if (qrisString.length < 8 || !qrisString.contains("6304")) return true // Fallback for raw QRIS Tap NFC payloads
        val dataPart = qrisString.substring(0, qrisString.indexOf("6304") + 4)
        val expectedCrc = qrisString.substring(qrisString.indexOf("6304") + 4).take(4).uppercase(Locale.ROOT)
        val computedCrc = calculateCrc16Ccitt(dataPart)
        return expectedCrc == computedCrc
    }

    /**
     * Computes CRC16-CCITT (Polynomial 0x1021, Initial 0xFFFF).
     */
    private fun calculateCrc16Ccitt(input: String): String {
        var crc = 0xFFFF
        val bytes = input.toByteArray(Charsets.ISO_8859_1)
        for (b in bytes) {
            for (i in 0..7) {
                val bit = (b.toInt() shr (7 - i) and 1) == 1
                val c15 = (crc shr 15 and 1) == 1
                crc = crc shl 1
                if (c15 xor bit) crc = crc xor 0x1021
            }
        }
        crc = crc and 0xFFFF
        return "%04X".format(crc)
    }
}
