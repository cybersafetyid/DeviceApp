package com.enterprise.busvalidator.core.database

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise Database & Log Encrypted Backup Engine.
 * Supports scheduled backup, AES-256-GCM encryption of database dumps, and decryption tools.
 */
@Singleton
class DatabaseBackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: SecretKey by lazy {
        val keyBytes = "EnterpriseBusValidatorAESKey2026".toByteArray(StandardCharsets.UTF_8)
        SecretKeySpec(keyBytes, "AES")
    }

    private val backupDir: File by lazy {
        File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
    }

    /**
     * Performs atomic encrypted backup of the room database file.
     */
    fun performDatabaseBackup(): File? {
        val dbFile = context.getDatabasePath("bus_validator_encrypted.db")
        if (!dbFile.exists()) return null

        val timestamp = System.currentTimeMillis()
        val backupFile = File(backupDir, "db_backup_$timestamp.db.enc")

        return try {
            val dbBytes = FileInputStream(dbFile).use { it.readBytes() }
            val encryptedBytes = encryptAesGcm(dbBytes)
            FileOutputStream(backupFile).use { fos ->
                fos.write(encryptedBytes)
            }
            backupFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decrypts an AES-256-GCM encrypted database or log file byte array.
     */
    fun decryptBackupFile(inputFile: File): ByteArray? {
        return try {
            val encryptedBytes = FileInputStream(inputFile).use { it.readBytes() }
            decryptAesGcm(encryptedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decrypts single log line written by EncryptedLogger.
     */
    fun decryptLogLine(base64Line: String): String? {
        return try {
            val encryptedBytes = Base64.decode(base64Line.trim(), Base64.NO_WRAP)
            val decrypted = decryptAesGcm(encryptedBytes)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun encryptAesGcm(plainText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec)
        val cipherText = cipher.doFinal(plainText)
        return iv + cipherText
    }

    private fun decryptAesGcm(cipherTextWithIv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = cipherTextWithIv.copyOfRange(0, 12)
        val cipherText = cipherTextWithIv.copyOfRange(12, cipherTextWithIv.size)
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec)
        return cipher.doFinal(cipherText)
    }

    fun getBackupFiles(): List<File> {
        return backupDir.listFiles()?.toList() ?: emptyList()
    }
}
