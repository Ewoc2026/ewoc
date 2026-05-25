package com.example.ergometerapp.workout.runner

import com.example.ergometerapp.workout.ImportedHrExecutionCapability
import com.example.ergometerapp.workout.ImportedHrExecutionCapabilitySnapshot
import com.example.ergometerapp.workout.ImportedHrExecutionPolicyV1
import com.example.ergometerapp.workout.resolvedSignalLossPowerWatts

/**
 * Pure runner-owned v1 state model for imported HR execution startup and
 * fail-safe transitions.
 *
 * This keeps capability gating and emergency-path semantics in the runner
 * layer, while leaving the later control-loop tuning work out of scope for
 * this first adoption slice.
 */
object ImportedHrRuntimeStateMachineV1 {
    /**
     * Creates the runner-owned ready state for a resolved imported HR policy.
     */
    fun ready(policy: ImportedHrExecutionPolicyV1): ImportedHrRuntimeState.Ready {
        return ImportedHrRuntimeState.Ready(policy = policy)
    }

    /**
     * Evaluates start readiness from the current runtime snapshot and either
     * arms the runner at the authored initial power or fails the segment start.
     */
    fun start(
        policy: ImportedHrExecutionPolicyV1,
        snapshot: ImportedHrExecutionCapabilitySnapshot,
    ): ImportedHrRuntimeTransition {
        val fromState = ready(policy)
        val missingCapabilities = policy.requiredCapabilities.filterTo(linkedSetOf()) { capability ->
            !snapshot.has(capability)
        }
        if (missingCapabilities.isNotEmpty()) {
            val event = startFailureEvent(missingCapabilities)
            return ImportedHrRuntimeTransition(
                fromState = fromState,
                toState = ImportedHrRuntimeState.FailedToStart(
                    policy = policy,
                    reason = startFailureReason(missingCapabilities),
                    missingCapabilities = missingCapabilities,
                ),
                event = event,
                commands = listOf(ImportedHrRuntimeCommand.FailStart),
            )
        }

        return ImportedHrRuntimeTransition(
            fromState = fromState,
            toState = ImportedHrRuntimeState.Running(
                policy = policy,
                currentPowerWatts = policy.initialPowerWatts,
                increasesBlocked = false,
            ),
            event = ImportedHrRuntimeEvent.STARTED,
            commands = listOf(
                ImportedHrRuntimeCommand.SetPower(policy.initialPowerWatts),
            ),
        )
    }

    /**
     * Enters the policy-defined fallback path after mid-segment HR signal loss.
     */
    fun onSignalLost(
        state: ImportedHrRuntimeState.Running,
    ): ImportedHrRuntimeTransition {
        val fallbackPowerWatts = state.policy.resolvedSignalLossPowerWatts()
        return ImportedHrRuntimeTransition(
            fromState = state,
            toState = ImportedHrRuntimeState.Fallback(
                policy = state.policy,
                fallbackPowerWatts = fallbackPowerWatts,
            ),
            event = ImportedHrRuntimeEvent.HR_SIGNAL_LOST,
            commands = listOf(
                ImportedHrRuntimeCommand.SetPower(fallbackPowerWatts),
                ImportedHrRuntimeCommand.BlockIncrease,
            ),
        )
    }

    /**
     * Stops the segment after the fallback power command has been applied for a
     * signal-loss event.
     */
    fun onFallbackApplied(
        state: ImportedHrRuntimeState.Fallback,
    ): ImportedHrRuntimeTransition {
        return ImportedHrRuntimeTransition(
            fromState = state,
            toState = ImportedHrRuntimeState.Stopped(
                policy = state.policy,
                reason = ImportedHrRuntimeStopReason.SIGNAL_LOSS,
                requiresUserAcknowledgement = false,
            ),
            event = ImportedHrRuntimeEvent.HR_SIGNAL_LOSS_FALLBACK_APPLIED,
            commands = listOf(ImportedHrRuntimeCommand.StopWorkout),
        )
    }

    /**
     * Stops HR execution immediately when trainer control disappears during an
     * active HR-owned segment state.
     */
    fun onTrainerControlLost(
        state: ImportedHrActiveRuntimeState,
    ): ImportedHrRuntimeTransition {
        return ImportedHrRuntimeTransition(
            fromState = state,
            toState = ImportedHrRuntimeState.Stopped(
                policy = state.policy,
                reason = ImportedHrRuntimeStopReason.TRAINER_CONTROL_LOST,
                requiresUserAcknowledgement = false,
            ),
            event = ImportedHrRuntimeEvent.TRAINER_CONTROL_LOST,
            commands = listOf(ImportedHrRuntimeCommand.StopWorkout),
        )
    }

