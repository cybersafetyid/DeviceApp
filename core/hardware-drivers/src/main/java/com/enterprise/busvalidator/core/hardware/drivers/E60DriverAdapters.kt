package com.enterprise.busvalidator.core.hardware.drivers

import android.content.Context
import com.enterprise.busvalidator.core.hardware.api.*
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.lenz.e60qsdk.Beeper
import com.lenz.e60qsdk.ICCard
import com.lenz.e60qsdk.Led
import com.lenz.e60qsdk.RfCardDriver
import com.lenz.e60qsdk.RfCardInfo
import com.lenz.e60qsdk.sdkJni
import com.example.sdkdemo.LibBarCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class E60QDriverAdapter @Inject constructor(
    private val logger: EncryptedLogger
) : NfcDriver, SamDriver, LedDriver, AudioDriver, ScannerDriver, MifareClassicDriver {

    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentMifareUid: ByteArray = ByteArray(0)

    override fun startCardListening(onCardDetected: (cardUid: String, apduHandler: (ByteArray) -> ByteArray) -> Unit) {
        startCardSessionListening { session ->
            onCardDetected(session.uid, session.transmitCardApdu)
        }
    }

    override fun startCardSessionListening(onCardDetected: (DetectedCardSession) -> Unit) {
        logger.log("E60Q_HAL", "Initializing E60Q Lenz Contactless RF Card Reader...")
        val initResult = sdkJni.getInstance().sdkInit(null, null)
        val openResult = RfCardDriver.getInstance().open()
        
        if (initResult != 0 || openResult != 0) {
            logger.log("E60Q_HAL", "E60Q Native Driver invocation failed. init=$initResult, open=$openResult", isError = true)
            return
        }
        
        logger.log("E60Q_HAL", "E60Q RfCardDriver opened successfully.")

        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    val cardInfo = RfCardDriver.getInstance().searchCard(500)
                    if (cardInfo != null && cardInfo.searchResult == RfCardInfo.NFC_ERR_NONE) {
                        val uidBytes = cardInfo.rfUid ?: ByteArray(0)
                        val uidString = uidBytes.joinToString("") { "%02X".format(it) }
                        currentMifareUid = uidBytes
                        
                        logger.log("E60Q_HAL", "Card Detected! UID: $uidString, Type: ${cardInfo.rfCardTypeName}")

                        onCardDetected(cardInfo.toDetectedCardSession("E60Q_HAL", logger))
                        
                        // Wait a bit before polling again to avoid spamming same card
                        delay(1000)
                    }
                } catch (e: Exception) {
                    logger.log("E60Q_HAL", "Error during card polling: ${e.message}", isError = true)
                }
                delay(100)
            }
        }
    }

    override fun stopCardListening() {
        logger.log("E60Q_HAL", "E60Q RfCardDriver stopped")
        pollingJob?.cancel()
        RfCardDriver.getInstance().close()
    }

    override fun isHardwareAvailable(): Boolean = true

    override fun powerOnSamSlot(slotIndex: Int): Boolean {
        logger.log("E60Q_HAL", "Powering ON E60Q SAM Slot $slotIndex via sdkJni...")
        // reset parameters according to standard ISO7816
        val rlen = IntArray(1)
        val rbuf = ByteArray(256)
        val ret = sdkJni.getInstance().iccardReset(slotIndex, 0x00.toByte(), 0x00.toByte(), rlen, rbuf)
        return if (ret == 0) {
            logger.log("E60Q_HAL", "E60Q SAM Slot $slotIndex powered on. ATR Length: ${rlen[0]}")
            true
        } else {
            logger.log("E60Q_HAL", "E60Q SAM Slot $slotIndex power on failed with code: $ret", isError = true)
            false
        }
    }

    override fun transmitSamApdu(apduCommand: ByteArray, slotIndex: Int): ByteArray {
        logger.log("E60Q_HAL", "Transmitting APDU to E60Q SAM Slot $slotIndex (${apduCommand.size} bytes)")
        val rlen = IntArray(1)
        val rbuf = ByteArray(256)
        val ret = sdkJni.getInstance().iccardApdu(slotIndex, apduCommand.size, apduCommand, rlen, rbuf)
        
        return if (ret == 0 && rlen[0] > 0) {
            rbuf.copyOf(rlen[0])
        } else {
            logger.log("E60Q_HAL", "SAM APDU failed. Code: $ret", isError = true)
            byteArrayOf(0x6F.toByte(), 0x00.toByte()) // Default error APDU
        }
    }

    override fun powerOffSamSlot(slotIndex: Int) {
        logger.log("E60Q_HAL", "Powering OFF E60Q SAM Slot $slotIndex")
        sdkJni.getInstance().iccardPowerDown(slotIndex)
    }

    override fun setLedSuccess() { 
        logger.log("E60Q_HAL", "E60Q LED -> GREEN SUCCESS")
        Led().set(Led.LED_GREEN, Led.LED_ON)
        Led().set(Led.LED_RED, Led.LED_OFF)
    }
    
    override fun setLedFailed() { 
        logger.log("E60Q_HAL", "E60Q LED -> RED FAILED")
        Led().set(Led.LED_GREEN, Led.LED_OFF)
        Led().set(Led.LED_RED, Led.LED_ON)
    }
    
    override fun setLedProcessing() { 
        logger.log("E60Q_HAL", "E60Q LED -> BLUE PROCESSING")
        Led().set(Led.LED_ONLINE, Led.LED_ON)
    }
    
    override fun turnOffLeds() { 
        logger.log("E60Q_HAL", "E60Q LED -> OFF")
        val led = Led()
        led.set(Led.LED_GREEN, Led.LED_OFF)
        led.set(Led.LED_RED, Led.LED_OFF)
        led.set(Led.LED_ONLINE, Led.LED_OFF)
    }

    override fun playSound(soundType: SoundType) {
        logger.log("E60Q_HAL", "E60Q Beeper Sound -> $soundType")
        when (soundType) {
            SoundType.SUCCESS_BEEP -> Beeper.getInstance().beep(100)
            SoundType.FAILED_BEEP, SoundType.INSUFFICIENT_BALANCE_BEEP, SoundType.CARD_ALREADY_TAPPED_BEEP -> {
                Beeper.getInstance().beep(300)
            }
        }
    }

    override fun startQrScan(onQrScanned: (qrContent: String) -> Unit) {
        logger.log("E60Q_HAL", "Initializing E60Q LibBarCode Barcode Scanner...")
        try {
            val qrSdk = LibBarCode.getInstance()
            qrSdk.init_barcode()
            qrSdk.barCodeRead(object : LibBarCode.OnBarCodeProgressListener {
                override fun onProgressChange(barcodeBytes: ByteArray?, len: Int): Int {
                    if (barcodeBytes != null) {
                        onQrScanned(String(barcodeBytes, 0, len))
                    }
                    return 0
                }
            })
        } catch (e: Throwable) {
            logger.log("E60Q_HAL", "LibBarCode load fallback: ${e.message}", isError = true)
        }
    }

    override fun stopQrScan() {
        logger.log("E60Q_HAL", "E60Q QR Scanner stopped")
        try {
            LibBarCode.getInstance().close_barcode()
        } catch (e: Throwable) {
            logger.log("E60Q_HAL", "Failed to close scanner: ${e.message}", isError = true)
        }
    }

    override fun connectMifare(): Boolean {
        return reconnectMifare("E60Q_HAL", logger) { currentMifareUid = it }
    }

    override fun authenticateMifareBlock(blockIndex: Int, keyType: MifareKeyType, key: ByteArray): Boolean {
        return authenticateMifareBlock("E60Q_HAL", logger, currentMifareUid, blockIndex, keyType, key)
    }

    override fun readMifareBlock(blockIndex: Int): ByteArray? {
        return readMifareBlock("E60Q_HAL", logger, blockIndex)
    }

    override fun writeMifareBlock(blockIndex: Int, data: ByteArray): Boolean {
        return writeMifareBlock("E60Q_HAL", logger, blockIndex, data)
    }
}

