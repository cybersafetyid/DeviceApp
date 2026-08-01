package com.enterprise.busvalidator.core.devicemanager.ota

import android.content.Context
import com.enterprise.busvalidator.core.devicemanager.LenzDeviceManager
import com.enterprise.busvalidator.core.security.EncryptedLogger
import com.enterprise.busvalidator.core.security.SuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: OtaApkDownloader,
    private val packageVerifier: OtaPackageVerifier,
    private val suManager: SuManager,
    private val lenzDeviceManager: LenzDeviceManager,
    private val logger: EncryptedLogger
) {
    private val updateMutex = Mutex()

    suspend fun performOtaUpdate(request: OtaUpdateRequest): OtaUpdateResult {
        if (!updateMutex.tryLock()) {
            return OtaUpdateResult.Failed(OtaUpdateStage.REQUEST_VALIDATION, "Another OTA update is already running")
        }

        return try {
            logger.log("OTA", "Starting OTA update from ${request.downloadUrl.redactedUrl()}")

            val downloadedApk = downloader.download(request).getOrElse { error ->
                return OtaUpdateResult.Failed(OtaUpdateStage.DOWNLOAD, error.message ?: "Download failed")
            }

            val verifiedPackage = packageVerifier.verify(downloadedApk.file, request).getOrElse { error ->
                return OtaUpdateResult.Failed(OtaUpdateStage.PACKAGE_VALIDATION, error.message ?: "Package validation failed")
            }

            val installed = installApk(downloadedApk.file.absolutePath, request)
            if (!installed) {
                return OtaUpdateResult.Failed(OtaUpdateStage.INSTALL, "Silent APK install command failed")
            }

            logger.log("OTA", "OTA install dispatched successfully for versionCode=${verifiedPackage.versionCode}")
            OtaUpdateResult.Success(
                packageName = verifiedPackage.packageName,
                versionCode = verifiedPackage.versionCode,
                sha256 = downloadedApk.sha256
            )
        } finally {
            updateMutex.unlock()
        }
    }

    private fun installApk(apkPath: String, request: OtaUpdateRequest): Boolean {
        return when (request.installMode) {
            OtaInstallMode.ROOT -> installWithRoot(apkPath, request.restartAfterInstall)
            OtaInstallMode.LENZ -> installWithLenz(apkPath, request.restartAfterInstall)
            OtaInstallMode.AUTO -> installWithRoot(apkPath, request.restartAfterInstall) ||
                installWithLenz(apkPath, request.restartAfterInstall)
        }
    }

    private fun installWithRoot(apkPath: String, restartAfterInstall: Boolean): Boolean {
        return suManager.installApkSilently(
            apkFilePath = apkPath,
            packageName = context.packageName,
            launcherActivity = ".MainActivity",
            restartAfterInstall = restartAfterInstall
        )
    }

    private fun installWithLenz(apkPath: String, restartAfterInstall: Boolean): Boolean {
        val installDispatched = lenzDeviceManager.installApp(apkPath)
        if (installDispatched && restartAfterInstall) {
            suManager.restartApp(context.packageName, ".MainActivity")
        }
        return installDispatched
    }
}

private fun String.redactedUrl(): String {
    return runCatching {
        val uri = URI(this)
        URI(uri.scheme, uri.authority, uri.path, null, null).toString()
    }.getOrDefault("[invalid-url]")
}
