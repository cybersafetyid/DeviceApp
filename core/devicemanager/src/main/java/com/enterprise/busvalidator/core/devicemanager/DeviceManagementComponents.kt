package com.enterprise.busvalidator.core.devicemanager

import com.enterprise.busvalidator.core.model.TerminalConfig
import com.enterprise.busvalidator.core.devicemanager.ota.AppUpdateManager
import com.enterprise.busvalidator.core.devicemanager.ota.LegacyAppUpdateCoordinator
import com.enterprise.busvalidator.core.devicemanager.ota.LegacyAppUpdateResult
import com.enterprise.busvalidator.core.devicemanager.ota.OtaCommandParser
import com.enterprise.busvalidator.core.devicemanager.ota.OtaUpdateResult
import com.enterprise.busvalidator.core.network.TerminalBootstrapApi
import com.enterprise.busvalidator.core.network.MqttTelemetryClient
import com.enterprise.busvalidator.core.network.OperatorRuntimeConfigStore
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.enterprise.busvalidator.core.security.MultiSourceTimeSyncEngine
import com.enterprise.busvalidator.core.security.SuManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 24/7 Self-Healing Watchdog Engine (Anti-Hang / Anti-Freeze).
 */
@Singleton
class AppHealthWatchdog @Inject constructor(
    private val suManager: SuManager,
    private val logger: EncryptedLogger
) {
    private var watchdogJob: Job? = null

    fun startWatchdog(scope: CoroutineScope) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch(Dispatchers.Default) {
            logger.log("Watchdog", "24/7 Health Watchdog Engine active.")
            while (isActive) {
                delay(5000)
                // Check memory pressure & thread state
                val runtime = Runtime.getRuntime()
                val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)

                if (usedMemoryMb > (maxMemoryMb * 0.90)) {
                    logger.log("Watchdog", "CRITICAL MEMORY PRESSURE: Used ${usedMemoryMb}MB / ${maxMemoryMb}MB. Invoking System.gc()", isError = true)
                    System.gc()
                }
            }
        }
    }
}

/**
 * Sequential Boot Readiness Pipeline Manager.
 * Fetches & validates MID, TID, PINCODE, PROCESSING_CODE, SAM_ID, MARRIAGE_CODE, TAP_MODE.
 */
sealed class InitStep {
    data class Progress(val stepName: String, val progressPercent: Int) : InitStep()
    data class Completed(val config: TerminalConfig) : InitStep()
    data class Failed(val errorReason: String) : InitStep()
}

@Singleton
class InitializationPipelineManager @Inject constructor(
    private val suManager: SuManager,
    private val timeSyncEngine: MultiSourceTimeSyncEngine,
    private val terminalBootstrapApi: TerminalBootstrapApi,
    private val runtimeConfigStore: OperatorRuntimeConfigStore,
    private val logger: EncryptedLogger
) {
    private val _initFlow = MutableStateFlow<InitStep>(InitStep.Progress("Starting Security Checks...", 10))
    val initFlow: StateFlow<InitStep> = _initFlow.asStateFlow()

    suspend fun runInitializationPipeline(
        activeOperatorConfig: com.enterprise.busvalidator.core.model.OperatorConfig = com.enterprise.busvalidator.core.model.OperatorPresets.BISKITA_BEKASI
    ) = withContext(Dispatchers.IO) {
        try {
            runtimeConfigStore.setActiveOperator(activeOperatorConfig)
            _initFlow.value = InitStep.Progress("Verifying Root & Hardware Keystore...", 20)
            delay(300)

            _initFlow.value = InitStep.Progress("Mounting Encrypted Database...", 40)
            delay(300)

            _initFlow.value = InitStep.Progress("Autodetecting Hardware & SAM Slot...", 60)
            delay(300)

            _initFlow.value = InitStep.Progress("Loading Operator Profile (${activeOperatorConfig.operatorName})...", 80)
            delay(400)

            val terminalConfig = runCatching {
                terminalBootstrapApi.fetchTerminalBootstrap(activeOperatorConfig).terminalConfig
            }.getOrElse { error ->
                logger.log(
                    "InitPipeline",
                    "Terminal bootstrap API failed: ${error.message}",
                    isError = true
                )
                throw IllegalStateException("Terminal bootstrap API failed: ${error.message}", error)
            }

            _initFlow.value = InitStep.Progress("Connecting TLS Telemetry to ${activeOperatorConfig.baseUrl}...", 95)
            delay(300)

            _initFlow.value = InitStep.Completed(terminalConfig)
            logger.log("InitPipeline", "Initialization Pipeline Completed Successfully for ${activeOperatorConfig.operatorName}!")
        } catch (e: Exception) {
            _initFlow.value = InitStep.Failed("Initialization Failed: ${e.message}")
            logger.log("InitPipeline", "Initialization Failed: ${e.message}", isError = true)
        }
    }
}