@Singleton
class E60V2DriverAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: EncryptedLogger
) : NfcDriver, SamDriver, LedDriver, AudioDriver, ScannerDriver, MifareClassicDriver {

    private val cameraScannerEngine by lazy {
        E60V2CameraScannerEngine(context, logger)
    }

    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentMifareUid: ByteArray = ByteArray(0)

    override fun startCardListening(onCardDetected: (cardUid: String, apduHandler: (ByteArray) -> ByteArray) -> Unit) {
        startCardSessionListening { session ->
            onCardDetected(session.uid, session.transmitCardApdu)
        }
    }

    override fun startCardSessionListening(onCardDetected: (DetectedCardSession) -> Unit) {
        logger.log("E60V2_HAL", "Initializing E60V2 RfCardDriver...")
        val initResult = sdkJni.getInstance().sdkInit(null, null)
        val openResult = RfCardDriver.getInstance().open()
        
        if (initResult != 0 || openResult != 0) {
            logger.log("E60V2_HAL", "E60V2 Native Driver invocation failed. init=$initResult, open=$openResult", isError = true)
            return
        }

        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    val cardInfo = RfCardDriver.getInstance().searchCard(500)
                    if (cardInfo != null && cardInfo.searchResult == RfCardInfo.NFC_ERR_NONE) {
                        val uidBytes = cardInfo.rfUid ?: ByteArray(0)
                        val uidString = uidBytes.joinToString("") { "%02X".format(it) }
                        currentMifareUid = uidBytes
                        
                        logger.log("E60V2_HAL", "Card Detected! UID: $uidString, Type: ${cardInfo.rfCardTypeName}")

                        onCardDetected(cardInfo.toDetectedCardSession("E60V2_HAL", logger))
                        
                        delay(1000)
                    }
                } catch (e: Exception) {
                    logger.log("E60V2_HAL", "Error during card polling: ${e.message}", isError = true)
                }
                delay(100)
            }
        }
    }

    override fun stopCardListening() {
        logger.log("E60V2_HAL", "E60V2 Card Listening Stopped")
        pollingJob?.cancel()
        RfCardDriver.getInstance().close()
    }

    override fun isHardwareAvailable(): Boolean = true

    override fun powerOnSamSlot(slotIndex: Int): Boolean {
        val rlen = IntArray(1)
        val rbuf = ByteArray(256)
        val ret = sdkJni.getInstance().iccardReset(slotIndex, 0x00.toByte(), 0x00.toByte(), rlen, rbuf)
        return ret == 0
    }

    override fun transmitSamApdu(apduCommand: ByteArray, slotIndex: Int): ByteArray {
        val rlen = IntArray(1)
        val rbuf = ByteArray(256)
        val ret = sdkJni.getInstance().iccardApdu(slotIndex, apduCommand.size, apduCommand, rlen, rbuf)
        
        return if (ret == 0 && rlen[0] > 0) {
            rbuf.copyOf(rlen[0])
        } else {
            byteArrayOf(0x6F.toByte(), 0x00.toByte())
        }
    }

    override fun powerOffSamSlot(slotIndex: Int) {
        sdkJni.getInstance().iccardPowerDown(slotIndex)
    }

    override fun setLedSuccess() { 
        logger.log("E60V2_HAL", "E60V2 LED -> GREEN") 
        Led().set(Led.LED_GREEN, Led.LED_ON)
        Led().set(Led.LED_RED, Led.LED_OFF)
    }
    
    override fun setLedFailed() { 
        logger.log("E60V2_HAL", "E60V2 LED -> RED") 
        Led().set(Led.LED_GREEN, Led.LED_OFF)
        Led().set(Led.LED_RED, Led.LED_ON)
    }
    
    override fun setLedProcessing() { 
        logger.log("E60V2_HAL", "E60V2 LED -> BLUE") 
        Led().set(Led.LED_ONLINE, Led.LED_ON)
    }
    
    override fun turnOffLeds() { 
        logger.log("E60V2_HAL", "E60V2 LED -> OFF") 
        val led = Led()
        led.set(Led.LED_GREEN, Led.LED_OFF)
        led.set(Led.LED_RED, Led.LED_OFF)
        led.set(Led.LED_ONLINE, Led.LED_OFF)
    }

    override fun playSound(soundType: SoundType) { 
        logger.log("E60V2_HAL", "E60V2 Beeper -> $soundType") 
        when (soundType) {
            SoundType.SUCCESS_BEEP -> Beeper.getInstance().beep(100)
            SoundType.FAILED_BEEP, SoundType.INSUFFICIENT_BALANCE_BEEP, SoundType.CARD_ALREADY_TAPPED_BEEP -> Beeper.getInstance().beep(300)
        }
    }

    override fun startQrScan(onQrScanned: (qrContent: String) -> Unit) {
        logger.log("E60V2_HAL", "Starting High-Speed E60V2 Camera QR Scanner Engine...")
        cameraScannerEngine.startScanning(onQrScanned)
    }

    override fun stopQrScan() {
        logger.log("E60V2_HAL", "Stopping E60V2 Camera QR Scanner Engine...")
        cameraScannerEngine.stopScanning()
    }

    override fun connectMifare(): Boolean {
        return reconnectMifare("E60V2_HAL", logger) { currentMifareUid = it }
    }

    override fun authenticateMifareBlock(blockIndex: Int, keyType: MifareKeyType, key: ByteArray): Boolean {
        return authenticateMifareBlock("E60V2_HAL", logger, currentMifareUid, blockIndex, keyType, key)
    }

    override fun readMifareBlock(blockIndex: Int): ByteArray? {
        return readMifareBlock("E60V2_HAL", logger, blockIndex)
    }

    override fun writeMifareBlock(blockIndex: Int, data: ByteArray): Boolean {
        return writeMifareBlock("E60V2_HAL", logger, blockIndex, data)
    }
}