    /**
     * Reduces power and blocks further increases when the hard HR cap is
     * breached, without stopping until the runner confirms the breach persists.
     */
    fun onSafetyCapBreached(
        state: ImportedHrRuntimeState.Running,
        requestedThrottlePowerWatts: Int,
    ): ImportedHrRuntimeTransition {
        val upperBound = state.currentPowerWatts.coerceIn(
            minimumValue = 1,
            maximumValue = state.policy.maxPowerWatts,
        )
        val throttledPowerWatts = requestedThrottlePowerWatts.coerceIn(
            minimumValue = 1,
            maximumValue = upperBound,
        )
        return ImportedHrRuntimeTransition(
            fromState = state,
            toState = ImportedHrRuntimeState.SafetyThrottle(
                policy = state.policy,
                throttledPowerWatts = throttledPowerWatts,
            ),
            event = ImportedHrRuntimeEvent.HR_CAP_BREACHED,
            commands = listOf(
                ImportedHrRuntimeCommand.SetPower(throttledPowerWatts),
                ImportedHrRuntimeCommand.BlockIncrease,
            ),
        )
    }

    /**
     * Converts a persistent safety-cap breach into a terminal stop that still
     * requires explicit acknowledgement before the segment is considered safe.
     */
    fun onSafetyCapPersisted(
        state: ImportedHrRuntimeState.SafetyThrottle,
    ): ImportedHrRuntimeTransition {
        return ImportedHrRuntimeTransition(
            fromState = state,
            toState = ImportedHrRuntimeState.Stopped(
                policy = state.policy,
                reason = ImportedHrRuntimeStopReason.HR_SAFETY_CAP_PERSISTED,
                requiresUserAcknowledgement = true,
            ),
            event = ImportedHrRuntimeEvent.HR_CAP_BREACH_PERSISTED,
            commands = listOf(
                ImportedHrRuntimeCommand.StopWorkout,
                ImportedHrRuntimeCommand.RequireUserAcknowledgement,
            ),
        )
    }

    /**
     * Applies one bounded upward correction while keeping the segment inside
     * authored power limits.
     */
    fun onPowerIncreased(
        state: ImportedHrActiveRuntimeState,
        nextPowerWatts: Int,
    ): ImportedHrRuntimeTransition {
        return ImportedHrRuntimeTransition(
            fromState = state,
            toState = ImportedHrRuntimeState.Running(
                policy = state.policy,
                currentPowerWatts = nextPowerWatts.coerceAtMost(state.policy.maxPowerWatts),
                increasesBlocked = false,
            ),
            event = ImportedHrRuntimeEvent.POWER_INCREASED,
            commands = listOf(
                ImportedHrRuntimeCommand.SetPower(nextPowerWatts.coerceAtMost(state.policy.maxPowerWatts)),
                ImportedHrRuntimeCommand.ClearUnreachableTargetStatus,
            ),
        )
    }

    /**
     * Applies one bounded downward correction while keeping the segment inside
     * authored power limits.
     */
    fun onPowerDecreased(
        state: ImportedHrActiveRuntimeState,
        nextPowerWatts: Int,
    ): ImportedHrRuntimeTransition {
        return ImportedHrRuntimeTransition(
            fromState = state,
            toState = ImportedHrRuntimeState.Running(
                policy = state.policy,
                currentPowerWatts = nextPowerWatts.coerceAtLeast(state.policy.minPowerWatts),
                increasesBlocked = false,
            ),
            event = ImportedHrRuntimeEvent.POWER_DECREASED,
            commands = listOf(
                ImportedHrRuntimeCommand.SetPower(nextPowerWatts.coerceAtLeast(state.policy.minPowerWatts)),
                ImportedHrRuntimeCommand.ClearUnreachableTargetStatus,
            ),
        )
    }

