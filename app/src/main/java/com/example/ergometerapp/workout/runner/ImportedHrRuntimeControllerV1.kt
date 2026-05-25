package com.example.ergometerapp.workout.runner

import com.example.ergometerapp.workout.ImportedHrExecutionCapabilitySnapshot
import com.example.ergometerapp.workout.ImportedHrExecutionPolicyV1
import com.example.ergometerapp.workout.resolvedSafetyCapThrottlePowerWatts

/**
 * Stateful runner-side coordinator for the conservative imported-HR V1 path.
 *
 * Why this exists:
 * - The pure state machine already defines stable transition vocabulary.
 * - Session/runtime wiring still needs one small mutable seam that can consume
 *   live telemetry snapshots without pushing those concerns into
 *   [ImportedHrRuntimeStateMachineV1].
 *
 * Current scope:
 * - starts only through the existing capability snapshot gate
 * - applies conservative bounded target-band correction while respecting
 *   authored min/max limits
 * - reacts to trainer-control loss, HR-signal loss, persistent HR-cap
 *   breaches, and max/min unreachable outcomes
 * - intentionally stops short of a broader adaptive closed-loop controller
 */
class ImportedHrRuntimeControllerV1(
    private val policy: ImportedHrExecutionPolicyV1,
) {
    private var nextIncreaseAllowedAtElapsedMs: Long = Long.MIN_VALUE
    private var nextDecreaseAllowedAtElapsedMs: Long = Long.MIN_VALUE
    private var state: ImportedHrRuntimeState = ImportedHrRuntimeStateMachineV1.ready(policy)

    /**
     * Exposes the latest runner-owned HR runtime state for diagnostics and
     * later session wiring.
     */
    fun state(): ImportedHrRuntimeState = state

    /**
     * Starts the HR-owned runtime path from the current capability snapshot.
     */
    fun start(snapshot: ImportedHrExecutionCapabilitySnapshot): ImportedHrRuntimeTransition {
        return ImportedHrRuntimeStateMachineV1.start(
            policy = policy,
            snapshot = snapshot,
        ).also { transition ->
            commitTransition(transition = transition, elapsedRealtimeMs = null)
        }
    }

    /**
     * Applies one live telemetry snapshot to the active HR runtime state.
     *
     * Returns null when the snapshot does not require a new transition under
     * the current bounded V1 rules.
     */
    fun onTelemetry(
        heartRateBpm: Int?,
        hasTrainerControl: Boolean,
        elapsedRealtimeMs: Long,
    ): ImportedHrRuntimeTransition? {
        val activeState = state as? ImportedHrActiveRuntimeState ?: return null
        if (!hasTrainerControl) {
            return ImportedHrRuntimeStateMachineV1.onTrainerControlLost(activeState)
                .also { transition ->
                    commitTransition(transition = transition, elapsedRealtimeMs = elapsedRealtimeMs)
                }
        }

        if (heartRateBpm == null || heartRateBpm <= 0) {
            return when (activeState) {
                is ImportedHrRuntimeState.Running ->
                    ImportedHrRuntimeStateMachineV1.onSignalLost(activeState).also { transition ->
                        commitTransition(transition = transition, elapsedRealtimeMs = elapsedRealtimeMs)
                    }

                is ImportedHrRuntimeState.Fallback ->
                    ImportedHrRuntimeStateMachineV1.onFallbackApplied(activeState).also { transition ->
                        commitTransition(transition = transition, elapsedRealtimeMs = elapsedRealtimeMs)
                    }

                is ImportedHrRuntimeState.SafetyThrottle,
                is ImportedHrRuntimeState.AtMaxPowerBelowTarget,
                is ImportedHrRuntimeState.AtMinPowerAboveTarget -> null
            }
        }

        if (heartRateBpm >= policy.hrUpperCapBpm) {
            return when (activeState) {
                is ImportedHrRuntimeState.Running ->
                    ImportedHrRuntimeStateMachineV1.onSafetyCapBreached(
                        state = activeState,
                        requestedThrottlePowerWatts = policy.resolvedSafetyCapThrottlePowerWatts(),
                    ).also { transition ->
                        commitTransition(transition = transition, elapsedRealtimeMs = elapsedRealtimeMs)
                    }

                is ImportedHrRuntimeState.SafetyThrottle ->
                    ImportedHrRuntimeStateMachineV1.onSafetyCapPersisted(activeState)
                        .also { transition ->
                            commitTransition(transition = transition, elapsedRealtimeMs = elapsedRealtimeMs)
                        }

                is ImportedHrRuntimeState.Fallback,
                is ImportedHrRuntimeState.AtMaxPowerBelowTarget,
                is ImportedHrRuntimeState.AtMinPowerAboveTarget -> null
            }
        }

        if (heartRateBpm < policy.targetLowBpm) {
            return handleBelowTarget(
                activeState = activeState,
                elapsedRealtimeMs = elapsedRealtimeMs,
            )
        }

        if (heartRateBpm > policy.targetHighBpm) {
            return handleAboveTarget(
                activeState = activeState,
                elapsedRealtimeMs = elapsedRealtimeMs,
            )
        }

        return when (activeState) {
            is ImportedHrRuntimeState.AtMaxPowerBelowTarget,
            is ImportedHrRuntimeState.AtMinPowerAboveTarget ->
                ImportedHrRuntimeStateMachineV1.onTargetReachableAgain(activeState)
                    .also { transition ->
                        commitTransition(transition = transition, elapsedRealtimeMs = elapsedRealtimeMs)
                    }

            else -> null
        }
    }

    private fun handleBelowTarget(
        activeState: ImportedHrActiveRuntimeState,
        elapsedRealtimeMs: Long,
    ): ImportedHrRuntimeTransition? {
        return when (activeState) {
            is ImportedHrRuntimeState.Fallback,
            is ImportedHrRuntimeState.SafetyThrottle -> null

            is ImportedHrRuntimeState.AtMaxPowerBelowTarget -> null

            is ImportedHrRuntimeState.AtMinPowerAboveTarget -> {
                if (elapsedRealtimeMs < nextIncreaseAllowedAtElapsedMs) {
                    ImportedHrRuntimeStateMachineV1.onTargetReachableAgain(activeState)
                        .also { transition ->
                            commitTransition(transition = transition, elapsedRealtimeMs = elapsedRealtimeMs)
                        }
                } else {
                    val nextPowerWatts = (activeState.currentPowerWatts + IMPORTED_HR_INCREASE_STEP_WATTS)
                        .coerceAtMost(policy.maxPowerWatts)
                    ImportedHrRuntimeStateMachineV1.onPowerIncreased(
                        state = activeState,
                        nextPowerWatts = nextPowerWatts,
                    ).also { transition ->
                        commitTransition(transition = transition, elapsedRealtimeMs = elapsedRealtimeMs)
                    }
                }
            }

            is ImportedHrRuntimeState.Running -> {
                if (activeState.currentPowerWatts >= policy.maxPowerWatts) {
                    ImportedHrRuntimeStateMachineV1.onTargetUnreachableAtMaxPower(activeState)
                        .also { transition ->
                            commitTransition(transition = transition, elapsedRealtimeMs = elapsedRealtimeMs)
                        }
                } else if (elapsedRealtimeMs >= nextIncreaseAllowedAtElapsedMs) {
                    val nextPowerWatts = (activeState.currentPowerWatts + IMPORTED_HR_INCREASE_STEP_WATTS)
                        .coerceAtMost(policy.maxPowerWatts)
                    ImportedHrRuntimeStateMachineV1.onPowerIncreased(
                        state = activeState,
                        nextPowerWatts = nextPowerWatts,
                    ).also { transition ->
                        commitTransition(transition = transition, elapsedRealtimeMs = elapsedRealtimeMs)
                    }
                } else {
                    null
                }
            }
        }
    }

    private fun handleAboveTarget(
        activeState: ImportedHrActiveRuntimeState,
        elapsedRealtimeMs: Long,
    ): ImportedHrRuntimeTransition? {
        return when (activeState) {
            is ImportedHrRuntimeState.Fallback,
            is ImportedHrRuntimeState.SafetyThrottle -> null

            is ImportedHrRuntimeState.AtMinPowerAboveTarget -> null

            is ImportedHrRuntimeState.AtMaxPowerBelowTarget -> {
                if (elapsedRealtimeMs < nextDecreaseAllowedAtElapsedMs) {
                    null
                } else {
                    val nextPowerWatts = (activeState.currentPowerWatts - IMPORTED_HR_DECREASE_STEP_WATTS)
                        .coerceAtLeast(policy.minPowerWatts)
                    ImportedHrRuntimeStateMachineV1.onPowerDecreased(
                        state = activeState,
                        nextPowerWatts = nextPowerWatts,
                    ).also { transition ->
                        commitTransition(transition = transition, elapsedRealtimeMs = elapsedRealtimeMs)
                    }
                }
            }

            is ImportedHrRuntimeState.Running -> {
                if (activeState.currentPowerWatts <= policy.minPowerWatts) {
                    ImportedHrRuntimeStateMachineV1.onTargetUnreachableAtMinPower(activeState)
                        .also { transition ->
                            commitTransition(transition = transition, elapsedRealtimeMs = elapsedRealtimeMs)
                        }
                } else if (elapsedRealtimeMs < nextDecreaseAllowedAtElapsedMs) {
                    null
                } else {
                    val nextPowerWatts = (activeState.currentPowerWatts - IMPORTED_HR_DECREASE_STEP_WATTS)
                        .coerceAtLeast(policy.minPowerWatts)
                    ImportedHrRuntimeStateMachineV1.onPowerDecreased(
                        state = activeState,
                        nextPowerWatts = nextPowerWatts,
                    ).also { transition ->
                        commitTransition(transition = transition, elapsedRealtimeMs = elapsedRealtimeMs)
                    }
                }
            }
        }
    }

    private fun commitTransition(
        transition: ImportedHrRuntimeTransition,
        elapsedRealtimeMs: Long?,
    ) {
        if (transition.event == ImportedHrRuntimeEvent.POWER_INCREASED) {
            nextIncreaseAllowedAtElapsedMs =
                (elapsedRealtimeMs ?: 0L) + IMPORTED_HR_INCREASE_HOLDOFF_MS
        }
        if (transition.event == ImportedHrRuntimeEvent.POWER_DECREASED) {
            nextDecreaseAllowedAtElapsedMs =
                (elapsedRealtimeMs ?: 0L) + IMPORTED_HR_DECREASE_HOLDOFF_MS
        }
        state = transition.toState
    }
}

internal const val IMPORTED_HR_INCREASE_STEP_WATTS = 5
internal const val IMPORTED_HR_DECREASE_STEP_WATTS = 10
internal const val IMPORTED_HR_INCREASE_HOLDOFF_MS = 15_000L
internal const val IMPORTED_HR_DECREASE_HOLDOFF_MS = 10_000L
