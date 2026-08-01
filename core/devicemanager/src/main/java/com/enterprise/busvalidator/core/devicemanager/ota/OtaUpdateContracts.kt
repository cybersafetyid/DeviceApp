package com.enterprise.busvalidator.core.devicemanager.ota

enum class OtaInstallMode {
    AUTO,
    ROOT,
    LENZ
}

enum class OtaUpdateStage {
    REQUEST_VALIDATION,
    DOWNLOAD,
    PACKAGE_VALIDATION,
    INSTALL,
    RESTART
}

data class OtaUpdateRequest(
    val downloadUrl: String,
    val expectedSha256: String,
    val targetVersionCode: Long? = null,
    val allowSameVersion: Boolean = false,
    val allowInsecureTransport: Boolean = false,
    val restartAfterInstall: Boolean = true,
    val installMode: OtaInstallMode = OtaInstallMode.AUTO,
    val maxDownloadBytes: Long = DEFAULT_MAX_DOWNLOAD_BYTES
) {
    companion object {
        const val DEFAULT_MAX_DOWNLOAD_BYTES: Long = 250L * 1024L * 1024L
    }
}

data class OtaVerifiedPackage(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    val signatureSha256Digests: Set<String>
)

sealed class OtaUpdateResult {
    data class Success(
        val packageName: String,
        val versionCode: Long,
        val sha256: String
    ) : OtaUpdateResult()

    data class Failed(
        val stage: OtaUpdateStage,
        val reason: String
    ) : OtaUpdateResult()
}