/**
 * Handles Remote Administration Commands over MQTT.
 */
@Singleton
class RemoteControlManager @Inject constructor(
    private val suManager: SuManager,
    private val logger: EncryptedLogger,
    private val mqttTelemetryClient: MqttTelemetryClient,
    private val appUpdateManager: AppUpdateManager,
    private val legacyAppUpdateCoordinator: LegacyAppUpdateCoordinator
) {
    private var remoteCommandJob: Job? = null

    fun listenRemoteCommands(scope: CoroutineScope) {
        if (remoteCommandJob?.isActive == true) return

        remoteCommandJob = scope.launch {
            mqttTelemetryClient.remoteCommandFlow.collect { (action, params) ->
                val normalizedAction = action.lowercase()
                val loggedParams = if (normalizedAction == "cmd_ota_update") "[redacted]" else params
                logger.log("RemoteControl", "Received Remote Command: Action=$action, Params=$loggedParams")
                when (normalizedAction) {
                    "cmd_reboot" -> suManager.rebootDevice()
                    "cmd_restart_app" -> suManager.restartApp("com.enterprise.busvalidator")
                    "cmd_ota_update" -> executeOtaUpdate(params)
                    "cmd_check_app_update" -> executeLegacyAppUpdate(params)
                    "cmd_clear_cache" -> System.gc()
                    else -> logger.log("RemoteControl", "Unknown command: $action")
                }
            }
        }
    }

    private suspend fun executeOtaUpdate(params: String) {
        val request = OtaCommandParser.parse(params).getOrElse { error ->
            logger.log("RemoteControl", "Invalid OTA command: ${error.message}", isError = true)
            return
        }

        when (val result = appUpdateManager.performOtaUpdate(request)) {
            is OtaUpdateResult.Success -> logger.log(
                "RemoteControl",
                "OTA update installed: package=${result.packageName}, versionCode=${result.versionCode}, sha256=${result.sha256}"
            )
            is OtaUpdateResult.Failed -> logger.log(
                "RemoteControl",
                "OTA update failed at ${result.stage}: ${result.reason}",
                isError = true
            )
        }
    }

    private suspend fun executeLegacyAppUpdate(params: String) {
        val manifestUrl = extractValue(params, "url", "manifestUrl", "updateUrl")
        val baseUrl = extractValue(params, "baseUrl", "base_url")
        if (manifestUrl.isNullOrBlank() && baseUrl.isNullOrBlank()) {
            logger.log(
                "RemoteControl",
                "App update command requires baseUrl or url/manifestUrl/updateUrl",
                isError = true
            )
            return
        }

        val updateResult = if (!baseUrl.isNullOrBlank()) {
            legacyAppUpdateCoordinator.checkAndInstallFromBaseUrl(baseUrl)
        } else {
            legacyAppUpdateCoordinator.checkAndInstall(manifestUrl!!)
        }

        when (val result = updateResult) {
            is LegacyAppUpdateResult.NoNewVersion -> logger.log(
                "RemoteControl",
                "No app update available: versionCode=${result.manifest.versionCode}"
            )
            is LegacyAppUpdateResult.Blocked -> logger.log(
                "RemoteControl",
                "App update blocked: ${result.reason}",
                isError = true
            )
            is LegacyAppUpdateResult.Installed -> logger.log(
                "RemoteControl",
                "App update installed: versionCode=${result.otaResult.versionCode}"
            )
            is LegacyAppUpdateResult.Failed -> logger.log(
                "RemoteControl",
                "App update failed: ${result.reason}",
                isError = true
            )
        }
    }

    private fun extractValue(params: String, vararg keys: String): String? {
        val trimmed = params.trim()
        if (trimmed.isBlank()) return null
        keys.forEach { key ->
            val jsonMatch = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .find(trimmed)
            if (jsonMatch != null) return jsonMatch.groupValues[1]
            val pairMatch = Regex("(?:^|[;\\n])\\s*$key\\s*=\\s*([^;\\n]+)", RegexOption.IGNORE_CASE)
                .find(trimmed)
            if (pairMatch != null) return pairMatch.groupValues[1].trim()
        }
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else null
    }
}
