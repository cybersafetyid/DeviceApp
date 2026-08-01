package com.enterprise.busvalidator.core.devicemanager.ota

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.enterprise.busvalidator.core.security.EncryptedLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OtaPackageVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: EncryptedLogger
) {
    fun verify(apkFile: File, request: OtaUpdateRequest): Result<OtaVerifiedPackage> {
        return runCatching {
            require(apkFile.exists() && apkFile.isFile) { "OTA APK file does not exist" }

            val packageManager = context.packageManager
            val archiveInfo = packageManager.getArchivePackageInfo(apkFile.absolutePath)
                ?: error("OTA APK metadata cannot be read")
            val installedInfo = packageManager.getInstalledPackageInfo(context.packageName)

            require(archiveInfo.packageName == context.packageName) {
                "OTA APK package ${archiveInfo.packageName} does not match ${context.packageName}"
            }

            val archiveVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
            val installedVersionCode = PackageInfoCompat.getLongVersionCode(installedInfo)

            request.targetVersionCode?.let { expectedVersion ->
                require(archiveVersionCode == expectedVersion) {
                    "OTA APK versionCode $archiveVersionCode does not match requested $expectedVersion"
                }
            }

            if (request.allowSameVersion) {
                require(archiveVersionCode >= installedVersionCode) {
                    "OTA APK versionCode $archiveVersionCode is older than installed $installedVersionCode"
                }
            } else {
                require(archiveVersionCode > installedVersionCode) {
                    "OTA APK versionCode $archiveVersionCode is not newer than installed $installedVersionCode"
                }
            }

            val archiveSignatures = archiveInfo.signatureSha256Digests()
            val installedSignatures = installedInfo.signatureSha256Digests()
            require(archiveSignatures.isNotEmpty()) { "OTA APK has no readable signing certificate" }
            require(installedSignatures.isNotEmpty()) { "Installed APK has no readable signing certificate" }
            require(archiveSignatures == installedSignatures) {
                "OTA APK signing certificate does not match installed app"
            }

            val verifiedPackage = OtaVerifiedPackage(
                packageName = archiveInfo.packageName,
                versionCode = archiveVersionCode,
                versionName = archiveInfo.versionName,
                signatureSha256Digests = archiveSignatures
            )
            logger.log("OTA", "Verified OTA APK package=${verifiedPackage.packageName}, versionCode=${verifiedPackage.versionCode}")
            verifiedPackage
        }.onFailure { error ->
            logger.log("OTA", "Package validation failed: ${error.message}", isError = true)
        }
    }

    private fun PackageManager.getArchivePackageInfo(path: String): PackageInfo? {
        val flags = packageInfoFlags()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            getPackageArchiveInfo(path, flags)
        }
    }

    private fun PackageManager.getInstalledPackageInfo(packageName: String): PackageInfo {
        val flags = packageInfoFlags()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, flags)
        }
    }

    private fun packageInfoFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
    }

    private fun PackageInfo.signatureSha256Digests(): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            signatures
        }

        return signatures
            ?.map { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
            }
            ?.toSet()
            ?: emptySet()
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
