package com.enterprise.busvalidator.core.network

import com.enterprise.busvalidator.core.model.OperatorPresets
import com.enterprise.busvalidator.core.model.TapMode
import com.enterprise.busvalidator.core.model.TerminalConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyApiResponseParserTest {

    @Test
    fun toTerminalConfig_mapsNestedLegacyConfigAndFareResponse() {
        val configRoot = LegacyApiResponseParser.parseObject(
            """
            {
              "status": true,
              "servertime": "2026-08-01 10:00:00",
              "config": {
                "mid": "MID-001",
                "tid": "TID-001",
                "pincode": "112233",
                "processing_code": "000001",
                "sam_id": "SAM-01",
	                "marriage_code": "MC-01",
	                "tap_mode": "TAP_IN_ONLY",
	                "terminal": "B-001",
	                "version": "21",
                "radius": "150.0f",
                "direction": "2",
                "doubleTap": "false",
                "locationLimit": "30",
                "issuers": [
                  {
                    "BNI": {
                      "mid": "000100410000496",
                      "tid": "41049601",
                      "slot": "2",
                      "status": "true",
                      "marriages": [
                        {
                          "code": "29F04FAB9DDBFEA616A702F46ACEA8CA",
                          "samId": "1100000001292199"
                        }
                      ]
                    },
                    "BRI": {
                      "mid": "000001999314563",
                      "tid": "10531875",
                      "slot": "7",
                      "status": "true",
                      "processingCode": "808174"
                    },
                    "QRIS": {
                      "mid": "0200000000000001",
                      "tid": "02000001",
                      "issuer": "NOBU",
                      "status": "false",
                      "checktime": 5,
                      "validtime": 1200
                    }
                  }
                ]
	              }
	            }
            """.trimIndent()
        )!!
        val terminalRoot = LegacyApiResponseParser.parseObject(
            """{"status":true,"tid":"TID-TERM","statusQris":true,"rawQRIS":"RAW-QR","c_city":"11","n_city":"DEPOK","cycle_time":60}"""
        )!!
        val fareRoot = LegacyApiResponseParser.parseObject(
            """
            {
              "status": true,
              "fare": "4500",
              "route_code": "R-01",
              "route": "Terminal - Stasiun",
              "trip": "7",
              "operational": "OR-0118",
              "start": "05:00",
              "end": "22:00",
              "start_lat": "-6.1",
              "start_lng": "107.1",
              "end_lat": "-6.2",
              "end_lng": "107.2",
              "filename": "fare.json",
              "filecard": "https://example.com/fare.json"
            }
            """.trimIndent()
        )

        val config = LegacyApiResponseParser.toTerminalConfig(
            operatorConfig = OperatorPresets.BISKITA_BEKASI,
            currentRuntime = TerminalConfig(
                merchantId = "",
                terminalId = "",
                pinCode = "",
                processingCode = "",
                samId = "",
                marriageCode = "",
                hardwareId = "HW-001",
                operatorConfig = OperatorPresets.BISKITA_BEKASI
            ),
            configRoot = configRoot,
            terminalRoot = terminalRoot,
            fareRoot = fareRoot
        )

        assertEquals("MID-001", config.merchantId)
        assertEquals("TID-TERM", config.terminalId)
        assertEquals("HW-001", config.hardwareId)
        assertEquals(TapMode.TAP_IN_ONLY, config.tapMode)
        assertEquals("R-01", config.routeCode)
        assertEquals("Terminal - Stasiun", config.routeName)
        assertEquals(4500L, config.baseFare)
        assertEquals("B-001", config.busCode)
        assertEquals("7", config.trip)
        assertEquals("OR-0118", config.operationalCode)
        assertEquals("05:00", config.operationalStart)
        assertEquals("22:00", config.operationalEnd)
        assertEquals(-6.1, config.startLatitude ?: 0.0, 0.0)
        assertEquals(107.2, config.endLongitude ?: 0.0, 0.0)
        assertTrue(config.qrisEnabled)
        assertEquals("RAW-QR", config.rawQris)
        assertEquals("21", config.configVersion)
        assertEquals("fare.json", config.fareFileName)
        assertEquals("https://example.com/fare.json", config.fareFileUrl)
        assertEquals("2026-08-01 10:00:00", config.serverTime)
        assertEquals(150.0, config.validationRadiusMeters ?: 0.0, 0.0)
        assertEquals("2", config.direction)
        assertFalse(config.doubleTapAllowed ?: true)
        assertEquals(30, config.locationLimitSeconds)
        assertEquals("11", config.cityCode)
        assertEquals("DEPOK", config.cityName)
        assertEquals(60, config.cycleTimeSeconds)
        assertEquals("000100410000496", config.cardIssuers["BNI"]?.merchantId)
        assertEquals("29F04FAB9DDBFEA616A702F46ACEA8CA", config.cardIssuers["BNI"]?.marriageCodes?.first())
        assertEquals("808174", config.cardIssuers["BRI"]?.processingCode)
        assertEquals("02000001", config.cardIssuers["QRIS"]?.terminalId)
        assertEquals(1200, config.cardIssuers["QRIS"]?.validTimeSeconds)
    }

    @Test
    fun parseModernBatchAck_acceptsCurrentSyncContract() {
        val root = LegacyApiResponseParser.parseObject(
            """
            {
              "acceptedTransactionIds": ["tx-1", "tx-2"],
              "backendLastCounter": 2
            }
            """.trimIndent()
        )!!

        val result = LegacyApiResponseParser.parseModernBatchAck(root)!!

        assertEquals(setOf("tx-1", "tx-2"), result.acceptedTransactionIds)
        assertEquals(2, result.backendLastCounter)
        assertFalse(result.hasConflict)
    }

    @Test
    fun appUpdateResponse_mapsLegacyUpdaterManifestShape() {
        val root = LegacyApiResponseParser.parseObject(
            """
            {
              "status": true,
              "data": {
                "versionCode": "205",
                "apkUrl": "https://updates.example.com/app.apk",
                "versionName": "3.0.0",
                "status_fare": "t",
                "versionNotes": ["Fix sync", "Update fare"],
                "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              }
            }
            """.trimIndent()
        )!!
        val data = LegacyApiResponseParser.objectOrNull(root["data"])!!

        assertTrue(LegacyApiResponseParser.isSuccess(root))
        assertEquals(205L, LegacyApiResponseParser.long(data, "versionCode"))
        assertEquals("https://updates.example.com/app.apk", LegacyApiResponseParser.string(data, "apkUrl"))
        assertEquals(listOf("Fix sync", "Update fare"), LegacyApiResponseParser.stringArray(data, "versionNotes"))
        assertEquals("t", LegacyApiResponseParser.string(data, "status_fare"))
    }

    @Test
    fun getVersionEndpoint_usesLegacyPathName() {
        assertEquals("get_version", LegacyTransitEndpoints.GET_VERSION)
    }
}
