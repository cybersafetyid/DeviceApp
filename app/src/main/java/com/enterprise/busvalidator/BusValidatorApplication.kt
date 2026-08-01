package com.enterprise.busvalidator

import android.app.Application
import com.enterprise.busvalidator.core.devicemanager.AppHealthWatchdog
import com.enterprise.busvalidator.core.network.MqttTelemetryClient
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.enterprise.busvalidator.core.security.RuntimePermissionProvisioner
import com.lenz.system.LenzSystemManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BusValidatorApplication : Application() {

    @Inject lateinit var watchdog: AppHealthWatchdog
    @Inject lateinit var mqttClient: MqttTelemetryClient
    @Inject lateinit var logger: EncryptedLogger
    @Inject lateinit var permissionProvisioner: RuntimePermissionProvisioner

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        logger.log("Application", "Bus Validator Enterprise System Starting...")

        applicationScope.launch {
            val report = permissionProvisioner.ensureProvisioned()
            if (!report.isFullyProvisioned) {
                logger.log(
                    "Application",
                    "Permission provisioning finished with failures: " +
                        "runtime=${report.failedRuntimePermissions}, " +
                        "system=${report.failedSystemCommands}, " +
                        "missingManifest=${report.missingManifestPermissions}",
                    isError = true
                )
            }
        }

        activateDaemonMode()
        watchdog.startWatchdog(applicationScope)
        mqttClient.connect()
    }

    private fun activateDaemonMode() {
        try {
            LenzSystemManager.Default().startDaemonApp(packageName)
            logger.log("Application", "Daemon Mode Activated for $packageName")
        } catch (e: Exception) {
            logger.log("Application", "Failed to start Daemon Mode: ${e.message}", isError = true)
        }
    }
}
