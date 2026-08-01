package com.enterprise.busvalidator.feature.validator

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
import androidx.compose.ui.platform.LocalContext
import com.enterprise.busvalidator.core.common.AppVersionProvider
import com.enterprise.busvalidator.core.devicemanager.InitStep

@Composable
fun InteractiveInitializationSplashScreen(
    initStep: InitStep
) {
    val context = LocalContext.current
    val appVersion = remember { AppVersionProvider.getAppVersion(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "BUS VALIDATOR SYSTEM",
                color = Color(0xFF38BDF8),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Enterprise Multi-Operator Platform ${appVersion.formattedVersion}",
                color = Color.Gray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            when (initStep) {
                is InitStep.Progress -> {
                    CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = initStep.stepName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { initStep.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(8.dp),
                        color = Color(0xFF38BDF8),
                        trackColor = Color(0xFF1E293B)
                    )
                    Text(
                        text = "${initStep.progressPercent}%",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                is InitStep.Failed -> {
                    Surface(
                        color = Color(0xFF7F1D1D),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "INITIALIZATION ERROR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = initStep.errorReason, color = Color.Yellow, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "AUTO RECOVERY ACTIVE", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                is InitStep.Completed -> {
                    Text(text = "✓ ALL MODULES READY!", color = Color(0xFF10B981), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
