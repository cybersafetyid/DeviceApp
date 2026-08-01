package com.enterprise.busvalidator.feature.validator

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.enterprise.busvalidator.core.model.OperatorBrand
import com.enterprise.busvalidator.core.model.TelemetryStatus
import com.enterprise.busvalidator.core.model.TerminalConfig
import com.enterprise.busvalidator.core.model.TransactionRecord
import java.text.SimpleDateFormat
import java.util.*

sealed class UiTransactionState {
    object Idle : UiTransactionState()
    data class Processing(val cardUid: String) : UiTransactionState()
    data class Success(val record: TransactionRecord) : UiTransactionState()
    data class CardAlreadyTapped(val cardUid: String) : UiTransactionState()
    data class InsufficientBalance(val balance: Long, val required: Long) : UiTransactionState()
    data class UntrustedTimeError(val message: String) : UiTransactionState()
}

/**
 * International Transit Standard Dashboard UI with Dynamic Operator Layout Engine.
 * Supports Biskita, Citra, and Surabaya with distinct UI visual identities.
 */
@Composable
fun ValidatorDashboardScreen(
    telemetry: TelemetryStatus,
    terminalConfig: TerminalConfig?,
    uiState: UiTransactionState,
    onTestTap: (bankIssuer: String) -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = when (uiState) {
            is UiTransactionState.Idle -> when (terminalConfig?.operatorConfig?.brand) {
                OperatorBrand.CITRA -> Color(0xFF064E3B) // Deep Emerald Slate for Citra
                OperatorBrand.SURABAYA -> Color(0xFF450A0A) // Deep Surabaya Maroon Red
                else -> Color(0xFF0F172A) // Sleek Dark Blue for Biskita
            }
            is UiTransactionState.Processing -> Color(0xFF1E3A8A)
            is UiTransactionState.Success -> Color(0xFF065F46) // Vibrant Transit Green
            is UiTransactionState.CardAlreadyTapped -> Color(0xFF9A3412) // Alert Orange
            is UiTransactionState.InsufficientBalance -> Color(0xFF991B1B) // Red
            is UiTransactionState.UntrustedTimeError -> Color(0xFF7F1D1D) // Dark Red
        },
        label = "BgAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Status Bar
            TopTelemetryHeader(telemetry, terminalConfig)

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content Area (Multi-Operator Engine)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (uiState) {
                    is UiTransactionState.Idle -> {
                        when (terminalConfig?.operatorConfig?.brand) {
                            OperatorBrand.CITRA -> CitraIdleContent(terminalConfig, telemetry)
                            OperatorBrand.SURABAYA -> SurabayaIdleContent(terminalConfig, telemetry)
                            else -> BiskitaIdleContent(terminalConfig, telemetry)
                        }
                    }
                    is UiTransactionState.Processing -> ProcessingStateContent(uiState.cardUid)
                    is UiTransactionState.Success -> SuccessStateContent(uiState.record)
                    is UiTransactionState.CardAlreadyTapped -> CardAlreadyTappedContent(uiState.cardUid)
                    is UiTransactionState.InsufficientBalance -> InsufficientBalanceContent(uiState.balance, uiState.required)
                    is UiTransactionState.UntrustedTimeError -> UntrustedTimeContent(uiState.message)
                }
            }

            // Bottom Simulator Bar
            SimulatorActionBar(onTestTap)
        }
    }
}

