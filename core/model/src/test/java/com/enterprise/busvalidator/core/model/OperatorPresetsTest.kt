package com.enterprise.busvalidator.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OperatorPresetsTest {

    @Test
    fun productionBaseUrls_matchLegacyNativeEndpointSource() {
        assertEquals("https://transpatriot.karcisku.id/c_bus/", OperatorPresets.BISKITA_BEKASI.baseUrl)
        assertEquals("https://transdepok.karcisku.id/c_bus/", OperatorPresets.BISKITA_DEPOK.baseUrl)
        assertEquals("https://kabbogor.karcisku.id/c_bus/", OperatorPresets.BISKITA_BOGOR.baseUrl)
        assertEquals("https://buscitrarayatgr.karcisku.id/c_bus/", OperatorPresets.CITRA_RAYA.baseUrl)
        assertEquals("https://buscitra.karcisku.id/c_bus/", OperatorPresets.CITRA_MAJA.baseUrl)
        assertEquals("https://suroboyo-bus.jaring.host/c_bus/", OperatorPresets.SURABAYA_WARA_WIRI.baseUrl)
        assertEquals("https://suroboyo-bus.jaring.host/c_bus/", OperatorPresets.SURABAYA_BUS.baseUrl)
    }

    @Test
    fun developmentBaseUrls_matchLegacyNativeEndpointSource() {
        assertEquals(
            "https://dev-buskita.karcisku.id/c_bus/",
            OperatorPresets.getPreset(OperatorSubService.BISKITA_BEKASI, ApiEnvironment.DEVELOPMENT).baseUrl
        )
        assertEquals(
            "https://dev-buskita.karcisku.id/c_bus/",
            OperatorPresets.getPreset(OperatorSubService.BISKITA_DEPOK, ApiEnvironment.DEVELOPMENT).baseUrl
        )
        assertEquals(
            "https://dev-buskita.karcisku.id/c_bus/",
            OperatorPresets.getPreset(OperatorSubService.BISKITA_BOGOR, ApiEnvironment.DEVELOPMENT).baseUrl
        )
        assertEquals(
            "https://afc-citraraya.net2software.net/c_bus/",
            OperatorPresets.getPreset(OperatorSubService.CITRA_RAYA, ApiEnvironment.DEVELOPMENT).baseUrl
        )
        assertEquals(
            "https://dev-buskita.karcisku.id/c_bus/",
            OperatorPresets.getPreset(OperatorSubService.CITRA_MAJA, ApiEnvironment.DEVELOPMENT).baseUrl
        )
        assertEquals(
            "https://dev-suroboyo.net2software.net/c_bus/",
            OperatorPresets.getPreset(OperatorSubService.SURABAYA_WARA_WIRI, ApiEnvironment.DEVELOPMENT).baseUrl
        )
        assertEquals(
            "https://dev-suroboyo.net2software.net/c_bus/",
            OperatorPresets.getPreset(OperatorSubService.SURABAYA_BUS, ApiEnvironment.DEVELOPMENT).baseUrl
        )
    }

    @Test
    fun mqttBrokerUrls_matchLegacyNativeEndpointSource() {
        assertEquals("tcp://mqtt.jsa2.host:12345", OperatorPresets.BISKITA_BEKASI.mqttBrokerConfig.brokerUrl)
        assertEquals("bv", OperatorPresets.BISKITA_BEKASI.mqttBrokerConfig.username)
        assertEquals("1sampai8", OperatorPresets.BISKITA_BEKASI.mqttBrokerConfig.password)
        assertEquals(
            "tcp://192.168.66.201:1883",
            OperatorPresets.getPreset(OperatorSubService.BISKITA_BEKASI, ApiEnvironment.DEVELOPMENT)
                .mqttBrokerConfig
                .brokerUrl
        )
    }

    @Test
    fun mqttTopics_matchLegacyRegionAndBusTopicShape() {
        val config = TerminalConfig(
            merchantId = "",
            terminalId = "",
            pinCode = "",
            processingCode = "",
            samId = "",
            marriageCode = "",
            operatorConfig = OperatorPresets.BISKITA_BEKASI,
            hardwareId = "2312001718000064",
            busCode = "BISKITA-01"
        )

        assertEquals("bekasi_BISKITA01", config.mqttTopicConfig.clientId(config.busCode))
        assertEquals("bv/bekasi/BISKITA-01", config.mqttTopicConfig.busTopic(config.busCode))
        assertEquals("notif/2312001718000064", config.mqttTopicConfig.notificationTopic(config.hardwareId))
        assertEquals("bus/command", config.mqttTopicConfig.commandTopic)
        assertEquals("bus/status", config.mqttTopicConfig.statusTopic)
    }
}
