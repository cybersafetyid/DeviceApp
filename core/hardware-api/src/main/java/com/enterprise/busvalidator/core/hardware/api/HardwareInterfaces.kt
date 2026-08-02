package com.enterprise.busvalidator.core.hardware.api

import kotlinx.coroutines.flow.Flow

enum class CardTechnology {
    ISO_DEP,
    FELICA,
    MIFARE_CLASSIC,
    UNKNOWN
}

data class DetectedCardSession(
    val uid: String,
    val technology: CardTechnology,
    val cardTypeName: String = "",
    val transmitCardApdu: (ByteArray) -> ByteArray
)

interface NfcDriver {
    fun startCardListening(onCardDetected: (cardUid: String, apduHandler: (ByteArray) -> ByteArray) -> Unit)
    fun startCardSessionListening(onCardDetected: (DetectedCardSession) -> Unit) {
        startCardListening { cardUid, apduHandler ->
            onCardDetected(
                DetectedCardSession(
                    uid = cardUid,
                    technology = CardTechnology.UNKNOWN,
                    transmitCardApdu = apduHandler
                )
            )
        }
    }
    fun stopCardListening()
    fun isHardwareAvailable(): Boolean
}

interface SamDriver {
    fun powerOnSamSlot(slotIndex: Int = 0): Boolean
    fun transmitSamApdu(apduCommand: ByteArray, slotIndex: Int = 0): ByteArray
    fun powerOffSamSlot(slotIndex: Int = 0)
}

interface SerialDriver {
    fun openSerialPort(portPath: String, baudRate: Int): Boolean
    fun writeSerialData(data: ByteArray): Boolean
    fun readSerialDataFlow(): Flow<ByteArray>
    fun closeSerialPort()
}

enum class MifareKeyType {
    KEY_A,
    KEY_B
}

interface MifareClassicDriver {
    fun connectMifare(): Boolean
    fun authenticateMifareBlock(blockIndex: Int, keyType: MifareKeyType, key: ByteArray): Boolean
    fun readMifareBlock(blockIndex: Int): ByteArray?
    fun writeMifareBlock(blockIndex: Int, data: ByteArray): Boolean
}

interface ScannerDriver {
    fun startQrScan(onQrScanned: (qrContent: String) -> Unit)
    fun stopQrScan()
}

interface LedDriver {
    fun setLedSuccess()   // Green LED
    fun setLedFailed()    // Red LED
    fun setLedProcessing()// Blue/Yellow LED
    fun turnOffLeds()
}

enum class SoundType {
    SUCCESS_BEEP,
    FAILED_BEEP,
    INSUFFICIENT_BALANCE_BEEP,
    CARD_ALREADY_TAPPED_BEEP
}

interface AudioDriver {
    fun playSound(soundType: SoundType)
}

enum class KeypadButton {
    UP,
    DOWN,
    ACCEPT_ENTER,
    CANCEL_ESC
}

interface KeypadDriver {
    fun keyEventsFlow(): Flow<KeypadButton>
}
