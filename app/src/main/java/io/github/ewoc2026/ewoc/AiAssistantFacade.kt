package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.ai.AiPhase
import io.github.ewoc2026.ewoc.ai.AiPresentationMessage
import io.github.ewoc2026.ewoc.ai.AiReachability
import io.github.ewoc2026.ewoc.ai.AiRecommendationType

/**
 * ViewModel-owned AI reachability bridge.
 *
 * Invariants:
 * - Trainer readiness upgrades FTMS reachability to reachable even if the last passive probe is stale.
 * - An active HR connection upgrades HR reachability to reachable because in-session GATT state is stronger than MENU probes.
 */
internal interface AiAssistantStatePort {
    val ftmsReady: Boolean
    val ftmsReachable: Boolean?
    val hrConnected: Boolean
    val hrReachable: Boolean?
}

/**
 * Encapsulates ViewModel-local AI helpers that do not belong in the domain coordinator.
 *
 * Why:
 * - `AiCoordinator` owns policy/presentation flow, while this adapter keeps Android string formatting and screen/reachability mapping near the UI layer.
 * - Extracting these helpers keeps `MainViewModel` wiring focused on composition instead of AI-specific translation logic.
 */
internal class AiAssistantSupport(
    private val statePort: AiAssistantStatePort,
    private val menuConnectivityCheckTrainerMessage: () -> String,
    private val menuReadinessReduceIntensityMessage: () -> String,
    private val sessionConnectivityStabilizeMessage: () -> String,
    private val sessionPacingHoldTargetMessage: (Int) -> String,
    private val sessionCadenceIncreaseSlightlyMessage: (Int, Int) -> String,
    private val sessionCadenceReduceSlightlyMessage: (Int, Int) -> String,
    private val sessionSafetyReduceEffortMessage: (Int) -> String,
    private val summaryRecoveryReduceNextLoadMessage: () -> String,
    private val fallbackMessage: () -> String,
) {

    fun trainerReachability(): AiReachability {
        if (statePort.ftmsReady) return AiReachability.REACHABLE
        return when (statePort.ftmsReachable) {
            true -> AiReachability.REACHABLE
            false -> AiReachability.UNREACHABLE
            null -> AiReachability.UNKNOWN
        }
    }

    fun heartRateReachability(): AiReachability {
        if (statePort.hrConnected) return AiReachability.REACHABLE
        return when (statePort.hrReachable) {
            true -> AiReachability.REACHABLE
            false -> AiReachability.UNREACHABLE
            null -> AiReachability.UNKNOWN
        }
    }

    fun formatMessage(message: AiPresentationMessage): String {
        return when (message.templateKey) {
            AI_MENU_CONNECTIVITY_CHECK_TRAINER_ACTION -> menuConnectivityCheckTrainerMessage()
            "ai.menu.readiness_reduce_intensity" -> menuReadinessReduceIntensityMessage()
            "ai.session.connectivity_stabilize" -> sessionConnectivityStabilizeMessage()
            "ai.session.pacing_hold_target" -> {
                val deviationPct = message.templateArgs["deviation_pct"]?.toIntOrNull() ?: 0
                sessionPacingHoldTargetMessage(deviationPct)
            }
            "ai.session.cadence_increase_slightly" -> {
                val cadenceRpm = message.templateArgs["cadence_rpm"]?.toIntOrNull() ?: 0
                val cadenceTargetRpm = message.resolveCadenceTargetRpm()
                sessionCadenceIncreaseSlightlyMessage(cadenceRpm, cadenceTargetRpm)
            }
            "ai.session.cadence_reduce_slightly" -> {
                val cadenceRpm = message.templateArgs["cadence_rpm"]?.toIntOrNull() ?: 0
                val cadenceTargetRpm = message.resolveCadenceTargetRpm()
                sessionCadenceReduceSlightlyMessage(cadenceRpm, cadenceTargetRpm)
            }
            "ai.session.safety_reduce_effort" -> {
                val heartRateBpm = message.templateArgs["hr_bpm"]?.toIntOrNull() ?: 0
                sessionSafetyReduceEffortMessage(heartRateBpm)
            }
            "ai.summary.recovery_reduce_next_load" -> summaryRecoveryReduceNextLoadMessage()
            else -> fallbackMessage()
        }
    }

    fun isErrorType(type: AiRecommendationType): Boolean {
        return type == AiRecommendationType.SAFETY ||
            type == AiRecommendationType.CONNECTIVITY
    }

    fun phaseForScreen(screen: AppScreen): AiPhase? {
        return when (screen) {
            AppScreen.MENU,
            AppScreen.EWO_EDITOR -> AiPhase.MENU
            AppScreen.SESSION -> AiPhase.SESSION
            AppScreen.SUMMARY,
            AppScreen.STOPPING -> AiPhase.SUMMARY
            AppScreen.CONNECTING,
            AppScreen.BASELINE_FITNESS_TEST -> null
        }
    }

    private fun AiPresentationMessage.resolveCadenceTargetRpm(): Int {
        return templateArgs["cadence_target_rpm"]?.toIntOrNull()
            ?: templateArgs["cadence_threshold_rpm"]?.toIntOrNull()
            ?: 0
    }
}

/**
 * Wraps AI coordinator entry points that depend on screen-to-phase resolution.
 *
 * Invariants:
 * - Refreshes suppress coordinator work when the current screen does not map to an AI phase.
 */
internal class AiAssistantFacade(
    private val currentScreen: () -> AppScreen,
    private val resolvePhase: (AppScreen) -> AiPhase?,
    private val refreshCoordinator: (AiPhase?, Boolean, Long) -> Unit,
    private val clearCoordinatorPhaseMessage: (AiPhase) -> Unit,
    private val nowMillisProvider: () -> Long = System::currentTimeMillis,
) {

    fun refresh(
        forcePhase: AiPhase? = null,
        force: Boolean = false,
        nowMillis: Long = nowMillisProvider(),
    ) {
        refreshCoordinator(
            forcePhase ?: resolvePhase(currentScreen()),
            force,
            nowMillis,
        )
    }

    fun clearPhaseMessage(phase: AiPhase) {
        clearCoordinatorPhaseMessage(phase)
    }
}
