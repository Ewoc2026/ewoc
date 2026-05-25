package com.example.ergometerapp.workout.runner

import com.example.ergometerapp.workout.HrCapBehavior
import com.example.ergometerapp.workout.HrSignalLossBehavior
import com.example.ergometerapp.workout.HrUnavailableAtStartBehavior
import com.example.ergometerapp.workout.HrUnreachableTargetBehavior
import com.example.ergometerapp.workout.ImportedHrExecutionCapability
import com.example.ergometerapp.workout.ImportedHrExecutionCapabilitySnapshot
import com.example.ergometerapp.workout.ImportedHrExecutionPolicyV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedHrRuntimeStateMachineV1Test {
    @Test
    fun startFailsWhenHeartRateSignalIsMissing() {
        val transition = ImportedHrRuntimeStateMachineV1.start(
            policy = policy(),
            snapshot = ImportedHrExecutionCapabilitySnapshot(
                hasHeartRateSignal = false,
                hasTrainerControl = true,
            ),
        )

        val failed = transition.toState as? ImportedHrRuntimeState.FailedToStart
            ?: throw AssertionError("Expected failed-to-start state, got ${transition.toState}")
        assertEquals(
            ImportedHrRuntimeEvent.HR_MISSING_AT_START,
            transition.event,
        )
        assertEquals(
            ImportedHrRuntimeStartFailureReason.HEART_RATE_SIGNAL_MISSING,
            failed.reason,
        )
        assertEquals(
            setOf(ImportedHrExecutionCapability.HEART_RATE_SIGNAL),
            failed.missingCapabilities,
        )
        assertEquals(
            listOf(ImportedHrRuntimeCommand.FailStart),
            transition.commands,
        )
    }

    @Test
    fun startFailsWhenTrainerControlIsMissing() {
        val transition = ImportedHrRuntimeStateMachineV1.start(
            policy = policy(),
            snapshot = ImportedHrExecutionCapabilitySnapshot(
                hasHeartRateSignal = true,
                hasTrainerControl = false,
            ),
        )

        val failed = transition.toState as? ImportedHrRuntimeState.FailedToStart
            ?: throw AssertionError("Expected failed-to-start state, got ${transition.toState}")
        assertEquals(
            ImportedHrRuntimeEvent.TRAINER_CONTROL_MISSING_AT_START,
            transition.event,
        )
        assertEquals(
            ImportedHrRuntimeStartFailureReason.TRAINER_CONTROL_MISSING,
            failed.reason,
        )
        assertEquals(
            setOf(ImportedHrExecutionCapability.TRAINER_CONTROL),
            failed.missingCapabilities,
        )
    }

    @Test
    fun startFailsWhenMultipleCapabilitiesAreMissing() {
        val transition = ImportedHrRuntimeStateMachineV1.start(
            policy = policy(),
            snapshot = ImportedHrExecutionCapabilitySnapshot(
                hasHeartRateSignal = false,
                hasTrainerControl = false,
            ),
        )

        val failed = transition.toState as? ImportedHrRuntimeState.FailedToStart
            ?: throw AssertionError("Expected failed-to-start state, got ${transition.toState}")
        assertEquals(
            ImportedHrRuntimeEvent.MULTIPLE_START_CAPABILITIES_MISSING,
            transition.event,
        )
        assertEquals(
            ImportedHrRuntimeStartFailureReason.MULTIPLE_REQUIRED_CAPABILITIES_MISSING,
            failed.reason,
        )
        assertEquals(
            setOf(
                ImportedHrExecutionCapability.HEART_RATE_SIGNAL,
                ImportedHrExecutionCapability.TRAINER_CONTROL,
            ),
            failed.missingCapabilities,
        )
    }

    @Test
    fun startEntersRunningStateAtInitialPowerWhenCapabilitiesAreReady() {
        val transition = ImportedHrRuntimeStateMachineV1.start(
            policy = policy(),
            snapshot = ImportedHrExecutionCapabilitySnapshot(
                hasHeartRateSignal = true,
                hasTrainerControl = true,
            ),
        )

        val running = transition.toState as? ImportedHrRuntimeState.Running
            ?: throw AssertionError("Expected running state, got ${transition.toState}")
        assertEquals(ImportedHrRuntimeEvent.STARTED, transition.event)
        assertEquals(180, running.currentPowerWatts)
        assertFalse(running.increasesBlocked)
        assertEquals(
            listOf(ImportedHrRuntimeCommand.SetPower(180)),
            transition.commands,
        )
    }

    @Test
    fun signalLossEntersFallbackAtSignalLossPowerAndBlocksIncrease() {
        val transition = ImportedHrRuntimeStateMachineV1.onSignalLost(
            state = ImportedHrRuntimeState.Running(
                policy = policy(),
                currentPowerWatts = 200,
                increasesBlocked = false,
            ),
        )

        val fallback = transition.toState as? ImportedHrRuntimeState.Fallback
            ?: throw AssertionError("Expected fallback state, got ${transition.toState}")
        assertEquals(ImportedHrRuntimeEvent.HR_SIGNAL_LOST, transition.event)
        assertEquals(90, fallback.fallbackPowerWatts)
        assertTrue(fallback.increasesBlocked)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.SetPower(90),
                ImportedHrRuntimeCommand.BlockIncrease,
            ),
            transition.commands,
        )
    }

    @Test
    fun signalLossFallbackRespectsCodeOwnedSafetyMaxPowerCap() {
        val transition = ImportedHrRuntimeStateMachineV1.onSignalLost(
            state = ImportedHrRuntimeState.Running(
                policy = policy(
                    initialPowerWatts = 300,
                    signalLossPowerWatts = 220,
                ),
                currentPowerWatts = 220,
                increasesBlocked = false,
            ),
        )

        val fallback = transition.toState as? ImportedHrRuntimeState.Fallback
            ?: throw AssertionError("Expected fallback state, got ${transition.toState}")
        assertEquals(100, fallback.fallbackPowerWatts)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.SetPower(100),
                ImportedHrRuntimeCommand.BlockIncrease,
            ),
            transition.commands,
        )
    }

    @Test
    fun trainerControlLossStopsImmediatelyFromActiveState() {
        val transition = ImportedHrRuntimeStateMachineV1.onTrainerControlLost(
            state = ImportedHrRuntimeState.Fallback(
                policy = policy(),
                fallbackPowerWatts = 150,
            ),
        )

        val stopped = transition.toState as? ImportedHrRuntimeState.Stopped
            ?: throw AssertionError("Expected stopped state, got ${transition.toState}")
        assertEquals(ImportedHrRuntimeEvent.TRAINER_CONTROL_LOST, transition.event)
        assertEquals(ImportedHrRuntimeStopReason.TRAINER_CONTROL_LOST, stopped.reason)
        assertFalse(stopped.requiresUserAcknowledgement)
        assertEquals(
            listOf(ImportedHrRuntimeCommand.StopWorkout),
            transition.commands,
        )
    }

    @Test
    fun appliedSignalLossFallbackStopsWorkoutOnNextTransition() {
        val transition = ImportedHrRuntimeStateMachineV1.onFallbackApplied(
            state = ImportedHrRuntimeState.Fallback(
                policy = policy(),
                fallbackPowerWatts = 150,
            ),
        )

        val stopped = transition.toState as? ImportedHrRuntimeState.Stopped
            ?: throw AssertionError("Expected stopped state, got ${transition.toState}")
        assertEquals(
            ImportedHrRuntimeEvent.HR_SIGNAL_LOSS_FALLBACK_APPLIED,
            transition.event,
        )
        assertEquals(ImportedHrRuntimeStopReason.SIGNAL_LOSS, stopped.reason)
        assertFalse(stopped.requiresUserAcknowledgement)
        assertEquals(
            listOf(ImportedHrRuntimeCommand.StopWorkout),
            transition.commands,
        )
    }

    @Test
    fun safetyCapBreachReducesPowerWithoutAllowingAnIncrease() {
        val transition = ImportedHrRuntimeStateMachineV1.onSafetyCapBreached(
            state = ImportedHrRuntimeState.Running(
                policy = policy(),
                currentPowerWatts = 190,
                increasesBlocked = false,
            ),
            requestedThrottlePowerWatts = 250,
        )

        val safetyThrottle = transition.toState as? ImportedHrRuntimeState.SafetyThrottle
            ?: throw AssertionError("Expected safety-throttle state, got ${transition.toState}")
        assertEquals(ImportedHrRuntimeEvent.HR_CAP_BREACHED, transition.event)
        assertEquals(190, safetyThrottle.throttledPowerWatts)
        assertTrue(safetyThrottle.increasesBlocked)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.SetPower(190),
                ImportedHrRuntimeCommand.BlockIncrease,
            ),
            transition.commands,
        )
    }

    @Test
    fun persistentSafetyCapBreachStopsAndRequiresAcknowledgement() {
        val transition = ImportedHrRuntimeStateMachineV1.onSafetyCapPersisted(
            state = ImportedHrRuntimeState.SafetyThrottle(
                policy = policy(),
                throttledPowerWatts = 160,
            ),
        )

        val stopped = transition.toState as? ImportedHrRuntimeState.Stopped
            ?: throw AssertionError("Expected stopped state, got ${transition.toState}")
        assertEquals(ImportedHrRuntimeEvent.HR_CAP_BREACH_PERSISTED, transition.event)
        assertEquals(ImportedHrRuntimeStopReason.HR_SAFETY_CAP_PERSISTED, stopped.reason)
        assertTrue(stopped.requiresUserAcknowledgement)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.StopWorkout,
                ImportedHrRuntimeCommand.RequireUserAcknowledgement,
            ),
            transition.commands,
        )
    }

    @Test
    fun powerIncreaseReturnsRunningStateAndClearsUnreachableStatus() {
        val transition = ImportedHrRuntimeStateMachineV1.onPowerIncreased(
            state = ImportedHrRuntimeState.AtMinPowerAboveTarget(
                policy = policy(),
                currentPowerWatts = 120,
            ),
            nextPowerWatts = 125,
        )

        val running = transition.toState as? ImportedHrRuntimeState.Running
            ?: throw AssertionError("Expected running state, got ${transition.toState}")
        assertEquals(ImportedHrRuntimeEvent.POWER_INCREASED, transition.event)
        assertEquals(125, running.currentPowerWatts)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.SetPower(125),
                ImportedHrRuntimeCommand.ClearUnreachableTargetStatus,
            ),
            transition.commands,
        )
    }

    @Test
    fun belowTargetAtMaxPowerSurfacesUnreachableHighStatus() {
        val transition = ImportedHrRuntimeStateMachineV1.onTargetUnreachableAtMaxPower(
            state = ImportedHrRuntimeState.Running(
                policy = policy(),
                currentPowerWatts = 260,
                increasesBlocked = false,
            ),
        )

        val unreachable = transition.toState as? ImportedHrRuntimeState.AtMaxPowerBelowTarget
            ?: throw AssertionError("Expected max-power unreachable state, got ${transition.toState}")
        assertEquals(ImportedHrRuntimeEvent.TARGET_UNREACHABLE_HIGH, transition.event)
        assertEquals(260, unreachable.currentPowerWatts)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.ReportUnreachableTarget(
                    ImportedHrRuntimeUnreachableTargetStatus.AT_MAX_POWER_BELOW_TARGET,
                ),
            ),
            transition.commands,
        )
    }

    @Test
    fun reachableAgainClearsUnreachableStatusWithoutChangingPower() {
        val transition = ImportedHrRuntimeStateMachineV1.onTargetReachableAgain(
            state = ImportedHrRuntimeState.AtMaxPowerBelowTarget(
                policy = policy(),
                currentPowerWatts = 260,
            ),
        )

        val running = transition.toState as? ImportedHrRuntimeState.Running
            ?: throw AssertionError("Expected running state, got ${transition.toState}")
        assertEquals(ImportedHrRuntimeEvent.TARGET_REACHABLE_AGAIN, transition.event)
        assertEquals(260, running.currentPowerWatts)
        assertEquals(
            listOf(ImportedHrRuntimeCommand.ClearUnreachableTargetStatus),
            transition.commands,
        )
    }

    private fun policy(
        initialPowerWatts: Int = 180,
        minPowerWatts: Int = 120,
        maxPowerWatts: Int = 260,
        signalLossPowerWatts: Int = 150,
    ): ImportedHrExecutionPolicyV1 {
        return ImportedHrExecutionPolicyV1(
            targetLowBpm = 140,
            targetHighBpm = 150,
            initialPowerWatts = initialPowerWatts,
            minPowerWatts = minPowerWatts,
            maxPowerWatts = maxPowerWatts,
            signalLossPowerWatts = signalLossPowerWatts,
            hrUpperCapBpm = 185,
            requiredCapabilities = setOf(
                ImportedHrExecutionCapability.HEART_RATE_SIGNAL,
                ImportedHrExecutionCapability.TRAINER_CONTROL,
            ),
            unavailableAtStartBehavior = HrUnavailableAtStartBehavior.FAIL_START,
            signalLossBehavior = HrSignalLossBehavior.FALLBACK_THEN_STOP,
            capBehavior = HrCapBehavior.THROTTLE_THEN_STOP,
            unreachableTargetBehavior = HrUnreachableTargetBehavior.HOLD_AT_BOUND_WITH_STATUS,
        )
    }
}