private fun RfCardInfo.toDetectedCardSession(tag: String, logger: EncryptedLogger): DetectedCardSession {
    val uidBytes = rfUid ?: ByteArray(0)
    val uidString = uidBytes.joinToString("") { "%02X".format(it) }
    val cardType = rfCardTypeName.orEmpty()
    return DetectedCardSession(
        uid = uidString,
        technology = detectTechnology(uidBytes, cardType),
        cardTypeName = cardType,
        transmitCardApdu = { apduCommand ->
            exchangeDetectedCardCommand(tag, logger, this, apduCommand)
        }
    )
}

private fun detectTechnology(uidBytes: ByteArray, cardTypeName: String): CardTechnology {
    val normalizedType = cardTypeName.uppercase()
    return when {
        normalizedType.contains("FELICA") || normalizedType.contains("KMT") -> CardTechnology.FELICA
        normalizedType.contains("MIFARE") || normalizedType.contains("M1") -> CardTechnology.MIFARE_CLASSIC
        uidBytes.size == 8 -> CardTechnology.FELICA
        uidBytes.isNotEmpty() -> CardTechnology.ISO_DEP
        else -> CardTechnology.UNKNOWN
    }
}

private fun exchangeDetectedCardCommand(
    tag: String,
    logger: EncryptedLogger,
    cardInfo: RfCardInfo,
    apduCommand: ByteArray
): ByteArray {
    logger.log(tag, "Sending card command (${apduCommand.size} bytes): ${apduCommand.toHexString()}")
    return try {
        val response = if (shouldUseFelicaTransport(cardInfo, apduCommand)) {
            transmitFelicaCommand(apduCommand)
        } else {
            RfCardDriver.getInstance().apduExchange(apduCommand)
        }
        if (response == null || response.isEmpty()) {
            logger.log(tag, "Null or empty card response", isError = true)
            byteArrayOf(0x6F.toByte(), 0x00.toByte())
        } else {
            response
        }
    } catch (error: Throwable) {
        logger.log(tag, "Card command failed: ${error.message}", isError = true)
        byteArrayOf(0x6F.toByte(), 0x00.toByte())
    }
}

