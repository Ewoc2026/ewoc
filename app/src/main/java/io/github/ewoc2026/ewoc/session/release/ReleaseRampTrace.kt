package io.github.ewoc2026.ewoc.session.release

/**
 * Emits structured release-ramp trace events without coupling the decision layer
 * directly to Android logging APIs.
 */
internal fun interface ReleaseRampTraceEmitter {
    fun emit(event: ReleaseRampTraceEvent)
}

/**
 * Structured trace payload for release-ramp decision observability.
 *
 * The intent is to keep the runtime breadcrumbs grep-friendly and stable across
 * trainer profiles instead of scattering free-form text into orchestrator logs.
 */
internal sealed interface ReleaseRampTraceEvent {
    fun context(): Map<String, String>

    data class Evaluate(
        val intent: ReleaseIntent,
        val authority: TrainerControlAuthority,
        val disconnectRequired: Boolean,
        val ftmsReady: Boolean,
        val ftmsControlGranted: Boolean,
        val cadenceRpm: Int?,
        val instantaneousPowerW: Int?,
        val knownAppTargetPowerW: Int?,
        val lowRiskCadenceThresholdRpm: Int,
        val preferRampEvenAtLowCadence: Boolean,
        val cadenceNeedsRamp: Boolean,
    ) : ReleaseRampTraceEvent {
        override fun context(): Map<String, String> {
            return linkedMapOf(
                "intent" to intent.name,
                "authority" to authority.name,
                "disconnectRequired" to disconnectRequired.toString(),
                "ftmsReady" to ftmsReady.toString(),
                "ftmsControlGranted" to ftmsControlGranted.toString(),
                "cadenceRpm" to cadenceRpm.asTraceValue(),
                "instantaneousPowerW" to instantaneousPowerW.asTraceValue(),
                "knownAppTargetPowerW" to knownAppTargetPowerW.asTraceValue(),
                "lowRiskCadenceThresholdRpm" to lowRiskCadenceThresholdRpm.toString(),
                "preferRampEvenAtLowCadence" to preferRampEvenAtLowCadence.toString(),
                "cadenceNeedsRamp" to cadenceNeedsRamp.toString(),
            )
        }
    }

    data class Decision(
        val intent: ReleaseIntent,
        val authority: TrainerControlAuthority,
        val disconnectRequired: Boolean,
        val ftmsReady: Boolean,
        val ftmsControlGranted: Boolean,
        val cadenceRpm: Int?,
        val knownAppTargetPowerW: Int?,
        val cadenceNeedsRamp: Boolean,
        val outcome: Outcome,
        val notPossibleReason: ReleaseRampDecision.NotPossible.Reason? = null,
        val plan: ReleaseRampPlan? = null,
    ) : ReleaseRampTraceEvent {
        override fun context(): Map<String, String> {
            val context = linkedMapOf(
                "intent" to intent.name,
                "authority" to authority.name,
                "disconnectRequired" to disconnectRequired.toString(),
                "ftmsReady" to ftmsReady.toString(),
                "ftmsControlGranted" to ftmsControlGranted.toString(),
                "cadenceRpm" to cadenceRpm.asTraceValue(),
                "knownAppTargetPowerW" to knownAppTargetPowerW.asTraceValue(),
                "cadenceNeedsRamp" to cadenceNeedsRamp.toString(),
                "outcome" to outcome.name,
            )
            notPossibleReason?.let { reason ->
                context["reason"] = reason.name
            }
            plan?.let { releasePlan ->
                context["startTargetPowerW"] = releasePlan.startTargetPowerW.toString()
                context["endTargetPowerW"] = releasePlan.endTargetPowerW.toString()
                context["durationMs"] = releasePlan.durationMs.toString()
                context["tickMs"] = releasePlan.tickMs.toString()
                context["stepRoundToWatts"] = releasePlan.stepRoundToWatts.toString()
            }
            return context
        }
    }

    enum class Outcome {
        NO_NEED,
        NOT_POSSIBLE,
        EXECUTE,
    }
}

private fun Int?.asTraceValue(): String = this?.toString() ?: "null"
