package com.example.ergometerapp

import android.content.Context
import com.example.ergometerapp.ai.AiInputSnapshot
import com.example.ergometerapp.ai.AiPhase
import com.example.ergometerapp.ai.AiPolicyGuardrails
import com.example.ergometerapp.ai.AiPresentationAdapter
import com.example.ergometerapp.ai.AiRecentEmission
import com.example.ergometerapp.ai.AiRecommendationCandidate
import com.example.ergometerapp.ai.AiRecommendationEngine
import com.example.ergometerapp.ai.AiTelemetryLogger
import com.example.ergometerapp.ai.AiWearableNormalizationResult

/**
 * Concrete [AiAssistantStatePort] backed by the existing ViewModel-owned state holders.
 *
 * Why:
 * - The AI support helpers only need four reachability signals, so this adapter keeps the
 *   contract explicit instead of rebuilding another anonymous object inside `MainViewModel`.
 * - The adapter is intentionally lambda-based so the caller can preserve the current owners
 *   without exposing the larger state containers as a new dependency surface.
 */
internal class RealAiAssistantStatePort(
    private val ftmsReadyProvider: () -> Boolean,
    private val ftmsReachableProvider: () -> Boolean?,
    private val hrConnectedProvider: () -> Boolean,
    private val hrReachableProvider: () -> Boolean?,
) : AiAssistantStatePort {
    override val ftmsReady: Boolean
        get() = ftmsReadyProvider()

    override val ftmsReachable: Boolean?
        get() = ftmsReachableProvider()

    override val hrConnected: Boolean
        get() = hrConnectedProvider()

    override val hrReachable: Boolean?
        get() = hrReachableProvider()
}

/**
 * Groups the AI helpers that `MainViewModel` composes together for refresh orchestration.
 *
 * The bundle keeps ownership boundaries visible: `support` handles local translation,
 * `coordinator` owns runtime policy/cadence flow, and `facade` exposes the screen-aware entry
 * points used by the rest of the ViewModel.
 */
internal data class AiAssistantIntegration(
    val support: AiAssistantSupport,
    val coordinator: AiCoordinator,
    val facade: AiAssistantFacade,
)

/**
 * Builds the ViewModel-local AI integration seam around the existing state owners and services.
 *
 * This keeps `MainViewModel` focused on long-lived app composition while the AI-specific wiring
 * remains in one place. No UI layout behavior lives here; callers still own state and Android
 * lifecycle plumbing.
 */
internal fun buildAiAssistantIntegration(
    appContext: Context,
    uiState: AppUiState,
    aiAssistantStatePort: AiAssistantStatePort,
    currentScreen: () -> AppScreen,
    currentHrConnected: () -> Boolean,
    hasSelectedHrDevice: () -> Boolean,
    isMockTrainerModeActive: () -> Boolean,
    ftpWatts: () -> Int,
    recommendationEngine: AiRecommendationEngine,
    policyGuardrails: AiPolicyGuardrails,
    presentationAdapter: AiPresentationAdapter,
    telemetryLogger: AiTelemetryLogger,
): AiAssistantIntegration {
    val support = AiAssistantSupport(
        statePort = aiAssistantStatePort,
        menuConnectivityCheckTrainerMessage = {
            appContext.getString(R.string.ai_menu_connectivity_check_trainer)
        },
        menuReadinessReduceIntensityMessage = {
            appContext.getString(R.string.ai_menu_readiness_reduce_intensity)
        },
        sessionConnectivityStabilizeMessage = {
            appContext.getString(R.string.ai_session_connectivity_stabilize)
        },
        sessionPacingHoldTargetMessage = { deviationPct ->
            appContext.getString(R.string.ai_session_pacing_hold_target, deviationPct)
        },
        sessionCadenceIncreaseSlightlyMessage = { cadenceRpm, cadenceTargetRpm ->
            appContext.getString(
                R.string.ai_session_cadence_increase_slightly,
                cadenceRpm,
                cadenceTargetRpm,
            )
        },
        sessionCadenceReduceSlightlyMessage = { cadenceRpm, cadenceTargetRpm ->
            appContext.getString(
                R.string.ai_session_cadence_reduce_slightly,
                cadenceRpm,
                cadenceTargetRpm,
            )
        },
        sessionSafetyReduceEffortMessage = { heartRateBpm ->
            appContext.getString(R.string.ai_session_safety_reduce_effort, heartRateBpm)
        },
        summaryRecoveryReduceNextLoadMessage = {
            appContext.getString(R.string.ai_summary_recovery_reduce_next_load)
        },
        fallbackMessage = {
            appContext.getString(R.string.ai_assistant_fallback)
        },
    )
    val coordinator = AiCoordinator(
        uiState = uiState,
        evaluateRecommendations = { phase, snapshot, nowMillis, recentSessionEmissions, wearableNormalization ->
            evaluateAiRecommendations(
                phase = phase,
                snapshot = snapshot,
                nowMillis = nowMillis,
                recentSessionEmissions = recentSessionEmissions,
                wearableNormalization = wearableNormalization,
                recommendationEngine = recommendationEngine,
            )
        },
        evaluatePolicy = policyGuardrails::evaluate,
        adaptPresentation = { phase, evaluation, nowMillis, recentPresented ->
            presentationAdapter.adapt(
                currentPhase = phase,
                evaluation = evaluation,
                nowMillis = nowMillis,
                recentPresented = recentPresented,
            )
        },
        logTelemetry = telemetryLogger::logCycle,
        formatMessage = support::formatMessage,
        isErrorType = support::isErrorType,
        trainerReachability = support::trainerReachability,
        heartRateReachability = support::heartRateReachability,
        isHeartRateConnected = currentHrConnected,
        hasSelectedHrDevice = hasSelectedHrDevice,
        isMockTrainerModeActive = isMockTrainerModeActive,
        ftpWatts = ftpWatts,
    )
    val facade = AiAssistantFacade(
        currentScreen = currentScreen,
        resolvePhase = support::phaseForScreen,
        refreshCoordinator = { phase, force, nowMillis ->
            coordinator.refresh(
                phase = phase,
                force = force,
                nowMillis = nowMillis,
            )
        },
        clearCoordinatorPhaseMessage = coordinator::clearPhaseMessage,
    )
    return AiAssistantIntegration(
        support = support,
        coordinator = coordinator,
        facade = facade,
    )
}

private fun evaluateAiRecommendations(
    phase: AiPhase,
    snapshot: AiInputSnapshot,
    nowMillis: Long,
    recentSessionEmissions: List<AiRecentEmission>,
    wearableNormalization: AiWearableNormalizationResult?,
    recommendationEngine: AiRecommendationEngine,
): List<AiRecommendationCandidate> {
    return if (phase == AiPhase.SESSION) {
        recommendationEngine.evaluateWithInRideLimits(
            snapshot = snapshot,
            nowMillis = nowMillis,
            recentEmissions = recentSessionEmissions,
            wearableNormalization = wearableNormalization,
        )
    } else {
        recommendationEngine.evaluate(
            snapshot = snapshot,
            nowMillis = nowMillis,
            wearableNormalization = wearableNormalization,
        )
    }
}
