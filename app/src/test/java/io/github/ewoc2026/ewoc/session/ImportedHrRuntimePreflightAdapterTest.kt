package io.github.ewoc2026.ewoc.session

import io.github.ewoc2026.ewoc.workout.HrCapBehavior
import io.github.ewoc2026.ewoc.workout.HrSignalLossBehavior
import io.github.ewoc2026.ewoc.workout.HrUnavailableAtStartBehavior
import io.github.ewoc2026.ewoc.workout.HrUnreachableTargetBehavior
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkoutCanonicalMetadata
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkoutControl
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkoutStep
import io.github.ewoc2026.ewoc.workout.ImportedHrExecutionCapability
import io.github.ewoc2026.ewoc.workout.ImportedHrExecutionCapabilitySnapshot
import io.github.ewoc2026.ewoc.workout.runner.ImportedHrRuntimeCommand
import io.github.ewoc2026.ewoc.workout.runner.ImportedHrRuntimeEvent
import io.github.ewoc2026.ewoc.workout.runner.ImportedHrRuntimeStartFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedHrRuntimePreflightAdapterTest {
    @Test
    fun reportsBlockedStartUsingRunnerStateMachineVocabulary() {
        val result = ImportedHrRuntimePreflightAdapter.evaluate(
            workout = canonicalHeartRateWorkout(),
            step = heartRateStep(),
            snapshot = ImportedHrExecutionCapabilitySnapshot(
                hasHeartRateSignal = false,
                hasTrainerControl = true,
            ),
        )

        val blocked = result as? ImportedHrRuntimePreflightResult.Blocked
            ?: throw AssertionError("Expected blocked preflight, got $result")
        assertEquals(
            ImportedHrRuntimeEvent.HR_MISSING_AT_START,
            blocked.transition.event,
        )
        assertEquals(
            ImportedHrRuntimeStartFailureReason.HEART_RATE_SIGNAL_MISSING,
            blocked.failureState.reason,
        )
        assertEquals(
            setOf(ImportedHrExecutionCapability.HEART_RATE_SIGNAL),
            blocked.failureState.missingCapabilities,
        )
        assertEquals(
            listOf(ImportedHrRuntimeCommand.FailStart),
            blocked.transition.commands,
        )
        assertEquals(
            setOf(
                ImportedHrExecutionCapability.HEART_RATE_SIGNAL,
                ImportedHrExecutionCapability.TRAINER_CONTROL,
            ),
            blocked.policy.requiredCapabilities,
        )
    }

    @Test
    fun reportsReadyStartWhenSnapshotSatisfiesPolicyRequirements() {
        val result = ImportedHrRuntimePreflightAdapter.evaluate(
            workout = canonicalHeartRateWorkout(),
            step = heartRateStep(),
            snapshot = ImportedHrExecutionCapabilitySnapshot(
                hasHeartRateSignal = true,
                hasTrainerControl = true,
            ),
        )

        val ready = result as? ImportedHrRuntimePreflightResult.Ready
            ?: throw AssertionError("Expected ready preflight, got $result")
        assertEquals(ImportedHrRuntimeEvent.STARTED, ready.transition.event)
        assertEquals(
            listOf(ImportedHrRuntimeCommand.SetPower(180)),
            ready.transition.commands,
        )
        assertEquals(180, ready.policy.initialPowerWatts)
        assertTrue(
            ready.policy.unavailableAtStartBehavior == HrUnavailableAtStartBehavior.FAIL_START,
        )
        assertTrue(
            ready.policy.signalLossBehavior == HrSignalLossBehavior.FALLBACK_THEN_STOP,
        )
        assertTrue(
            ready.policy.capBehavior == HrCapBehavior.THROTTLE_THEN_STOP,
        )
        assertTrue(
            ready.policy.unreachableTargetBehavior ==
                HrUnreachableTargetBehavior.HOLD_AT_BOUND_WITH_STATUS,
        )
    }

    @Test
    fun preservesMissingCanonicalControlAsPreflightBoundary() {
        val workout = canonicalHeartRateWorkout().copy(canonicalMetadata = null)

        val result = ImportedHrRuntimePreflightAdapter.evaluate(
            workout = workout,
            step = workout.steps.single() as ImportedErgoWorkoutStep.HeartRateSteady,
            snapshot = ImportedHrExecutionCapabilitySnapshot(
                hasHeartRateSignal = true,
                hasTrainerControl = true,
            ),
        )

        assertEquals(
            ImportedHrRuntimePreflightResult.MissingCanonicalControl(
                snapshot = ImportedHrExecutionCapabilitySnapshot(
                    hasHeartRateSignal = true,
                    hasTrainerControl = true,
                ),
            ),
            result,
        )
    }

    private fun canonicalHeartRateWorkout(): ImportedErgoWorkout {
        return ImportedErgoWorkout(
            title = "Canonical HR builder",
            description = null,
            steps = listOf(heartRateStep()),
            totalDurationSec = 600,
            canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                uid = null,
                revision = null,
                control = ImportedErgoWorkoutControl(
                    initialPowerWatts = 180,
                    minPowerWatts = 120,
                    maxPowerWatts = 260,
                    signalLossPowerWatts = 150,
                    hrUpperCapBpm = 185,
                ),
                messages = emptyList(),
            ),
        )
    }

    private fun heartRateStep(): ImportedErgoWorkoutStep.HeartRateSteady {
        return ImportedErgoWorkoutStep.HeartRateSteady(
            stepIndex = 0,
            startOffsetSec = 0,
            durationSec = 600,
            lowBpm = 140,
            highBpm = 150,
            initialPowerWatts = 180,
            minPowerWatts = 120,
            maxPowerWatts = 260,
            signalLossPowerWatts = 150,
        )
    }
}
