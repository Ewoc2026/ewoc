package io.github.ewoc2026.ewoc.session

import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkoutExecutionPolicy
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkoutStep
import io.github.ewoc2026.ewoc.workout.ImportedHrExecutionCapabilitySnapshot
import io.github.ewoc2026.ewoc.workout.ImportedHrExecutionPolicyResolution
import io.github.ewoc2026.ewoc.workout.ImportedHrExecutionPolicyV1
import io.github.ewoc2026.ewoc.workout.runner.ImportedHrRuntimeState
import io.github.ewoc2026.ewoc.workout.runner.ImportedHrRuntimeStateMachineV1
import io.github.ewoc2026.ewoc.workout.runner.ImportedHrRuntimeTransition

/**
 * Bridges imported-HR policy resolution into the runner-owned start-state
 * machine so session preflight and later runtime adoption share the same
 * capability-gating semantics.
 */
internal object ImportedHrRuntimePreflightAdapter {
    /**
     * Evaluates whether an imported HR segment can enter the runner start path
     * under the current runtime capability snapshot.
     */
    fun evaluate(
        workout: ImportedErgoWorkout,
        step: ImportedErgoWorkoutStep.HeartRateSteady,
        snapshot: ImportedHrExecutionCapabilitySnapshot,
    ): ImportedHrRuntimePreflightResult {
        return when (
            val resolution = ImportedErgoWorkoutExecutionPolicy.resolveHeartRateExecutionPolicy(
                workout = workout,
                step = step,
            )
        ) {
            ImportedHrExecutionPolicyResolution.MissingCanonicalControl -> {
                ImportedHrRuntimePreflightResult.MissingCanonicalControl(snapshot = snapshot)
            }

            is ImportedHrExecutionPolicyResolution.Available -> {
                val transition = ImportedHrRuntimeStateMachineV1.start(
                    policy = resolution.policy,
                    snapshot = snapshot,
                )
                when (val toState = transition.toState) {
                    is ImportedHrRuntimeState.Running -> {
                        ImportedHrRuntimePreflightResult.Ready(
                            snapshot = snapshot,
                            policy = resolution.policy,
                            transition = transition,
                        )
                    }

                    is ImportedHrRuntimeState.FailedToStart -> {
                        ImportedHrRuntimePreflightResult.Blocked(
                            snapshot = snapshot,
                            policy = resolution.policy,
                            transition = transition,
                            failureState = toState,
                        )
                    }

                    else -> {
                        error(
                            "Imported HR start preflight emitted unexpected state " +
                                "${toState::class.simpleName}",
                        )
                    }
                }
            }
        }
    }
}

/**
 * Result of adapting imported-HR start preconditions into the runner-owned
 * start-state vocabulary.
 */
internal sealed interface ImportedHrRuntimePreflightResult {
    val snapshot: ImportedHrExecutionCapabilitySnapshot

    data class MissingCanonicalControl(
        override val snapshot: ImportedHrExecutionCapabilitySnapshot,
    ) : ImportedHrRuntimePreflightResult

    sealed interface PolicyBacked : ImportedHrRuntimePreflightResult {
        val policy: ImportedHrExecutionPolicyV1
        val transition: ImportedHrRuntimeTransition
    }

    data class Ready(
        override val snapshot: ImportedHrExecutionCapabilitySnapshot,
        override val policy: ImportedHrExecutionPolicyV1,
        override val transition: ImportedHrRuntimeTransition,
    ) : PolicyBacked

    data class Blocked(
        override val snapshot: ImportedHrExecutionCapabilitySnapshot,
        override val policy: ImportedHrExecutionPolicyV1,
        override val transition: ImportedHrRuntimeTransition,
        val failureState: ImportedHrRuntimeState.FailedToStart,
    ) : PolicyBacked
}
