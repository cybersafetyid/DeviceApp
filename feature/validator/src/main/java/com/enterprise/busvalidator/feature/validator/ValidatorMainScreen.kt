package com.enterprise.busvalidator.feature.validator

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterprise.busvalidator.core.model.TelemetryStatus
import com.enterprise.busvalidator.core.model.TerminalConfig
import com.enterprise.busvalidator.core.model.TransactionRecord
import com.enterprise.busvalidator.core.model.TransactionStatus
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
 * International Transit Standard Dashboard UI (VDV / ITxPT Layout) with State-Screen Engine.
 * Strictly NO Popups / Dialogs.
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
            is UiTransactionState.Idle -> Color(0xFF0F172A) // Sleek Dark Blue
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
            // Top Status Bar (International Transit Telemetry)
            TopTelemetryHeader(telemetry, terminalConfig)

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (uiState) {
                    is UiTransactionState.Idle -> IdleStateContent(terminalConfig, telemetry)
                    is UiTransactionState.Processing -> ProcessingStateContent(uiState.cardUid)
                    is UiTransactionState.Success -> SuccessStateContent(uiState.record)
                    is UiTransactionState.CardAlreadyTapped -> CardAlreadyTappedContent(uiState.cardUid)
                    is UiTransactionState.InsufficientBalance -> InsufficientBalanceContent(uiState.balance, uiState.required)
                    is UiTransactionState.UntrustedTimeError -> UntrustedTimeContent(uiState.message)
                }
            }

            // Bottom Simulator Bar for Demonstration
            SimulatorActionBar(onTestTap)
        }
    }
}

@Composable
fun TopTelemetryHeader(telemetry: TelemetryStatus, config: TerminalConfig?) {
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
                Text(text = "BUS: ${config?.busCode ?: "BUS-1049"} | ${config?.marriageCode ?: "MARRIAGE-OK"}", color = Color.Gray, fontSize = 10.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(text = if (telemetry.isOnline) "ONLINE" else "OFFLINE", color = if (telemetry.isOnline) Color(0xFF10B981) else Color(0xFFF59E0B))
                StatusBadge(text = "GPS: 3D (${telemetry.gpsSatellites} Sats)", color = Color(0xFF3B82F6))
                StatusBadge(text = "${telemetry.networkType} ${telemetry.signalDbm}dBm", color = Color(0xFF8B5CF6))
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

@Composable
fun IdleStateContent(config: TerminalConfig?, telemetry: TelemetryStatus) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = config?.operatorName ?: "TRANSJAKARTA", color = Color(0xFF38BDF8), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(text = "${config?.routeCode} : ${config?.routeName}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "TEMPELKAN KARTU / SCAN QRIS", color = Color.Yellow, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = "TARIF: Rp ${config?.baseFare ?: 3500}", color = Color.LightGray, fontSize = 16.sp)

        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoCard(title = "Hari Ini", value = "${telemetry.dailyTransactionCount} Tx")
            InfoCard(title = "Pending Sync", value = "${telemetry.pendingSyncCount}")
        }
    }
}

@Composable
fun InfoCard(title: String, value: String) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, color = Color.Gray, fontSize = 10.sp)
            Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
