package com.enterprise.busvalidator.core.common

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Enterprise Dynamic App Version Info Model.
 */
data class AppVersionInfo(
    val versionName: String,
    val versionCode: Long,
    val gitHash: String,
    val isDebug: Boolean,
    val formattedVersion: String
)

/**
 * Provider helper for retrieving runtime dynamic app version metadata.
 * Prevents static hardcoding in UI components.
 */
object AppVersionProvider {

    private const val DEFAULT_VERSION_NAME = "2.5.0-dynamic"
    private const val DEFAULT_VERSION_CODE = 142L
    private const val DEFAULT_GIT_HASH = "a1b2c3d"

    fun getAppVersion(context: Context?): AppVersionInfo {
        if (context == null) {
            return AppVersionInfo(
                versionName = DEFAULT_VERSION_NAME,
                versionCode = DEFAULT_VERSION_CODE,
                gitHash = DEFAULT_GIT_HASH,
                isDebug = true,
                formattedVersion = "v$DEFAULT_VERSION_NAME ($DEFAULT_GIT_HASH)"
            )
        }

        return try {
            val packageManager = context.packageManager
            val packageName = context.packageName
            val pInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }

            val vName = pInfo.versionName ?: DEFAULT_VERSION_NAME
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }

            val gitHash = extractGitHash(vName)

            AppVersionInfo(
                versionName = vName,
                versionCode = vCode,
                gitHash = gitHash,
                isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                formattedVersion = "v$vName"
            )
        } catch (e: Exception) {
            AppVersionInfo(
                versionName = DEFAULT_VERSION_NAME,
                versionCode = DEFAULT_VERSION_CODE,
                gitHash = DEFAULT_GIT_HASH,
                isDebug = true,
                formattedVersion = "v$DEFAULT_VERSION_NAME"
            )
        }
    }

    private fun extractGitHash(versionName: String): String {
        return if (versionName.contains(".")) {
            versionName.substringAfterLast(".", "a1b2c3d")
        } else {
            DEFAULT_GIT_HASH
        }
    }
}
