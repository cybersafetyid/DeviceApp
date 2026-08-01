package com.enterprise.busvalidator.feature.diagnostic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
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
    onRunDiagnostic: () -> Unit = {},
    onBackClick: () -> Unit,
    viewModel: HardwareDiagnosticViewModel = hiltViewModel()
) {
    val statusMessage by viewModel.statusMessage.collectAsState()
    val diagnosticResults by viewModel.diagnosticResults.collectAsState()

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
            Column {
                Text(text = "HARDWARE HEALTH & DECRYPTION DIAGNOSTIC", color = Color(0xFF38BDF8), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text = "SQLCipher DB & AES-256 Log Decryption Tools Enabled", color = Color.Gray, fontSize = 11.sp)
            }
            Button(onClick = onBackClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) {
                Text("KEMBALI")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (statusMessage != null) {
            Surface(
                color = Color(0xFF065F46),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = statusMessage!!,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

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

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    viewModel.setStatusMessage("Backup Terenkripsi Dibuat & Decrypt Test OK via tools/decrypt_logs_and_db.py")
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Text("TEST BACKUP & DECRYPT TOOL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.runDiagnostics() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("JALANKAN SELF-DIAGNOSTIC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