    /**
     * Surfaces that the target remains unreachable while clamped to the
     * segment's maximum power.
     */
    fun onTargetUnreachableAtMaxPower(
        state: ImportedHrActiveRuntimeState,
    ): ImportedHrRuntimeTransition {
        return ImportedHrRuntimeTransition(
            fromState = state,
            toState = ImportedHrRuntimeState.AtMaxPowerBelowTarget(
                policy = state.policy,
                currentPowerWatts = state.currentPowerWatts.coerceAtMost(state.policy.maxPowerWatts),
            ),
            event = ImportedHrRuntimeEvent.TARGET_UNREACHABLE_HIGH,
            commands = listOf(
                ImportedHrRuntimeCommand.ReportUnreachableTarget(
                    status = ImportedHrRuntimeUnreachableTargetStatus.AT_MAX_POWER_BELOW_TARGET,
                ),
            ),
        )
    }

    /**
     * Surfaces that the target remains unreachable while clamped to the
     * segment's minimum power.
     */
    fun onTargetUnreachableAtMinPower(
        state: ImportedHrActiveRuntimeState,
    ): ImportedHrRuntimeTransition {
        return ImportedHrRuntimeTransition(
            fromState = state,
            toState = ImportedHrRuntimeState.AtMinPowerAboveTarget(
                policy = state.policy,
                currentPowerWatts = state.currentPowerWatts.coerceAtLeast(state.policy.minPowerWatts),
            ),
            event = ImportedHrRuntimeEvent.TARGET_UNREACHABLE_LOW,
            commands = listOf(
                ImportedHrRuntimeCommand.ReportUnreachableTarget(
                    status = ImportedHrRuntimeUnreachableTargetStatus.AT_MIN_POWER_ABOVE_TARGET,
                ),
            ),
        )
    }

    /**
     * Clears an unreachable-target status once HR returns in range or the
     * runtime can move away from the bound again.
     */
    fun onTargetReachableAgain(
        state: ImportedHrActiveRuntimeState,
    ): ImportedHrRuntimeTransition {
        return ImportedHrRuntimeTransition(
            fromState = state,
            toState = ImportedHrRuntimeState.Running(
                policy = state.policy,
                currentPowerWatts = state.currentPowerWatts,
                increasesBlocked = false,
            ),
            event = ImportedHrRuntimeEvent.TARGET_REACHABLE_AGAIN,
            commands = listOf(ImportedHrRuntimeCommand.ClearUnreachableTargetStatus),
        )
    }

    private fun startFailureEvent(
        missingCapabilities: Set<ImportedHrExecutionCapability>,
    ): ImportedHrRuntimeEvent {
        return when {
            missingCapabilities.size > 1 -> ImportedHrRuntimeEvent.MULTIPLE_START_CAPABILITIES_MISSING
            ImportedHrExecutionCapability.HEART_RATE_SIGNAL in missingCapabilities ->
                ImportedHrRuntimeEvent.HR_MISSING_AT_START

            ImportedHrExecutionCapability.TRAINER_CONTROL in missingCapabilities ->
                ImportedHrRuntimeEvent.TRAINER_CONTROL_MISSING_AT_START

            else -> ImportedHrRuntimeEvent.MULTIPLE_START_CAPABILITIES_MISSING
        }
    }

    private fun startFailureReason(
        missingCapabilities: Set<ImportedHrExecutionCapability>,
    ): ImportedHrRuntimeStartFailureReason {
        return when {
            missingCapabilities.size > 1 -> {
                ImportedHrRuntimeStartFailureReason.MULTIPLE_REQUIRED_CAPABILITIES_MISSING
            }

            ImportedHrExecutionCapability.HEART_RATE_SIGNAL in missingCapabilities -> {
                ImportedHrRuntimeStartFailureReason.HEART_RATE_SIGNAL_MISSING
            }

            ImportedHrExecutionCapability.TRAINER_CONTROL in missingCapabilities -> {
                ImportedHrRuntimeStartFailureReason.TRAINER_CONTROL_MISSING
            }

            else -> ImportedHrRuntimeStartFailureReason.MULTIPLE_REQUIRED_CAPABILITIES_MISSING
        }
    }
}

/**
 * Explicit v1 HR runner states for conservative bounded imported-HR control.
 */
sealed interface ImportedHrRuntimeState {
    val policy: ImportedHrExecutionPolicyV1

    data class Ready(
        override val policy: ImportedHrExecutionPolicyV1,
    ) : ImportedHrRuntimeState

    data class Running(
        override val policy: ImportedHrExecutionPolicyV1,
        override val currentPowerWatts: Int,
        override val increasesBlocked: Boolean,
    ) : ImportedHrActiveRuntimeState

    data class Fallback(
        override val policy: ImportedHrExecutionPolicyV1,
        val fallbackPowerWatts: Int,
    ) : ImportedHrActiveRuntimeState {
        override val currentPowerWatts: Int
            get() = fallbackPowerWatts

        override val increasesBlocked: Boolean
            get() = true
    }

