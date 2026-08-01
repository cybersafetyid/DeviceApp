package com.enterprise.busvalidator.core.security

import java.util.concurrent.TimeUnit
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
            val process = ProcessBuilder("su", "-c", "id").start()
            if (!process.waitFor(ROOT_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return false
            }
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun executeRootCommand(
        command: String,
        timeoutSeconds: Long = ROOT_COMMAND_TIMEOUT_SECONDS
    ): Boolean {
        return try {
            logger.log("SuManager", "Executing root command: $command")
            val process = ProcessBuilder("su", "-c", command).start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                logger.log("SuManager", "Root command timed out after ${timeoutSeconds}s", isError = true)
                return false
            }

            val result = process.exitValue() == 0
            if (!result) {
                val errorText = process.errorStream.bufferedReader().use { it.readText() }.take(500)
                logger.log("SuManager", "Root command failed: $errorText", isError = true)
            }
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

    fun restartApp(
        packageName: String,
        launcherActivity: String = ".MainActivity"
    ): Boolean {
        val component = "$packageName/$launcherActivity"
        return executeRootCommand(
            command = "am force-stop ${packageName.shellQuote()} && am start -n ${component.shellQuote()}",
            timeoutSeconds = ROOT_COMMAND_TIMEOUT_SECONDS
        )
    }

    fun installApkSilently(
        apkFilePath: String,
        packageName: String = "com.enterprise.busvalidator",
        launcherActivity: String = ".MainActivity",
        restartAfterInstall: Boolean = true
    ): Boolean {
        val restartCommand = if (restartAfterInstall) {
            " && sleep 2 && am start -n ${"$packageName/$launcherActivity".shellQuote()}"
        } else {
            ""
        }
        return executeRootCommand(
            command = "chmod 0644 ${apkFilePath.shellQuote()} && pm install -r ${apkFilePath.shellQuote()}$restartCommand",
            timeoutSeconds = APK_INSTALL_TIMEOUT_SECONDS
        )
    }

    fun setSystemTime(utcMillis: Long): Boolean {
        val seconds = utcMillis / 1000
        return executeRootCommand("date -u @$seconds || date -s @$seconds")
    }

    private companion object {
        const val ROOT_COMMAND_TIMEOUT_SECONDS = 15L
        const val APK_INSTALL_TIMEOUT_SECONDS = 180L
    }
}

private fun String.shellQuote(): String = "'${replace("'", "'\"'\"'")}'"
