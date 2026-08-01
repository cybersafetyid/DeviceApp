package com.enterprise.busvalidator.core.devicemanager.ota

import android.content.Context
import com.enterprise.busvalidator.core.security.EncryptedLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OtaApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: EncryptedLogger
) {
    suspend fun download(request: OtaUpdateRequest): Result<DownloadedApk> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = URI(request.downloadUrl)
            val scheme = uri.scheme?.lowercase(Locale.US)
            require(scheme == "https" || (scheme == "http" && request.allowInsecureTransport)) {
                "OTA download URL must use HTTPS unless allowInsecureTransport=true"
            }

            val otaDirectory = File(context.getExternalFilesDir(null) ?: context.cacheDir, OTA_DIRECTORY_NAME)
                .apply { if (!exists()) mkdirs() }
            cleanupOldDownloads(otaDirectory)

            val tempFile = File(otaDirectory, "update-${System.currentTimeMillis()}.apk.part")
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesWritten = 0L

            val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream")
            }

            try {
                val responseCode = connection.responseCode
                require(responseCode in 200..299) { "OTA download failed with HTTP $responseCode" }

                val contentLength = connection.contentLengthLong
                require(contentLength <= 0L || contentLength <= request.maxDownloadBytes) {
                    "OTA download exceeds maxDownloadBytes before transfer"
                }

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break

                            bytesWritten += read.toLong()
                            require(bytesWritten <= request.maxDownloadBytes) {
                                "OTA download exceeds maxDownloadBytes during transfer"
                            }

                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            require(bytesWritten > 0L) { "OTA download returned an empty APK" }

            val actualSha256 = digest.digest().toHex()
            require(actualSha256.equals(request.expectedSha256, ignoreCase = true)) {
                "OTA SHA-256 mismatch"
            }

            val apkFile = File(otaDirectory, "busvalidator-$actualSha256.apk")
            if (apkFile.exists()) apkFile.delete()
            require(tempFile.renameTo(apkFile)) { "Failed to finalize OTA APK file" }
            apkFile.setReadable(true, false)

            logger.log("OTA", "Downloaded OTA APK (${bytesWritten} bytes, sha256=$actualSha256)")
            DownloadedApk(apkFile, actualSha256, bytesWritten)
        }.onFailure { error ->
            File(context.getExternalFilesDir(null) ?: context.cacheDir, OTA_DIRECTORY_NAME)
                .listFiles()
                ?.filter { it.isFile && it.name.endsWith(".part") }
                ?.forEach { file -> runCatching { file.delete() } }
            logger.log("OTA", "Download failed: ${error.message}", isError = true)
        }
    }

    private fun cleanupOldDownloads(directory: File) {
        directory.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".apk") || it.name.endsWith(".part")) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_RETAINED_DOWNLOADS)
            ?.forEach { file -> runCatching { file.delete() } }
    }

    data class DownloadedApk(
        val file: File,
        val sha256: String,
        val bytesWritten: Long
    )

    private companion object {
        const val OTA_DIRECTORY_NAME = "ota"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_RETAINED_DOWNLOADS = 4
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
