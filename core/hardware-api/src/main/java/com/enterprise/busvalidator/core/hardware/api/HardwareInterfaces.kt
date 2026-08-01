package com.enterprise.busvalidator.core.hardware.api

import kotlinx.coroutines.flow.Flow

interface NfcDriver {
    fun startCardListening(onCardDetected: (cardUid: String, apduHandler: (ByteArray) -> ByteArray) -> Unit)
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
