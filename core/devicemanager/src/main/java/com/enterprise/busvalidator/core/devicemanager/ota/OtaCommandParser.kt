package com.enterprise.busvalidator.core.devicemanager.ota

import java.util.Locale

object OtaCommandParser {
    private val sha256Regex = Regex("^[A-Fa-f0-9]{64}$")
    private val jsonPairRegex = Regex("\"([^\"]+)\"\\s*:\\s*(\"(?:\\\\.|[^\"])*\"|true|false|-?\\d+)", RegexOption.IGNORE_CASE)

    fun parse(params: String): Result<OtaUpdateRequest> {
        val values = parseValues(params.trim())
        val url = values.firstValue("url", "downloadUrl", "apkUrl")
        val sha256 = values.firstValue("sha256", "sha")

        if (url.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("OTA update requires url/downloadUrl/apkUrl"))
        }
        if (sha256.isNullOrBlank() || !sha256Regex.matches(sha256)) {
            return Result.failure(IllegalArgumentException("OTA update requires a 64-character sha256"))
        }

        val targetVersionCode = values.firstValue("targetVersionCode", "versionCode")?.toLongOrNull()
        if (targetVersionCode != null && targetVersionCode <= 0L) {
            return Result.failure(IllegalArgumentException("targetVersionCode must be positive"))
        }

        val maxDownloadBytes = values.firstValue("maxDownloadBytes", "maxBytes")
            ?.toLongOrNull()
            ?: OtaUpdateRequest.DEFAULT_MAX_DOWNLOAD_BYTES
        if (maxDownloadBytes <= 0L) {
            return Result.failure(IllegalArgumentException("maxDownloadBytes must be positive"))
        }

        val installMode = values.firstValue("installMode", "mode")
            ?.uppercase(Locale.US)
            ?.let { runCatching { OtaInstallMode.valueOf(it) }.getOrNull() }
            ?: OtaInstallMode.AUTO

        return Result.success(
            OtaUpdateRequest(
                downloadUrl = url,
                expectedSha256 = sha256.lowercase(Locale.US),
                targetVersionCode = targetVersionCode,
                allowSameVersion = values.booleanValue("allowSameVersion") ?: false,
                allowInsecureTransport = values.booleanValue("allowInsecureTransport") ?: false,
                restartAfterInstall = values.booleanValue("restartAfterInstall", "restart") ?: true,
                installMode = installMode,
                maxDownloadBytes = maxDownloadBytes
            )
        )
    }

    private fun parseValues(params: String): Map<String, String> {
        if (params.isBlank()) return emptyMap()

        if (params.startsWith("{") && params.endsWith("}")) {
            return jsonPairRegex.findAll(params).associate { match ->
                match.groupValues[1] to unquoteJsonValue(match.groupValues[2])
            }
        }

        return params
            .split(';', '\n')
            .mapNotNull { token ->
                val separator = token.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = token.substring(0, separator).trim()
                val value = token.substring(separator + 1).trim()
                if (key.isEmpty() || value.isEmpty()) null else key to value
            }
            .toMap()
    }

    private fun Map<String, String>.firstValue(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
        }
    }

    private fun Map<String, String>.booleanValue(vararg keys: String): Boolean? {
        return firstValue(*keys)?.let { value ->
            when (value.lowercase(Locale.US)) {
                "true", "1", "yes", "y" -> true
                "false", "0", "no", "n" -> false
                else -> null
            }
        }
    }

    private fun unquoteJsonValue(value: String): String {
        if (!value.startsWith("\"") || !value.endsWith("\"")) return value
        return value
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}
