package com.enterprise.busvalidator.core.devicemanager

import android.content.Context
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.lenz.e60qsdk.sdkJni
import com.lenz.system.LenzSystemManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LenzDeviceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: EncryptedLogger
) {
    private val sdkInstance = sdkJni.getInstance()
    private val lenzSystemManager = LenzSystemManager.Default() // Need actual system binder if required, or static method if available. Usually binding happens via aidl or reflection. Assuming standard instantiation for now.

    fun rebootDevice() {
        logger.log("DeviceManager", "Rebooting device via LenzSystemManager...")
        try {
            lenzSystemManager.reboot()
        } catch (e: Exception) {
            logger.log("DeviceManager", "Reboot failed: ${e.message}", isError = true)
        }
    }

    fun shutdownDevice() {
        logger.log("DeviceManager", "Shutting down device via LenzSystemManager...")
        try {
            lenzSystemManager.shutdown()
        } catch (e: Exception) {
            logger.log("DeviceManager", "Shutdown failed: ${e.message}", isError = true)
        }
    }

    fun installApp(apkPath: String): Boolean {
        logger.log("DeviceManager", "Installing APK from path: $apkPath")
        return try {
            lenzSystemManager.setInstallApkPath(apkPath)
            true
        } catch (e: Exception) {
            logger.log("DeviceManager", "Install failed: ${e.message}", isError = true)
            false
        }
    }

    fun uninstallApp(packageName: String) {
        logger.log("DeviceManager", "Uninstalling package: $packageName")
        try {
            lenzSystemManager.uninstallApp(packageName)
        } catch (e: Exception) {
            logger.log("DeviceManager", "Uninstall failed: ${e.message}", isError = true)
        }
    }

    fun getKernelVersion(): String {
        return try {
            sdkInstance.m3KernelVer()?.let { String(it).trim() } ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun getBootVersion(): String {
        return try {
            sdkInstance.m3BootVer()?.let { String(it).trim() } ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun getHardwareVersion(): String {
        return try {
            sdkInstance.m3HwVer()?.let { String(it).trim() } ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun getSdkVersion(): String {
        return try {
            sdkInstance.sdkVersion()?.let { String(it).trim() } ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun updateFirmware(kernelType: Int, kernelLength: Int, buffer: ByteArray, crc: Int): Boolean {
        logger.log("DeviceManager", "Starting Kernel Update...")
        return try {
            val result = sdkInstance.m3KernelUpdate(kernelType, kernelLength, buffer, crc)
            if (result == 0) {
                logger.log("DeviceManager", "Kernel Update Success.")
                true
            } else {
                logger.log("DeviceManager", "Kernel Update Failed with code: $result", isError = true)
                false
            }
        } catch (e: Exception) {
            logger.log("DeviceManager", "Kernel Update Exception: ${e.message}", isError = true)
            false
        }
    }

    fun executeSystemCommand(command: String): String {
        logger.log("DeviceManager", "Executing System Command: $command")
        return try {
            val resultBytes = sdkInstance.systemCmdExec(command.toByteArray())
            if (resultBytes != null) String(resultBytes).trim() else ""
        } catch (e: Exception) {
            logger.log("DeviceManager", "Command execution failed: ${e.message}", isError = true)
            ""
        }
    }
}
