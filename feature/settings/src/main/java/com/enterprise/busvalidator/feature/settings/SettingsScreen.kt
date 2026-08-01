package com.enterprise.busvalidator.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterprise.busvalidator.core.common.AppVersionProvider
import com.enterprise.busvalidator.core.model.ApiEnvironment
import com.enterprise.busvalidator.core.model.OperatorPresets
import com.enterprise.busvalidator.core.model.OperatorSubService
import com.enterprise.busvalidator.core.model.TerminalConfig
import com.enterprise.busvalidator.core.model.VendorDeviceModel

@Composable
fun SettingsScreen(
    currentConfig: TerminalConfig?,
    currentVendor: VendorDeviceModel,
    currentApiEnvironment: ApiEnvironment,
    onOperatorSubServiceSelected: (OperatorSubService) -> Unit,
    onApiEnvironmentSelected: (ApiEnvironment) -> Unit,
    onVendorSelected: (VendorDeviceModel) -> Unit,
    onOpenDiagnosticClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val appVersion = remember { AppVersionProvider.getAppVersion(context) }
    val activeConfig = (currentConfig?.operatorConfig ?: OperatorPresets.BISKITA_BEKASI)
        .withApiEnvironment(currentApiEnvironment)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "OPERATOR & HARDWARE SETTINGS", color = Color(0xFF38BDF8), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Dynamic Build: ${appVersion.formattedVersion} (Code: ${appVersion.versionCode})", color = Color.Gray, fontSize = 12.sp)
                }
                Button(onClick = onBackClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) {
                    Text("KEMBALI")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section 1: Multi-Operator Profile Selector
        item {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Pilih Operator Transit & Layanan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text = "Aktif: ${activeConfig.operatorName} (${activeConfig.subService.name})", color = Color(0xFF38BDF8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Environment: ${activeConfig.apiEnvironment.name}", color = Color(0xFF38BDF8), fontSize = 12.sp)
                    Text(text = "Base URL: ${activeConfig.baseUrl}", color = Color.LightGray, fontSize = 11.sp)
                    Text(text = "MQTT: ${activeConfig.mqttBrokerConfig.brokerUrl}", color = Color.LightGray, fontSize = 11.sp)
                    Text(
                        text = "Tarif Base: Rp ${activeConfig.fareRulePolicy.baseFare} | Pelajar: Rp ${activeConfig.fareRulePolicy.studentFare} | Lansia: Rp ${activeConfig.fareRulePolicy.seniorCitizenFare}",
                        color = Color.Yellow,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "API Environment:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        OperatorButton("PROD", currentApiEnvironment == ApiEnvironment.PRODUCTION) {
                            onApiEnvironmentSelected(ApiEnvironment.PRODUCTION)
                        }
                        OperatorButton("DEV", currentApiEnvironment == ApiEnvironment.DEVELOPMENT) {
                            onApiEnvironmentSelected(ApiEnvironment.DEVELOPMENT)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "Biskita Transit:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        OperatorButton("Bekasi (E60Q/V2)", activeConfig.subService == OperatorSubService.BISKITA_BEKASI) {
                            onOperatorSubServiceSelected(OperatorSubService.BISKITA_BEKASI)
                        }
                        OperatorButton("Depok (E60V2)", activeConfig.subService == OperatorSubService.BISKITA_DEPOK) {
                            onOperatorSubServiceSelected(OperatorSubService.BISKITA_DEPOK)
                        }
                        OperatorButton("Bogor (E60Q/V2)", activeConfig.subService == OperatorSubService.BISKITA_BOGOR) {
                            onOperatorSubServiceSelected(OperatorSubService.BISKITA_BOGOR)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Citra Shuttle:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        OperatorButton("Citra Raya (E60Q/V2)", activeConfig.subService == OperatorSubService.CITRA_RAYA) {
                            onOperatorSubServiceSelected(OperatorSubService.CITRA_RAYA)
                        }
                        OperatorButton("Citra Maja (E60Q/V2)", activeConfig.subService == OperatorSubService.CITRA_MAJA) {
                            onOperatorSubServiceSelected(OperatorSubService.CITRA_MAJA)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Surabaya Municipal:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        OperatorButton("Wara Wiri (E60Q)", activeConfig.subService == OperatorSubService.SURABAYA_WARA_WIRI) {
                            onOperatorSubServiceSelected(OperatorSubService.SURABAYA_WARA_WIRI)
                        }
                        OperatorButton("Bus Surabaya (Q6)", activeConfig.subService == OperatorSubService.SURABAYA_BUS) {
                            onOperatorSubServiceSelected(OperatorSubService.SURABAYA_BUS)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section 2: Hardware Driver Override
        item {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Vendor Hardware Driver Override", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text = "Active Hardware Driver: ${currentVendor.name}", color = Color.Yellow, fontSize = 14.sp)
                    Text(
                        text = "Kompatibilitas Operator (${activeConfig.operatorName}): ${activeConfig.supportedHardwareModels.joinToString()}",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onVendorSelected(VendorDeviceModel.AUTO) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                            Text("AUTO DETECT")
                        }
                        Button(onClick = { onVendorSelected(VendorDeviceModel.E60Q) }) { Text("E60Q") }
                        Button(onClick = { onVendorSelected(VendorDeviceModel.E60V2) }) { Text("E60V2") }
                        Button(onClick = { onVendorSelected(VendorDeviceModel.Q6) }) { Text("Q6") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section 3: Diagnostic Button
        item {
            Button(
                onClick = onOpenDiagnosticClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Text("BUKA SELF-DIAGNOSTIC HARDWARE", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun OperatorButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF10B981) else Color(0xFF334155)
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}
