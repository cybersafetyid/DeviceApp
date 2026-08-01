package com.enterprise.busvalidator.core.hardware.drivers

import com.enterprise.busvalidator.core.hardware.api.SerialDriver
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.lenz.e60qsdk.M3Serial
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class E60SerialAdapter @Inject constructor(
    private val logger: EncryptedLogger
) : SerialDriver {

    private val m3Serial = M3Serial()
    
    // According to SDK: SERIAL_RS232 is 1 (or defined in M3Serial)
    private val serialPortId: Byte = M3Serial.SERIAL_RS232
    
    private val _incomingData = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
    override fun readSerialDataFlow(): Flow<ByteArray> = _incomingData.asSharedFlow()

    private var isOpen = false
    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    override fun openSerialPort(portPath: String, baudRate: Int): Boolean {
        if (isOpen) {
            logger.log("E60Serial", "RS232 Port is already open.")
            return true
        }

        logger.log("E60Serial", "Opening RS232 Port at $baudRate baud...")
        try {
            // M3Serial.BIT_8 is usually 8
            val ret = m3Serial.open(serialPortId, baudRate, M3Serial.BIT_8)
            if (ret == 0 || ret == 1) { // 0 or 1 usually indicates success depending on lenz SDK return codes
                isOpen = true
                startPolling()
                logger.log("E60Serial", "RS232 Port Opened successfully.")
                return true
            } else {
                logger.log("E60Serial", "Failed to open RS232 Port. Return code: $ret", isError = true)
                return false
            }
        } catch (e: Exception) {
            logger.log("E60Serial", "Exception opening RS232: ${e.message}", isError = true)
            return false
        }
    }

    override fun closeSerialPort(): Unit {
        if (!isOpen) return
        logger.log("E60Serial", "Closing RS232 Port...")
        try {
            isOpen = false
            pollingJob?.cancel()
            m3Serial.close(serialPortId)
            logger.log("E60Serial", "RS232 Port Closed.")
        } catch (e: Exception) {
            logger.log("E60Serial", "Exception closing RS232: ${e.message}", isError = true)
        }
    }

    override fun writeSerialData(data: ByteArray): Boolean {
        if (!isOpen) {
            logger.log("E60Serial", "Cannot send data. RS232 Port is closed.", isError = true)
            return false
        }
        try {
            // send(byte type, int len, byte[] data)
            val ret = m3Serial.send(serialPortId, data.size, data)
            if (ret > 0) {
                return true
            } else {
                logger.log("E60Serial", "Failed to send data via RS232. Return: $ret", isError = true)
                return false
            }
        } catch (e: Exception) {
            logger.log("E60Serial", "Exception sending RS232: ${e.message}", isError = true)
            return false
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isOpen && isActive) {
                try {
                    // Check if data is available
                    val status = m3Serial.check(serialPortId)
                    if (status == M3Serial.STATUS_FULL || status == M3Serial.STATUS_RECV) {
                        // recv(byte type, int timeout, int maxLen)
                        val received = m3Serial.recv(serialPortId, 10, 1024)
                        if (received != null && received.isNotEmpty()) {
                            _incomingData.tryEmit(received)
                        }
                    }
                } catch (e: Exception) {
                    logger.log("E60Serial", "Exception receiving RS232: ${e.message}", isError = true)
                }
                delay(20) // Yield thread for 20ms to prevent CPU pegging
            }
        }
    }
}
