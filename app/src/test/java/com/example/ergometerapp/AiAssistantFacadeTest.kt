package com.example.ergometerapp

import com.example.ergometerapp.ai.AiPhase
import com.example.ergometerapp.ai.AiPresentationMessage
import com.example.ergometerapp.ai.AiPresentationSurface
import com.example.ergometerapp.ai.AiQualityClass
import com.example.ergometerapp.ai.AiReachability
import com.example.ergometerapp.ai.AiRecommendationType
import org.junit.Assert.assertEquals
import org.junit.Test

class AiAssistantFacadeTest {

    @Test
    fun trainerReachability_prefersReadyFtmsState() {
        val support = support(
            statePort = FakeAiAssistantStatePort(
                ftmsReady = true,
                ftmsReachable = false,
            ),
        )

        assertEquals(AiReachability.REACHABLE, support.trainerReachability())
    }

    @Test
    fun heartRateReachability_prefersConnectedState() {
        val support = support(
            statePort = FakeAiAssistantStatePort(
                hrConnected = true,
                hrReachable = false,
            ),
        )

        assertEquals(AiReachability.REACHABLE, support.heartRateReachability())
    }

    @Test
    fun formatMessage_usesCadenceThresholdFallbackWhenTargetMissing() {
        val support = support()

        val result = support.formatMessage(
            message(
                templateKey = "ai.session.cadence_increase_slightly",
                templateArgs = mapOf(
                    "cadence_rpm" to "72",
                    "cadence_threshold_rpm" to "85",
                ),
            ),
        )

        assertEquals("cadence_up:72:85", result)
    }

    @Test
    fun formatMessage_returnsFallbackForUnknownTemplateKey() {
        val support = support()

        val result = support.formatMessage(
            message(templateKey = "ai.unknown"),
        )

        assertEquals("fallback", result)
    }

    @Test
    fun isErrorType_marksSafetyAndConnectivityAsErrors() {
        val support = support()

        assertEquals(true, support.isErrorType(AiRecommendationType.SAFETY))
        assertEquals(true, support.isErrorType(AiRecommendationType.CONNECTIVITY))
        assertEquals(false, support.isErrorType(AiRecommendationType.PACING))
    }

    @Test
    fun phaseForScreen_mapsTransientAndVisibleScreens() {
        val support = support()

        assertEquals(AiPhase.MENU, support.phaseForScreen(AppScreen.MENU))
        assertEquals(AiPhase.MENU, support.phaseForScreen(AppScreen.EWO_EDITOR))
        assertEquals(AiPhase.SESSION, support.phaseForScreen(AppScreen.SESSION))
        assertEquals(AiPhase.SUMMARY, support.phaseForScreen(AppScreen.SUMMARY))
        assertEquals(AiPhase.SUMMARY, support.phaseForScreen(AppScreen.STOPPING))
        assertEquals(null, support.phaseForScreen(AppScreen.CONNECTING))
    }

    @Test
    fun refresh_usesResolvedCurrentScreenPhaseWhenForcePhaseMissing() {
        val record = mutableListOf<String>()
        val facade = facade(
            currentScreen = { AppScreen.SESSION },
            refreshCoordinator = { phase, force, nowMillis ->
                record += "refresh:$phase:$force:$nowMillis"
            },
        )

        facade.refresh(force = true, nowMillis = 123L)

        assertEquals(listOf("refresh:SESSION:true:123"), record)
    }

    @Test
    fun refresh_forwardsExplicitPhaseWithoutResolvingScreen() {
        val record = mutableListOf<String>()
        val facade = facade(
            currentScreen = { AppScreen.CONNECTING },
            refreshCoordinator = { phase, force, nowMillis ->
                record += "refresh:$phase:$force:$nowMillis"
            },
        )

        facade.refresh(forcePhase = AiPhase.MENU, force = true, nowMillis = 456L)

        assertEquals(listOf("refresh:MENU:true:456"), record)
    }

    private fun support(
        statePort: FakeAiAssistantStatePort = FakeAiAssistantStatePort(),
    ): AiAssistantSupport {
        return AiAssistantSupport(
            statePort = statePort,
            menuConnectivityCheckTrainerMessage = { "menu_connectivity_check" },
            menuReadinessReduceIntensityMessage = { "menu_reduce_intensity" },
            sessionConnectivityStabilizeMessage = { "session_connectivity_stabilize" },
            sessionPacingHoldTargetMessage = { deviationPct -> "pacing:$deviationPct" },
            sessionCadenceIncreaseSlightlyMessage = { cadenceRpm, targetRpm ->
                "cadence_up:$cadenceRpm:$targetRpm"
            },
            sessionCadenceReduceSlightlyMessage = { cadenceRpm, targetRpm ->
                "cadence_down:$cadenceRpm:$targetRpm"
            },
            sessionSafetyReduceEffortMessage = { heartRateBpm -> "safety:$heartRateBpm" },
            summaryRecoveryReduceNextLoadMessage = { "summary_recovery" },
            fallbackMessage = { "fallback" },
        )
    }

    private fun facade(
        currentScreen: () -> AppScreen,
        refreshCoordinator: (AiPhase?, Boolean, Long) -> Unit = { _, _, _ -> },
    ): AiAssistantFacade {
        return AiAssistantFacade(
            currentScreen = currentScreen,
            resolvePhase = support()::phaseForScreen,
            refreshCoordinator = refreshCoordinator,
            clearCoordinatorPhaseMessage = {},
            nowMillisProvider = { 999L },
        )
    }

    private fun message(
        templateKey: String,
        templateArgs: Map<String, String> = emptyMap(),
    ): AiPresentationMessage {
        return AiPresentationMessage(
            phase = AiPhase.SESSION,
            surface = AiPresentationSurface.SESSION_ASSISTANT,
            type = AiRecommendationType.CADENCE,
            confidence = AiQualityClass.HIGH,
            templateKey = templateKey,
            templateArgs = templateArgs,
        )
    }

    private data class FakeAiAssistantStatePort(
        override val ftmsReady: Boolean = false,
        override val ftmsReachable: Boolean? = null,
        override val hrConnected: Boolean = false,
        override val hrReachable: Boolean? = null,
    ) : AiAssistantStatePort
}
