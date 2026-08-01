package com.enterprise.busvalidator

import android.app.Application
import com.enterprise.busvalidator.core.devicemanager.AppHealthWatchdog
import com.enterprise.busvalidator.core.network.MqttTelemetryClient
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.enterprise.busvalidator.core.devicemanager.LenzDeviceManager
import com.lenz.system.LenzSystemManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class BusValidatorApplication : Application() {

    @Inject lateinit var watchdog: AppHealthWatchdog
    @Inject lateinit var mqttClient: MqttTelemetryClient
    @Inject lateinit var logger: EncryptedLogger

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        logger.log("Application", "Bus Validator Enterprise System Starting...")
        
        try {
            LenzSystemManager.Default().startDaemonApp(packageName)
            logger.log("Application", "Daemon Mode Activated for $packageName")
        } catch (e: Exception) {
            logger.log("Application", "Failed to start Daemon Mode: ${e.message}", isError = true)
        }

        watchdog.startWatchdog(applicationScope)
        mqttClient.connect()
    }
}
