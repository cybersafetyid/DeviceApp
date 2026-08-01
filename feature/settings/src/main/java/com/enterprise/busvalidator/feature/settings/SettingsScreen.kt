package com.enterprise.busvalidator.feature.settings

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
import com.enterprise.busvalidator.core.model.VendorDeviceModel

@Composable
fun SettingsScreen(
    currentVendor: VendorDeviceModel,
    onVendorSelected: (VendorDeviceModel) -> Unit,
    onOpenDiagnosticClick: () -> Unit,
    onBackClick: () -> Unit
) {
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
            Text(text = "OPERATOR & VENDOR CONFIGURATION", color = Color(0xFF38BDF8), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onBackClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) {
                Text("KEMBALI")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Vendor Hardware Driver Override", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = "Dipilih: ${currentVendor.name}", color = Color.Yellow, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onVendorSelected(VendorDeviceModel.AUTO) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                        Text("AUTO DETECT")
                    }
                    Button(onClick = { onVendorSelected(VendorDeviceModel.TELPO) }) { Text("TELPO") }
                    Button(onClick = { onVendorSelected(VendorDeviceModel.Z90) }) { Text("Z90") }
                    Button(onClick = { onVendorSelected(VendorDeviceModel.E60Q) }) { Text("E60Q") }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onOpenDiagnosticClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
        ) {
            Text("BUKA SELF-DIAGNOSTIC HARDWARE", fontWeight = FontWeight.Bold)
        }
    }
}
