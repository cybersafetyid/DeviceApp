package com.enterprise.busvalidator

import android.app.Application
import com.enterprise.busvalidator.core.devicemanager.AppHealthWatchdog
import com.enterprise.busvalidator.core.devicemanager.RemoteControlManager
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.enterprise.busvalidator.core.security.MultiSourceTimeSyncEngine
import com.enterprise.busvalidator.core.security.RuntimePermissionProvisioner
import com.enterprise.busvalidator.core.sync.SyncManager
import com.enterprise.busvalidator.core.sync.TelemetrySyncManager
import com.lenz.system.LenzSystemManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BusValidatorApplication : Application() {

    @Inject lateinit var watchdog: AppHealthWatchdog
    @Inject lateinit var remoteControlManager: RemoteControlManager
    @Inject lateinit var logger: EncryptedLogger
    @Inject lateinit var permissionProvisioner: RuntimePermissionProvisioner
    @Inject lateinit var telemetrySyncManager: TelemetrySyncManager
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var timeSyncEngine: MultiSourceTimeSyncEngine

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
        timeSyncEngine.startContinuousValidation(applicationScope)
        telemetrySyncManager.start(applicationScope)
        remoteControlManager.listenRemoteCommands(applicationScope)
        startTransactionSyncLoop()
    }

    private fun activateDaemonMode() {
        try {
            LenzSystemManager.Default().startDaemonApp(packageName)
            logger.log("Application", "Daemon Mode Activated for $packageName")
        } catch (e: Exception) {
            logger.log("Application", "Failed to start Daemon Mode: ${e.message}", isError = true)
        }
    }

    private fun startTransactionSyncLoop() {
        applicationScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching {
                    syncManager.syncPendingTransactions()
                }.onFailure { error ->
                    logger.log("Application", "Scheduled transaction sync failed: ${error.message}", isError = true)
                }
                delay(TRANSACTION_SYNC_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val TRANSACTION_SYNC_INTERVAL_MS = 30_000L
    }
}
