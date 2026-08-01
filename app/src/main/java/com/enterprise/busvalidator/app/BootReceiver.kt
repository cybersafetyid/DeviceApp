package com.enterprise.busvalidator.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.enterprise.busvalidator.MainActivity
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("BootReceiver", "Received Boot Intent: $action")

        if (action == null || action !in bootActions) {
            return
        }

        val pendingResult = goAsync()
        Thread {
            try {
                startValidator(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun startValidator(context: Context) {
        Log.i("BootReceiver", "Starting Bus Validator automatically after boot...")
        if (startMainActivity(context)) return
        if (startMainActivityWithRoot(context)) return
        startHomeActivity(context)
    }

    private fun startMainActivity(context: Context): Boolean {
        return try {
            val startIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(startIntent)
            Log.i("BootReceiver", "MainActivity started from boot receiver.")
            true
        } catch (e: Exception) {
            Log.e("BootReceiver", "Direct MainActivity start failed: ${e.message}")
            false
        }
    }

    private fun startMainActivityWithRoot(context: Context): Boolean {
        return try {
            val component = "${context.packageName}/.MainActivity"
            val process = ProcessBuilder(
                "su",
                "-c",
                "am start -n $component --activity-clear-top --activity-single-top"
            ).start()

            if (!process.waitFor(ROOT_START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                Log.e("BootReceiver", "Root activity start timed out.")
                return false
            }

            val success = process.exitValue() == 0
            if (!success) {
                val errorText = process.errorStream.bufferedReader().use { it.readText() }.take(500)
                Log.e("BootReceiver", "Root activity start failed: $errorText")
            } else {
                Log.i("BootReceiver", "MainActivity started via root fallback.")
            }
            success
        } catch (e: Exception) {
            Log.e("BootReceiver", "Root fallback unavailable: ${e.message}")
            false
        }
    }

    private fun startHomeActivity(context: Context): Boolean {
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                setPackage(context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(homeIntent)
            Log.i("BootReceiver", "HOME fallback intent dispatched.")
            true
        } catch (e: Exception) {
            Log.e("BootReceiver", "HOME fallback failed: ${e.message}")
            false
        }
    }

    private companion object {
        const val ROOT_START_TIMEOUT_SECONDS = 10L
        val bootActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
