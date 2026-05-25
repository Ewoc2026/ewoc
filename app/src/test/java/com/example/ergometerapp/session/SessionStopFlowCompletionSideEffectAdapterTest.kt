package com.example.ergometerapp.session

import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SessionStopFlowCompletionSideEffectAdapterTest {

    @Test
    fun completeToSummaryAppliesUiTransitionBeforeCallback() {
        val uiState = AppUiState().apply {
            screen.value = AppScreen.STOPPING
            summary.value = null
        }
        val expectedSummary = sampleSummary()
        val transitionOrder = mutableListOf<String>()
        var callbackScreen: AppScreen? = null
        var callbackSummary: SessionSummary? = null
        var callbackReason: String? = null
        val adapter = SessionStopFlowCompletionSideEffectAdapter(
            uiState = uiState,
            finalizeSessionSummary = {
                transitionOrder += "finalizeSessionSummary"
            },
            summaryProvider = {
                transitionOrder += "summaryProvider"
                expectedSummary
            },
            allowScreenOff = {
                transitionOrder += "allowScreenOff"
            },
            onAfterSummaryTransition = { reason ->
                transitionOrder += "callback"
                callbackReason = reason
                callbackScreen = uiState.screen.value
                callbackSummary = uiState.summary.value
            },
        )

        adapter.completeToSummary(reason = "stopFlowTimeout")

        assertEquals(
            listOf("finalizeSessionSummary", "summaryProvider", "allowScreenOff", "callback"),
            transitionOrder,
        )
        assertSame(expectedSummary, uiState.summary.value)
        assertEquals(AppScreen.SUMMARY, uiState.screen.value)
        assertEquals(AppScreen.SUMMARY, callbackScreen)
        assertSame(expectedSummary, callbackSummary)
        assertEquals("stopFlowTimeout", callbackReason)
    }

    @Test
    fun completeToSummaryAllowsMissingSummaryPayload() {
        val uiState = AppUiState().apply {
            screen.value = AppScreen.STOPPING
            summary.value = sampleSummary()
        }
        var callbackCount = 0
        var finalizeCalls = 0
        val adapter = SessionStopFlowCompletionSideEffectAdapter(
            uiState = uiState,
            finalizeSessionSummary = { finalizeCalls += 1 },
            summaryProvider = { null },
            allowScreenOff = {},
            onAfterSummaryTransition = { callbackCount += 1 },
        )

        adapter.completeToSummary(reason = "ack")

        assertNull(uiState.summary.value)
        assertEquals(AppScreen.SUMMARY, uiState.screen.value)
        assertEquals(1, finalizeCalls)
        assertEquals(1, callbackCount)
    }

    private fun sampleSummary(): SessionSummary {
        return SessionSummary(
            startTimestampMillis = 1_700_000_000_000L,
            stopTimestampMillis = 1_700_000_090_000L,
            durationSeconds = 90,
            actualTss = 12.5,
            avgPower = 210,
            maxPower = 320,
            avgCadence = 87,
            maxCadence = 102,
            avgHeartRate = 154,
            maxHeartRate = 168,
            distanceMeters = 1_250,
            totalEnergyKcal = 48,
        )
    }
}
