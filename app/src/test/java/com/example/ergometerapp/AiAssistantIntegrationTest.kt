package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Test

class AiAssistantIntegrationTest {

    @Test
    fun realAiAssistantStatePort_readsLatestProviderValues() {
        var ftmsReady = false
        var ftmsReachable: Boolean? = null
        var hrConnected = false
        var hrReachable: Boolean? = null
        val statePort = RealAiAssistantStatePort(
            ftmsReadyProvider = { ftmsReady },
            ftmsReachableProvider = { ftmsReachable },
            hrConnectedProvider = { hrConnected },
            hrReachableProvider = { hrReachable },
        )

        ftmsReady = true
        ftmsReachable = false
        hrConnected = true
        hrReachable = true

        assertEquals(true, statePort.ftmsReady)
        assertEquals(false, statePort.ftmsReachable)
        assertEquals(true, statePort.hrConnected)
        assertEquals(true, statePort.hrReachable)
    }

    @Test
    fun realAiAssistantStatePort_preservesUnknownReachability() {
        val statePort = RealAiAssistantStatePort(
            ftmsReadyProvider = { false },
            ftmsReachableProvider = { null },
            hrConnectedProvider = { false },
            hrReachableProvider = { null },
        )

        assertEquals(null, statePort.ftmsReachable)
        assertEquals(null, statePort.hrReachable)
    }
}
