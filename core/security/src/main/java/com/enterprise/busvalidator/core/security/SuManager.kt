package com.enterprise.busvalidator.core.security

import java.io.DataOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Root Executor for dedicated validator Android devices.
 * Handles system reboot, RTC time adjustment, port permission elevation, and silent OTA installs.
 */
@Singleton
open class SuManager @Inject constructor(
    private val logger: EncryptedLogger
) {
    open fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c id")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    fun executeRootCommand(command: String): Boolean {
        return try {
            logger.log("SuManager", "Executing root command: $command")
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { dos ->
                dos.writeBytes("$command\n")
                dos.writeBytes("exit\n")
                dos.flush()
            }
            val result = process.waitFor() == 0
            logger.log("SuManager", "Command result: $result")
            result
        } catch (e: Exception) {
            logger.log("SuManager", "Root execution error: ${e.message}", isError = true)
            false
        }
    }

    fun rebootDevice(): Boolean {
        return executeRootCommand("reboot")
    }

    fun installApkSilently(apkFilePath: String): Boolean {
        return executeRootCommand("pm install -r '$apkFilePath' && am start -n com.enterprise.busvalidator/.MainActivity")
    }

    fun setSystemTime(utcMillis: Long): Boolean {
        val seconds = utcMillis / 1000
        return executeRootCommand("date -u @$seconds || date -s @$seconds")
    }
}