private fun shouldUseFelicaTransport(cardInfo: RfCardInfo, command: ByteArray): Boolean {
    val typeName = cardInfo.rfCardTypeName.orEmpty().uppercase()
    if (typeName.contains("FELICA") || typeName.contains("KMT")) return true
    if (command.size < 2) return false
    val frameLength = command[0].toInt() and 0xFF
    val felicaCommandCode = command[1].toInt() and 0xFF
    return frameLength == command.size && felicaCommandCode in FELICA_COMMAND_CODES
}

private fun transmitFelicaCommand(command: ByteArray): ByteArray? {
    if (command.size < 2) return null
    val driver = RfCardDriver.getInstance()
    val felicaExecCmd = driver.javaClass.methods.firstOrNull { method ->
        method.name == "felicaExecCmd" && method.parameterTypes.size == 5
    } ?: return driver.apduExchange(command)

    val responseLength = IntArray(2)
    val responseData = ByteArray(MAX_FELICA_RESPONSE_BYTES)
    val payload = command.copyOfRange(1, command.size)
    val result = felicaExecCmd.invoke(
        driver,
        command[0],
        payload.size,
        payload,
        responseLength,
        responseData
    ) as? Int ?: -1
    return if (result == 0 && responseLength[0] > 0) {
        responseData.copyOf(responseLength[0])
    } else {
        null
    }
}

