package com.enterprise.busvalidator.core.network

import com.enterprise.busvalidator.core.model.OperatorConfig
import com.enterprise.busvalidator.core.model.OperatorPresets
import com.enterprise.busvalidator.core.model.TerminalConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperatorRuntimeConfigStore @Inject constructor() {
    private val defaultTerminalConfig = TerminalConfig(
        merchantId = "",
        terminalId = "",
        pinCode = "",
        processingCode = "",
        samId = "",
        marriageCode = "",
        operatorConfig = OperatorPresets.BISKITA_BEKASI
    )

    private val _terminalConfig = MutableStateFlow(defaultTerminalConfig)
    val terminalConfig: StateFlow<TerminalConfig> = _terminalConfig.asStateFlow()

    val activeTerminalConfig: TerminalConfig
        get() = _terminalConfig.value

    val activeOperatorConfig: OperatorConfig
        get() = _terminalConfig.value.operatorConfig

    fun setActiveOperator(operatorConfig: OperatorConfig) {
        _terminalConfig.value = _terminalConfig.value.copy(operatorConfig = operatorConfig)
    }

    fun setTerminalConfig(terminalConfig: TerminalConfig) {
        _terminalConfig.value = terminalConfig
    }
}
