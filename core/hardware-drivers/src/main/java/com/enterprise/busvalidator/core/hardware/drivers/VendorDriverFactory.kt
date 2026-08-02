package com.enterprise.busvalidator.core.hardware.drivers

import android.os.Build
import com.enterprise.busvalidator.core.hardware.api.*
import com.enterprise.busvalidator.core.model.VendorDeviceModel
import com.enterprise.busvalidator.core.security.EncryptedLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceModelDetector @Inject constructor(
    private val logger: EncryptedLogger
) {
    fun detectDeviceModel(): VendorDeviceModel {
        val model = Build.MODEL.uppercase()
        val manufacturer = Build.MANUFACTURER.uppercase()
        val hardware = Build.HARDWARE.uppercase()

        logger.log("DeviceDetector", "Detecting device: Model=$model, Mfr=$manufacturer, Hw=$hardware")

        return when {
            model.contains("E60Q") -> VendorDeviceModel.E60Q
            model.contains("E60V2") || model.contains("E60") -> VendorDeviceModel.E60V2
            model.contains("Q6") -> VendorDeviceModel.Q6
            model.contains("Z90") -> VendorDeviceModel.Z90
            model.contains("A90") -> VendorDeviceModel.A90
            model.contains("Z91") -> VendorDeviceModel.Z91
            model.contains("TELPO") || manufacturer.contains("TELPO") -> VendorDeviceModel.TELPO
            model.contains("MSI") || manufacturer.contains("MSI") -> VendorDeviceModel.MSI
            else -> VendorDeviceModel.GENERIC
        }
    }
}

/**
 * Driver Adapter Factory providing safe driver injection and fallback wrappers for multi-vendor hardware.
 */
@Singleton
class VendorDriverFactory @Inject constructor(
    private val detector: DeviceModelDetector,
    private val logger: EncryptedLogger,
    private val e60qAdapter: E60QDriverAdapter,
    private val e60v2Adapter: E60V2DriverAdapter,
    private val e60SerialAdapter: E60SerialAdapter
) {
    private var activeModel: VendorDeviceModel = VendorDeviceModel.AUTO

    fun getActiveDeviceModel(): VendorDeviceModel {
        return if (activeModel == VendorDeviceModel.AUTO) detector.detectDeviceModel() else activeModel
    }

    fun setManualVendorOverride(vendorModel: VendorDeviceModel) {
        activeModel = vendorModel
        logger.log("VendorFactory", "Manual vendor override set to: $vendorModel")
    }

    fun createNfcDriver(): NfcDriver {
        return when (getActiveDeviceModel()) {
            VendorDeviceModel.E60Q -> e60qAdapter
            VendorDeviceModel.E60V2 -> e60v2Adapter
            else -> DefaultNfcDriver(logger)
        }
    }

    fun createSamDriver(): SamDriver {
        return when (getActiveDeviceModel()) {
            VendorDeviceModel.E60Q -> e60qAdapter
            VendorDeviceModel.E60V2 -> e60v2Adapter
            else -> DefaultSamDriver(logger)
        }
    }

    fun createSerialDriver(): SerialDriver {
        return when (getActiveDeviceModel()) {
            VendorDeviceModel.E60Q -> e60SerialAdapter
            VendorDeviceModel.E60V2 -> e60SerialAdapter
            else -> DefaultSerialDriver(logger)
        }
    }

    fun createMifareClassicDriver(): MifareClassicDriver {
        return when (getActiveDeviceModel()) {
            VendorDeviceModel.E60Q -> e60qAdapter
            VendorDeviceModel.E60V2 -> e60v2Adapter
            else -> DefaultMifareClassicDriver(logger)
        }
    }

    fun createScannerDriver(): ScannerDriver {
        return when (getActiveDeviceModel()) {
            VendorDeviceModel.E60Q -> e60qAdapter
            VendorDeviceModel.E60V2 -> e60v2Adapter
            else -> DefaultScannerDriver(logger)
        }
    }

    fun createLedDriver(): LedDriver {
        return when (getActiveDeviceModel()) {
            VendorDeviceModel.E60Q -> e60qAdapter
            VendorDeviceModel.E60V2 -> e60v2Adapter
            else -> DefaultLedDriver(logger)
        }
    }

    fun createAudioDriver(): AudioDriver {
        return when (getActiveDeviceModel()) {
            VendorDeviceModel.E60Q -> e60qAdapter
            VendorDeviceModel.E60V2 -> e60v2Adapter
            else -> DefaultAudioDriver(logger)
        }
    }

    fun createKeypadDriver(): KeypadDriver = DefaultKeypadDriver()
}