    data class SafetyThrottle(
        override val policy: ImportedHrExecutionPolicyV1,
        val throttledPowerWatts: Int,
    ) : ImportedHrActiveRuntimeState {
        override val currentPowerWatts: Int
            get() = throttledPowerWatts

        override val increasesBlocked: Boolean
            get() = true
    }

    data class AtMaxPowerBelowTarget(
        override val policy: ImportedHrExecutionPolicyV1,
        override val currentPowerWatts: Int,
    ) : ImportedHrActiveRuntimeState {
        override val increasesBlocked: Boolean
            get() = true
    }

    data class AtMinPowerAboveTarget(
        override val policy: ImportedHrExecutionPolicyV1,
        override val currentPowerWatts: Int,
    ) : ImportedHrActiveRuntimeState {
        override val increasesBlocked: Boolean
            get() = false
    }

    data class FailedToStart(
        override val policy: ImportedHrExecutionPolicyV1,
        val reason: ImportedHrRuntimeStartFailureReason,
        val missingCapabilities: Set<ImportedHrExecutionCapability>,
    ) : ImportedHrRuntimeState

    data class Stopped(
        override val policy: ImportedHrExecutionPolicyV1,
        val reason: ImportedHrRuntimeStopReason,
        val requiresUserAcknowledgement: Boolean,
    ) : ImportedHrRuntimeState
}

/**
 * Active runner-owned HR states that still control or command trainer power.
 */
sealed interface ImportedHrActiveRuntimeState : ImportedHrRuntimeState {
    val currentPowerWatts: Int
    val increasesBlocked: Boolean
}

/**
 * Stable state-machine event vocabulary for imported HR runtime transitions.
 */
enum class ImportedHrRuntimeEvent {
    STARTED,
    HR_MISSING_AT_START,
    TRAINER_CONTROL_MISSING_AT_START,
    MULTIPLE_START_CAPABILITIES_MISSING,
    HR_SIGNAL_LOST,
    HR_SIGNAL_LOSS_FALLBACK_APPLIED,
    TRAINER_CONTROL_LOST,
    HR_CAP_BREACHED,
    HR_CAP_BREACH_PERSISTED,
    POWER_INCREASED,
    POWER_DECREASED,
    TARGET_UNREACHABLE_HIGH,
    TARGET_UNREACHABLE_LOW,
    TARGET_REACHABLE_AGAIN,
}

/**
 * Minimal runner actions emitted by the pure state model.
 */
sealed interface ImportedHrRuntimeCommand {
    data class SetPower(
        val watts: Int,
    ) : ImportedHrRuntimeCommand

    data object BlockIncrease : ImportedHrRuntimeCommand

    data object FailStart : ImportedHrRuntimeCommand

    data object StopWorkout : ImportedHrRuntimeCommand

    data object RequireUserAcknowledgement : ImportedHrRuntimeCommand

    data class ReportUnreachableTarget(
        val status: ImportedHrRuntimeUnreachableTargetStatus,
    ) : ImportedHrRuntimeCommand

    data object ClearUnreachableTargetStatus : ImportedHrRuntimeCommand
}

/**
 * Result of a single imported-HR runtime transition.
 */
data class ImportedHrRuntimeTransition(
    val fromState: ImportedHrRuntimeState,
    val toState: ImportedHrRuntimeState,
    val event: ImportedHrRuntimeEvent,
    val commands: List<ImportedHrRuntimeCommand>,
)

/**
 * Why an imported HR segment could not start under the current runtime
 * capability snapshot.
 */
enum class ImportedHrRuntimeStartFailureReason {
    HEART_RATE_SIGNAL_MISSING,
    TRAINER_CONTROL_MISSING,
    MULTIPLE_REQUIRED_CAPABILITIES_MISSING,
}

/**
 * Terminal reasons for imported HR execution in the first runner-owned slice.
 */
enum class ImportedHrRuntimeStopReason {
    SIGNAL_LOSS,
    TRAINER_CONTROL_LOST,
    HR_SAFETY_CAP_PERSISTED,
}

/**
 * Stable degraded-status vocabulary for imported-HR target reachability.
 */
enum class ImportedHrRuntimeUnreachableTargetStatus {
    AT_MAX_POWER_BELOW_TARGET,
    AT_MIN_POWER_ABOVE_TARGET,
}
