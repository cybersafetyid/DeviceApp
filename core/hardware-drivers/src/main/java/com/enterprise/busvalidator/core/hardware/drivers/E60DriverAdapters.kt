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
) : NfcDriver, SamDriver, LedDriver, AudioDriver, ScannerDriver {

    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun startCardListening(onCardDetected: (cardUid: String, apduHandler: (ByteArray) -> ByteArray) -> Unit) {
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
                        
                        logger.log("E60Q_HAL", "Card Detected! UID: $uidString, Type: ${cardInfo.rfCardTypeName}")
                        
                        onCardDetected(uidString) { apduCommand ->
                            logger.log("E60Q_HAL", "Sending APDU to Card: ${apduCommand.joinToString("") { "%02X".format(it) }}")
                            val response = RfCardDriver.getInstance().apduExchange(apduCommand)
                            if (response == null || response.isEmpty()) {
                                logger.log("E60Q_HAL", "Null or empty APDU response", isError = true)
                                byteArrayOf(0x6F.toByte(), 0x00.toByte())
                            } else {
                                response
                            }
                        }
                        
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
}

@Singleton
class E60V2DriverAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: EncryptedLogger
) : NfcDriver, SamDriver, LedDriver, AudioDriver, ScannerDriver {

    private val cameraScannerEngine by lazy {
        E60V2CameraScannerEngine(context, logger)
    }

    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun startCardListening(onCardDetected: (cardUid: String, apduHandler: (ByteArray) -> ByteArray) -> Unit) {
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
                        
                        logger.log("E60V2_HAL", "Card Detected! UID: $uidString, Type: ${cardInfo.rfCardTypeName}")
                        
                        onCardDetected(uidString) { apduCommand ->
                            logger.log("E60V2_HAL", "Sending APDU to Card: ${apduCommand.joinToString("") { "%02X".format(it) }}")
                            val response = RfCardDriver.getInstance().apduExchange(apduCommand)
                            if (response == null || response.isEmpty()) {
                                logger.log("E60V2_HAL", "Null or empty APDU response", isError = true)
                                byteArrayOf(0x6F.toByte(), 0x00.toByte())
                            } else {
                                response
                            }
                        }
                        
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
}
