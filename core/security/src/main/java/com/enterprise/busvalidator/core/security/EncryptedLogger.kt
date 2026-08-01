package com.enterprise.busvalidator.core.security

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-performance AES-256-GCM Encrypted Logger.
 * Captures process logs, hardware I/O traces, errors, and security events.
 */
@Singleton
class EncryptedLogger @Inject constructor(
    private val context: Context
) {
    private val logDirectory: File by lazy {
        File(context.filesDir, "encrypted_logs").apply { if (!exists()) mkdirs() }
    }

    private val masterKey: SecretKey by lazy {
        // Master key derivation (256-bit)
        val keyBytes = "EnterpriseBusValidatorAESKey2026".toByteArray(StandardCharsets.UTF_8)
        SecretKeySpec(keyBytes, "AES")
    }

    @Synchronized
    fun log(tag: String, message: String, isError: Boolean = false) {
        val timestamp = System.currentTimeMillis()
        val rawEntry = "[$timestamp] [$tag] ${if (isError) "ERROR" else "INFO"}: $message\n"
        
        try {
            val encryptedBytes = encryptAesGcm(rawEntry.toByteArray(StandardCharsets.UTF_8))
            val currentLogFile = File(logDirectory, "log_${timestamp / 86400000}.enc")
            
            FileOutputStream(currentLogFile, true).use { fos ->
                val line = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP) + "\n"
                fos.write(line.toByteArray(StandardCharsets.UTF_8))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun encryptAesGcm(plainText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec)
        val cipherText = cipher.doFinal(plainText)
        return iv + cipherText
    }

    fun getLogFiles(): List<File> {
        return logDirectory.listFiles()?.toList() ?: emptyList()
    }
}
