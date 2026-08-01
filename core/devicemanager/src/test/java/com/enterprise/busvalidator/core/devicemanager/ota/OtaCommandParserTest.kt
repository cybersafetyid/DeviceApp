package com.enterprise.busvalidator.core.devicemanager.ota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaCommandParserTest {
    @Test
    fun parseKeyValuePayload_returnsValidatedRequest() {
        val result = OtaCommandParser.parse(
            "url=https://updates.example.com/app.apk;" +
                "sha256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef;" +
                "versionCode=204;" +
                "restart=false;" +
                "installMode=root"
        )

        assertTrue(result.isSuccess)
        val request = result.getOrThrow()
        assertEquals("https://updates.example.com/app.apk", request.downloadUrl)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", request.expectedSha256)
        assertEquals(204L, request.targetVersionCode)
        assertFalse(request.restartAfterInstall)
        assertEquals(OtaInstallMode.ROOT, request.installMode)
    }

    @Test
    fun parseJsonPayload_supportsAliases() {
        val result = OtaCommandParser.parse(
            "{" +
                "\"apkUrl\":\"https://updates.example.com/app.apk\"," +
                "\"sha\":\"abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd\"," +
                "\"allowSameVersion\":true," +
                "\"maxBytes\":4096" +
                "}"
        )

        assertTrue(result.isSuccess)
        val request = result.getOrThrow()
        assertEquals("https://updates.example.com/app.apk", request.downloadUrl)
        assertEquals("abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd", request.expectedSha256)
        assertTrue(request.allowSameVersion)
        assertEquals(4096L, request.maxDownloadBytes)
    }

    @Test
    fun parseMissingSha256_failsFast() {
        val result = OtaCommandParser.parse("url=https://updates.example.com/app.apk")

        assertTrue(result.isFailure)
    }

    @Test
    fun parseInvalidInstallMode_defaultsToAuto() {
        val result = OtaCommandParser.parse(
            "url=https://updates.example.com/app.apk;" +
                "sha256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef;" +
                "installMode=unsupported"
        )

        assertTrue(result.isSuccess)
        assertEquals(OtaInstallMode.AUTO, result.getOrThrow().installMode)
    }
}