@Composable
fun TopTelemetryHeader(telemetry: TelemetryStatus, config: TerminalConfig?) {
    val context = LocalContext.current
    val appVersion = remember { AppVersionProvider.getAppVersion(context) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss | yyyy-MM-dd", Locale.US) }
    val currentTime = remember { mutableStateOf(timeFormat.format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            currentTime.value = timeFormat.format(Date())
        }
    }

    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = currentTime.value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "BUS: ${config?.busCode ?: "BUS-1049"} | ${appVersion.formattedVersion}",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(text = config?.operatorConfig?.subService?.displayName ?: "BISKITA", color = Color(0xFF38BDF8))
                StatusBadge(text = if (telemetry.isOnline) "ONLINE" else "OFFLINE", color = if (telemetry.isOnline) Color(0xFF10B981) else Color(0xFFF59E0B))
                StatusBadge(text = "GPS: 3D (${telemetry.gpsSatellites} Sats)", color = Color(0xFF3B82F6))
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.2f),
        contentColor = color,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/**
 * BISKITA UI Layout: Clean ITxPT Cyan & Dark Blue Theme.
 */
@Composable
fun BiskitaIdleContent(config: TerminalConfig?, telemetry: TelemetryStatus) {
    val farePolicy = config?.operatorConfig?.fareRulePolicy

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = Color(0xFF0284C7).copy(alpha = 0.2f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "🚌 BISKITA TRANSIT NETWORK",
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Text(
            text = config?.operatorName ?: "BISKITA BEKASI",
            color = Color(0xFF38BDF8),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${config?.routeCode ?: "BK-01"} : ${config?.routeName ?: "Terminal Bekasi - Harapan Indah"}",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .border(1.dp, Color(0xFF0284C7), RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "TEMPELKAN KARTU / SCAN QRIS", color = Color(0xFFFACC15), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "TARIF UTAMA: Rp ${farePolicy?.baseFare ?: 4000}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Pelajar: Rp ${farePolicy?.studentFare ?: 2000} | Lansia/Disabilitas: Rp ${farePolicy?.seniorCitizenFare ?: 0}",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoCard(title = "Hari Ini", value = "${telemetry.dailyTransactionCount} Tx")
            InfoCard(title = "Pending Sync", value = "${telemetry.pendingSyncCount}")
            InfoCard(title = "Device Model", value = config?.operatorConfig?.supportedHardwareModels?.joinToString("/") ?: "E60Q")
        }
    }
}

/**
 * CITRA UI Layout: Emerald & Gold Township Estate Theme.
 */
@Composable
fun CitraIdleContent(config: TerminalConfig?, telemetry: TelemetryStatus) {
    val farePolicy = config?.operatorConfig?.fareRulePolicy

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = Color(0xFFD97706).copy(alpha = 0.25f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "🏡 CITRA TOWNSHIP RESIDENTIAL SHUTTLE",
                color = Color(0xFFFBBF24),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Text(
            text = config?.operatorName ?: "CITRA RAYA SHUTTLE",
            color = Color(0xFF10B981),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${config?.routeCode ?: "CR-01"} : ${config?.routeName ?: "Citra Raya Shuttle - EcoPlaza Loop"}",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = Color(0xFF064E3B),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(2.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "TAP RESIDENT CARD / E-MONEY", color = Color(0xFFFBBF24), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "TARIF SHUTTLE: Rp ${farePolicy?.baseFare ?: 5000}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Resident/Pelajar: Rp ${farePolicy?.studentFare ?: 3000} | Lansia: Rp ${farePolicy?.seniorCitizenFare ?: 2500}",
                    color = Color(0xFFA7F3D0),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoCard(title = "Shuttle Tx", value = "${telemetry.dailyTransactionCount}")
            InfoCard(title = "Zone", value = "Estate Core")
            InfoCard(title = "Models", value = config?.operatorConfig?.supportedHardwareModels?.joinToString("/") ?: "E60V2")
        }
    }
}

/**
 * SURABAYA UI Layout: Municipal Red & Amber Suroboyo / Wara Wiri Theme.
 */
@Composable
fun SurabayaIdleContent(config: TerminalConfig?, telemetry: TelemetryStatus) {
    val farePolicy = config?.operatorConfig?.fareRulePolicy

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = Color(0xFFDC2626).copy(alpha = 0.25f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "🐊 SURABAYA MUNICIPAL TRANSIT SYSTEM",
                color = Color(0xFFF87171),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Text(
            text = config?.operatorName ?: "BUS SURABAYA",
            color = Color(0xFFFBBF24),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${config?.routeCode ?: "SB-01"} : ${config?.routeName ?: "Suroboyo Bus Purabaya - Rajawali"}",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = Color(0xFF7F1D1D),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(2.dp, Color(0xFFFBBF24), RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "TAP E-MONEY / SCAN QRIS", color = Color(0xFFFBBF24), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "TARIF FLAT: Rp ${farePolicy?.baseFare ?: 5000}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Pelajar/Veteran: Rp ${farePolicy?.studentFare ?: 2500} | Gratis Lansia & Disabilitas",
                    color = Color(0xFFFECACA),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚡ Integrasi Feeder Wara Wiri - Suroboyo Bus (Free Transfer 30 Mins)",
                    color = Color.Yellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoCard(title = "Municipal Tx", value = "${telemetry.dailyTransactionCount}")
            InfoCard(title = "Transfer Window", value = "30 Mins")
            InfoCard(title = "Model", value = config?.operatorConfig?.supportedHardwareModels?.joinToString("/") ?: "Q6")
        }
    }
}

@Composable
fun InfoCard(title: String, value: String) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, color = Color.Gray, fontSize = 10.sp)
            Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProcessingStateContent(cardUid: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "MEMPROSES PEMBAYARAN...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = "UID: $cardUid", color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
fun SuccessStateContent(record: TransactionRecord) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "✓ TRANSAKSI BERHASIL", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "${record.bankIssuer} [${record.cardUid.takeLast(4)}]", color = Color.Yellow, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text = "TERPOTONG: Rp ${record.amountDeducted}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = "SISA SALDO: Rp ${record.finalBalance}", color = Color(0xFF6EE7B7), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CardAlreadyTappedContent(cardUid: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "⚠ KARTU SUDAH TERPOTONG", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Anti-Passback Safeguard Protection", color = Color.LightGray, fontSize = 14.sp)
        Text(text = "Mohon tidak menempelkan kartu berulang.", color = Color.Yellow, fontSize = 14.sp)
    }
}

@Composable
fun InsufficientBalanceContent(balance: Long, required: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "✕ SALDO TIDAK CUKUP", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "SALDO KARTU: Rp $balance", color = Color.Yellow, fontSize = 18.sp)
        Text(text = "DIBUTUHKAN: Rp $required", color = Color.White, fontSize = 18.sp)
    }
}

@Composable
fun UntrustedTimeContent(msg: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "⛔ SYSTEM TIME UNTRUSTED", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Waktu sistem tidak valid. Transaksi dibekukan.", color = Color.Yellow, fontSize = 14.sp)
        Text(text = msg, color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
fun SimulatorActionBar(onTestTap: (bankIssuer: String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = { onTestTap("BCA FLAZZ") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
            Text("Tap Flazz", fontSize = 11.sp)
        }
        Button(onClick = { onTestTap("MANDIRI E-MONEY") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))) {
            Text("Tap e-Money", fontSize = 11.sp)
        }
        Button(onClick = { onTestTap("BNI TAPCASH") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))) {
            Text("Tap TapCash", fontSize = 11.sp)
        }
        Button(onClick = { onTestTap("BRI BRIZZI") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))) {
            Text("Tap Brizzi", fontSize = 11.sp)
        }
    }
}
