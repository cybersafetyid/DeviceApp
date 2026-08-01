package com.enterprise.busvalidator.feature.diagnostic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DiagnosticItem(
    val name: String,
    val isPassed: Boolean,
    val details: String
)

@Composable
fun HardwareDiagnosticScreen(
    onRunDiagnostic: () -> Unit,
    onBackClick: () -> Unit
) {
    val diagnosticResults = remember {
        listOf(
            DiagnosticItem("NFC Antenna Reader", true, "ISO 14443-4 APDU OK"),
            DiagnosticItem("SAM Module Slot 1", true, "ATR Answer To Reset Valid"),
            DiagnosticItem("RS232 Serial Port", true, "/dev/ttyS1 115200 Baud OK"),
            DiagnosticItem("Barcode / QR Scanner", true, "Frame Reader Engine OK"),
            DiagnosticItem("Audio Synthesizer", true, "SoundPool Audio Speaker OK"),
            DiagnosticItem("Onboard Board LED", true, "Green/Red/Blue Drivers OK"),
            DiagnosticItem("GPS Location Module", true, "3D Fix | 11 Satellites Lock"),
            DiagnosticItem("MQTT TLS Telemetry", true, "Ping RTT 45ms"),
            DiagnosticItem("Encrypted SQLCipher DB", true, "AES-256 Storage I/O OK")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "HARDWARE HEALTH DIAGNOSTIC", color = Color(0xFF38BDF8), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onBackClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) {
                Text("KEMBALI")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(diagnosticResults) { item ->
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = item.details, color = Color.Gray, fontSize = 12.sp)
                        }
                        Surface(
                            color = if (item.isPassed) Color(0xFF10B981) else Color(0xFFEF4444),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (item.isPassed) "PASS" else "FAIL",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRunDiagnostic,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
            Text("JALANKAN SELF-DIAGNOSTIC ULANG", fontWeight = FontWeight.Bold)
        }
    }
}
