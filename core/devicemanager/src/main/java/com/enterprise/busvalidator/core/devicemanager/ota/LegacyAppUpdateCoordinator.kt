package com.enterprise.busvalidator.core.devicemanager.ota

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.enterprise.busvalidator.core.network.AppUpdateManifest
import com.enterprise.busvalidator.core.network.AppUpdateManifestApi
import com.enterprise.busvalidator.core.security.EncryptedLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class LegacyAppUpdateResult {
    data class NoNewVersion(val manifest: AppUpdateManifest) : LegacyAppUpdateResult()
    data class Blocked(val manifest: AppUpdateManifest, val reason: String) : LegacyAppUpdateResult()
    data class Installed(val manifest: AppUpdateManifest, val otaResult: OtaUpdateResult.Success) : LegacyAppUpdateResult()
    data class Failed(val reason: String, val manifest: AppUpdateManifest? = null) : LegacyAppUpdateResult()
}

@Singleton
class LegacyAppUpdateCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manifestApi: AppUpdateManifestApi,
    private val appUpdateManager: AppUpdateManager,
    private val logger: EncryptedLogger
) {
    suspend fun checkAndInstall(manifestUrl: String): LegacyAppUpdateResult {
        return checkAndInstallInternal { installedVersionName, installedVersionCode ->
            manifestApi.checkAppUpdate(
                manifestUrl = manifestUrl,
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode
            )
        }
    }

    suspend fun checkAndInstallFromBaseUrl(baseUrl: String): LegacyAppUpdateResult {
        return checkAndInstallInternal { installedVersionName, installedVersionCode ->
            manifestApi.checkAppUpdateFromBaseUrl(
                baseUrl = baseUrl,
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode
            )
        }
    }

    private suspend fun checkAndInstallInternal(
        loadManifest: suspend (installedVersionName: String, installedVersionCode: Long) -> AppUpdateManifest
    ): LegacyAppUpdateResult {
        return runCatching {
            val installedInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val installedVersionCode = PackageInfoCompat.getLongVersionCode(installedInfo)
            val installedVersionName = installedInfo.versionName ?: "0"
            val manifest = loadManifest(installedVersionName, installedVersionCode)

            if (!manifest.hasUpdate) {
                logger.log("Updater", "No new version in app update manifest")
                return@runCatching LegacyAppUpdateResult.NoNewVersion(manifest)
            }

            val apkUrl = manifest.apkUrl
            if (apkUrl.isNullOrBlank()) {
                return@runCatching LegacyAppUpdateResult.Blocked(manifest, "App update manifest missing apkUrl")
            }

            val sha256 = manifest.expectedSha256
            if (sha256.isNullOrBlank() || !SHA256_REGEX.matches(sha256)) {
                return@runCatching LegacyAppUpdateResult.Blocked(
                    manifest,
                    "App update manifest missing 64-character SHA-256"
                )
            }

            val otaResult = appUpdateManager.performOtaUpdate(
                OtaUpdateRequest(
                    downloadUrl = apkUrl,
                    expectedSha256 = sha256.lowercase(Locale.US),
                    targetVersionCode = manifest.versionCode,
                    allowSameVersion = true,
                    restartAfterInstall = true,
                    installMode = OtaInstallMode.AUTO
                )
            )

            when (otaResult) {
                is OtaUpdateResult.Success -> LegacyAppUpdateResult.Installed(manifest, otaResult)
                is OtaUpdateResult.Failed -> LegacyAppUpdateResult.Failed(
                    reason = "OTA failed at ${otaResult.stage}: ${otaResult.reason}",
                    manifest = manifest
                )
            }
        }.getOrElse { error ->
            LegacyAppUpdateResult.Failed(error.message ?: "App update check failed")
        }
    }

    private companion object {
        val SHA256_REGEX = Regex("^[A-Fa-f0-9]{64}$")
    }
}