private fun reconnectMifare(tag: String, logger: EncryptedLogger, onUid: (ByteArray) -> Unit): Boolean {
    return try {
        RfCardDriver.getInstance().close()
        val openResult = RfCardDriver.getInstance().open()
        if (openResult != 0) {
            logger.log(tag, "MIFARE reconnect open failed: $openResult", isError = true)
            return false
        }
        val cardInfo = RfCardDriver.getInstance().searchCard(MIFARE_SEARCH_TIMEOUT_MS)
        if (cardInfo != null && cardInfo.searchResult == RfCardInfo.NFC_ERR_NONE) {
            onUid(cardInfo.rfUid ?: ByteArray(0))
            true
        } else {
            logger.log(tag, "MIFARE card not found during reconnect", isError = true)
            false
        }
    } catch (error: Throwable) {
        logger.log(tag, "MIFARE reconnect failed: ${error.message}", isError = true)
        false
    }
}

private fun authenticateMifareBlock(
    tag: String,
    logger: EncryptedLogger,
    uid: ByteArray,
    blockIndex: Int,
    keyType: MifareKeyType,
    key: ByteArray
): Boolean {
    if (uid.isEmpty() || key.size != MIFARE_KEY_SIZE_BYTES) {
        logger.log(tag, "MIFARE auth rejected due to invalid UID/key length", isError = true)
        return false
    }
    return try {
        val sdkKeyType = when (keyType) {
            MifareKeyType.KEY_A -> RfCardInfo.NFC_KEYA
            MifareKeyType.KEY_B -> RfCardInfo.NFC_KEYB
        }
        RfCardDriver.getInstance().m1Authenticate(blockIndex.toByte(), sdkKeyType, key, uid) == 0
    } catch (error: Throwable) {
        logger.log(tag, "MIFARE auth failed: ${error.message}", isError = true)
        false
    }
}

private fun readMifareBlock(tag: String, logger: EncryptedLogger, blockIndex: Int): ByteArray? {
    return try {
        val data = ByteArray(MIFARE_BLOCK_SIZE_BYTES)
        val result = RfCardDriver.getInstance().m1ReadBlock(blockIndex.toByte(), data)
        if (result == 0) data else null
    } catch (error: Throwable) {
        logger.log(tag, "MIFARE read failed: ${error.message}", isError = true)
        null
    }
}

private fun writeMifareBlock(tag: String, logger: EncryptedLogger, blockIndex: Int, data: ByteArray): Boolean {
    if (data.size != MIFARE_BLOCK_SIZE_BYTES) {
        logger.log(tag, "MIFARE write rejected due to invalid block size ${data.size}", isError = true)
        return false
    }
    return try {
        RfCardDriver.getInstance().m1WriteBlock(blockIndex.toByte(), data) == 0
    } catch (error: Throwable) {
        logger.log(tag, "MIFARE write failed: ${error.message}", isError = true)
        false
    }
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

private val FELICA_COMMAND_CODES = setOf(0x00, 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0E, 0x10)
private const val MAX_FELICA_RESPONSE_BYTES = 300
private const val MIFARE_SEARCH_TIMEOUT_MS = 500
private const val MIFARE_BLOCK_SIZE_BYTES = 16
private const val MIFARE_KEY_SIZE_BYTES = 6
