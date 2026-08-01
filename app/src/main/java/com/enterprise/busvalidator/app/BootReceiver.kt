package com.enterprise.busvalidator.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.enterprise.busvalidator.MainActivity

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("BootReceiver", "Received Boot Intent: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Starting Bus Validator Main Activity...")
            val startIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            try {
                context.startActivity(startIntent)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start MainActivity on boot: ${e.message}")
            }
        }
    }
}
