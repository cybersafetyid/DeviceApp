package com.enterprise.busvalidator

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.enterprise.busvalidator.core.database.TransactionDao
import com.enterprise.busvalidator.core.devicemanager.InitStep
import com.enterprise.busvalidator.core.devicemanager.InitializationPipelineManager
import com.enterprise.busvalidator.core.hardware.drivers.VendorDriverFactory
import com.enterprise.busvalidator.core.location.BusLocationManager
import com.enterprise.busvalidator.core.model.*
import com.enterprise.busvalidator.core.payment.PaymentEngine
import com.enterprise.busvalidator.core.security.RuntimePermissionProvisioner
import com.enterprise.busvalidator.feature.diagnostic.HardwareDiagnosticScreen
import com.enterprise.busvalidator.feature.settings.SettingsScreen
import com.enterprise.busvalidator.feature.validator.InteractiveInitializationSplashScreen
import com.enterprise.busvalidator.feature.validator.UiTransactionState
import com.enterprise.busvalidator.feature.validator.ValidatorDashboardScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Screen {
    SPLASH,
    DASHBOARD,
    SETTINGS,
    DIAGNOSTIC
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var initPipeline: InitializationPipelineManager
    @Inject lateinit var paymentEngine: PaymentEngine
    @Inject lateinit var driverFactory: VendorDriverFactory
    @Inject lateinit var locationManager: BusLocationManager
    @Inject lateinit var transactionDao: TransactionDao
    @Inject lateinit var permissionProvisioner: RuntimePermissionProvisioner

    private val currentScreen = mutableStateOf(Screen.SPLASH)
    private val uiTxState = mutableStateOf<UiTransactionState>(UiTransactionState.Idle)
    private var activeOperatorConfig: OperatorConfig = OperatorPresets.BISKITA_BEKASI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            permissionProvisioner.ensureProvisioned()
            locationManager.startLocationTracking()
            initPipeline.runInitializationPipeline(activeOperatorConfig)
        }

        setContent {
            val initStep by initPipeline.initFlow.collectAsState()
            val pendingSyncCount by transactionDao.getPendingSyncCountFlow().collectAsState(initial = 0)
            val todayTxCount by transactionDao.getDailyTransactionCountFlow(startOfDayTimestamp()).collectAsState(initial = 1420)

            var loadedConfig by remember { mutableStateOf<TerminalConfig?>(null) }
            val telemetry by remember(pendingSyncCount, todayTxCount) {
                mutableStateOf(
                    TelemetryStatus(
                        pendingSyncCount = pendingSyncCount,
                        dailyTransactionCount = todayTxCount
                    )
                )
            }

            LaunchedEffect(initStep) {
                when (val step = initStep) {
                    is InitStep.Completed -> {
                        loadedConfig = step.config
                        delay(400)
                        currentScreen.value = Screen.DASHBOARD
                    }
                    is InitStep.Failed -> {
                        delay(2_000)
                        permissionProvisioner.ensureProvisioned()
                        initPipeline.runInitializationPipeline(activeOperatorConfig)
                    }
                    is InitStep.Progress -> Unit
                }
            }

            when (currentScreen.value) {
                Screen.SPLASH -> {
                    InteractiveInitializationSplashScreen(initStep = initStep)
                }
                Screen.DASHBOARD -> {
                    ValidatorDashboardScreen(
                        telemetry = telemetry,
                        terminalConfig = loadedConfig,
                        uiState = uiTxState.value,
                        onTestTap = { bankIssuer ->
                            performTestCardTap(bankIssuer, loadedConfig)
                        }
                    )
                }
                Screen.SETTINGS -> {
                    SettingsScreen(
                        currentConfig = loadedConfig,
                        currentVendor = driverFactory.getActiveDeviceModel(),
                        onOperatorSubServiceSelected = { subService ->
                            activeOperatorConfig = OperatorPresets.getPreset(subService)
                            currentScreen.value = Screen.SPLASH
                            lifecycleScope.launch {
                                permissionProvisioner.ensureProvisioned()
                                initPipeline.runInitializationPipeline(activeOperatorConfig)
                            }
                        },
                        onVendorSelected = { model -> driverFactory.setManualVendorOverride(model) },
                        onOpenDiagnosticClick = { currentScreen.value = Screen.DIAGNOSTIC },
                        onBackClick = { currentScreen.value = Screen.DASHBOARD }
                    )
                }
                Screen.DIAGNOSTIC -> {
                    HardwareDiagnosticScreen(
                        onRunDiagnostic = {},
                        onBackClick = { currentScreen.value = Screen.SETTINGS }
                    )
                }
            }
        }
    }

    private fun performTestCardTap(bankIssuer: String, terminalConfig: TerminalConfig?) {
        lifecycleScope.launch {
            val dummyUid = "A1B2C3D4"
            uiTxState.value = UiTransactionState.Processing(dummyUid)

            val record = if (bankIssuer.contains("QRIS")) {
                paymentEngine.processQrisTapFlow(
                    qrPayload = "00020101021226670016ID.GO.QRIS.WWW540440005802ID5914BISKITA BEKASI63041D9C",
                    fareRulePolicy = terminalConfig?.operatorConfig?.fareRulePolicy
                )
            } else {
                paymentEngine.processCardApduFlow(
                    cardUid = dummyUid,
                    fareRulePolicy = terminalConfig?.operatorConfig?.fareRulePolicy,
                    transmitCardApdu = { byteArrayOf(0x90.toByte(), 0x00.toByte()) }
                )
            }

            uiTxState.value = when (record.status) {
                TransactionStatus.SUCCESS -> UiTransactionState.Success(record)
                TransactionStatus.CARD_ALREADY_TAPPED -> UiTransactionState.CardAlreadyTapped(dummyUid)
                TransactionStatus.INSUFFICIENT_BALANCE -> UiTransactionState.InsufficientBalance(
                    balance = 5000L,
                    required = terminalConfig?.baseFare ?: 4000L
                )
                else -> UiTransactionState.UntrustedTimeError("Time Validation Failure")
            }

            delay(3000)
            uiTxState.value = UiTransactionState.Idle
        }
    }

    /**
     * Intercept Physical Keypad Buttons (DPAD UP/DOWN, ENTER, ESC).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                currentScreen.value = Screen.SETTINGS
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                currentScreen.value = Screen.DIAGNOSTIC
                true
            }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                if (currentScreen.value != Screen.DASHBOARD) {
                    currentScreen.value = Screen.DASHBOARD
                    true
                } else super.onKeyDown(keyCode, event)
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun startOfDayTimestamp(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