// Default Generic Drivers
class DefaultNfcDriver(private val logger: EncryptedLogger) : NfcDriver {
    override fun startCardListening(onCardDetected: (cardUid: String, apduHandler: (ByteArray) -> ByteArray) -> Unit) {
        logger.log("HAL_NFC", "Generic NFC card listening started")
    }
    override fun stopCardListening() { logger.log("HAL_NFC", "Generic NFC card listening stopped") }
    override fun isHardwareAvailable(): Boolean = true
}

class DefaultSamDriver(private val logger: EncryptedLogger) : SamDriver {
    override fun powerOnSamSlot(slotIndex: Int): Boolean {
        logger.log("HAL_SAM", "Generic SAM Slot $slotIndex powered ON")
        return true
    }
    override fun transmitSamApdu(apduCommand: ByteArray, slotIndex: Int): ByteArray {
        logger.log("HAL_SAM", "Generic SAM Slot $slotIndex APDU Transmit (${apduCommand.size} bytes)")
        return byteArrayOf(0x90.toByte(), 0x00.toByte())
    }
    override fun powerOffSamSlot(slotIndex: Int) { logger.log("HAL_SAM", "Generic SAM Slot $slotIndex powered OFF") }
}

class DefaultSerialDriver(private val logger: EncryptedLogger) : SerialDriver {
    override fun openSerialPort(portPath: String, baudRate: Int): Boolean {
        logger.log("HAL_SERIAL", "Opened port $portPath at $baudRate baud")
        return true
    }
    override fun writeSerialData(data: ByteArray): Boolean = true
    override fun readSerialDataFlow(): Flow<ByteArray> = emptyFlow()
    override fun closeSerialPort() { logger.log("HAL_SERIAL", "Port closed") }
}

class DefaultMifareClassicDriver(private val logger: EncryptedLogger) : MifareClassicDriver {
    override fun connectMifare(): Boolean {
        logger.log("HAL_MIFARE", "Generic MIFARE driver unavailable")
        return false
    }

    override fun authenticateMifareBlock(blockIndex: Int, keyType: MifareKeyType, key: ByteArray): Boolean = false
    override fun readMifareBlock(blockIndex: Int): ByteArray? = null
    override fun writeMifareBlock(blockIndex: Int, data: ByteArray): Boolean = false
}

class DefaultScannerDriver(private val logger: EncryptedLogger) : ScannerDriver {
    override fun startQrScan(onQrScanned: (qrContent: String) -> Unit) { logger.log("HAL_SCANNER", "Generic QR Scanner active") }
    override fun stopQrScan() { logger.log("HAL_SCANNER", "Generic QR Scanner stopped") }
}

class DefaultLedDriver(private val logger: EncryptedLogger) : LedDriver {
    override fun setLedSuccess() { logger.log("HAL_LED", "LED -> GREEN") }
    override fun setLedFailed() { logger.log("HAL_LED", "LED -> RED") }
    override fun setLedProcessing() { logger.log("HAL_LED", "LED -> BLUE") }
    override fun turnOffLeds() { logger.log("HAL_LED", "LED -> OFF") }
}

class DefaultAudioDriver(private val logger: EncryptedLogger) : AudioDriver {
    override fun playSound(soundType: SoundType) { logger.log("HAL_AUDIO", "Sound -> $soundType") }
}

class DefaultKeypadDriver : KeypadDriver {
    override fun keyEventsFlow(): Flow<KeypadButton> = emptyFlow()
}
