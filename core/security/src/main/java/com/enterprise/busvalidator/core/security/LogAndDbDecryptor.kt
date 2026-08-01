package com.enterprise.busvalidator.core.security

import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Standalone Kotlin JVM Decryption Utility for Logs & Database Backups.
 */
object LogAndDbDecryptor {

    private const val MASTER_KEY_STRING = "EnterpriseBusValidatorAESKey2026"
    const val SQLCIPHER_PASSPHRASE = "EnterpriseBusValidatorSQLCipherPassphrase2026"

    private val masterKey: SecretKey by lazy {
        SecretKeySpec(MASTER_KEY_STRING.toByteArray(StandardCharsets.UTF_8), "AES")
    }

    /**
     * Decrypts an encrypted Base64 log line written by EncryptedLogger.
     */
    fun decryptLogLine(base64Line: String): String {
        val encryptedBytes = Base64.decode(base64Line.trim(), Base64.NO_WRAP)
        val decryptedBytes = decryptAesGcm(encryptedBytes)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    /**
     * Decrypts an encrypted log file line-by-line into cleartext string list.
     */
    fun decryptLogFile(inputFile: File): List<String> {
        val lines = inputFile.readLines(StandardCharsets.UTF_8)
        return lines.mapNotNull { line ->
            if (line.isBlank()) null
            else runCatching { decryptLogLine(line) }.getOrElse { "[DECRYPTION ERROR: ${it.message}]" }
        }
    }

    /**
     * Decrypts an AES-256-GCM binary database backup file (.db.enc).
     */
    fun decryptDatabaseBackup(inputFile: File, outputFile: File) {
        val encryptedBytes = inputFile.readBytes()
        val decryptedBytes = decryptAesGcm(encryptedBytes)
        outputFile.writeBytes(decryptedBytes)
    }

    private fun decryptAesGcm(cipherTextWithIv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = cipherTextWithIv.copyOfRange(0, 12)
        val cipherText = cipherTextWithIv.copyOfRange(12, cipherTextWithIv.size)
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec)
        return cipher.doFinal(cipherText)
    }
}
