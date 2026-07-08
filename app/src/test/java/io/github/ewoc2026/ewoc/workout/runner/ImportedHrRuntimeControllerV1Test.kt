package io.github.ewoc2026.ewoc.workout.runner

import io.github.ewoc2026.ewoc.workout.HrCapBehavior
import io.github.ewoc2026.ewoc.workout.HrSignalLossBehavior
import io.github.ewoc2026.ewoc.workout.HrUnavailableAtStartBehavior
import io.github.ewoc2026.ewoc.workout.HrUnreachableTargetBehavior
import io.github.ewoc2026.ewoc.workout.ImportedHrExecutionCapability
import io.github.ewoc2026.ewoc.workout.ImportedHrExecutionCapabilitySnapshot
import io.github.ewoc2026.ewoc.workout.ImportedHrExecutionPolicyV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedHrRuntimeControllerV1Test {
    @Test
    fun startTransitionsIntoRunningStateWhenSnapshotIsReady() {
        val controller = ImportedHrRuntimeControllerV1(policy())

        val transition = controller.start(
            snapshot = ImportedHrExecutionCapabilitySnapshot(
                hasHeartRateSignal = true,
                hasTrainerControl = true,
            ),
        )

        assertEquals(ImportedHrRuntimeEvent.STARTED, transition.event)
        assertEquals(listOf(ImportedHrRuntimeCommand.SetPower(180)), transition.commands)
        val state = controller.state() as? ImportedHrRuntimeState.Running
            ?: throw AssertionError("Expected running state after successful start")
        assertEquals(180, state.currentPowerWatts)
        assertTrue(!state.increasesBlocked)
    }

    @Test
    fun signalLossFallsBackThenStopsOnNextTelemetryPass() {
        val controller = startedController()

        val fallback = controller.onTelemetry(
            heartRateBpm = null,
            hasTrainerControl = true,
            elapsedRealtimeMs = 1_000L,
        ) ?: throw AssertionError("Expected fallback transition")

        assertEquals(ImportedHrRuntimeEvent.HR_SIGNAL_LOST, fallback.event)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.SetPower(90),
                ImportedHrRuntimeCommand.BlockIncrease,
            ),
            fallback.commands,
        )
        assertEquals(
            ImportedHrRuntimeState.Fallback(
                policy = policy(),
                fallbackPowerWatts = 90,
            ),
            controller.state(),
        )

        val stop = controller.onTelemetry(
            heartRateBpm = null,
            hasTrainerControl = true,
            elapsedRealtimeMs = 2_000L,
        ) ?: throw AssertionError("Expected signal-loss stop transition")

        assertEquals(ImportedHrRuntimeEvent.HR_SIGNAL_LOSS_FALLBACK_APPLIED, stop.event)
        assertEquals(listOf(ImportedHrRuntimeCommand.StopWorkout), stop.commands)
        val state = controller.state() as? ImportedHrRuntimeState.Stopped
            ?: throw AssertionError("Expected stopped state after fallback stop")
        assertEquals(ImportedHrRuntimeStopReason.SIGNAL_LOSS, state.reason)
    }

    @Test
    fun zeroHeartRateFallsBackThenStopsOnNextTelemetryPass() {
        val controller = startedController()

        val fallback = controller.onTelemetry(
            heartRateBpm = 0,
            hasTrainerControl = true,
            elapsedRealtimeMs = 1_000L,
        ) ?: throw AssertionError("Expected zero-HR fallback transition")

        assertEquals(ImportedHrRuntimeEvent.HR_SIGNAL_LOST, fallback.event)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.SetPower(90),
                ImportedHrRuntimeCommand.BlockIncrease,
            ),
            fallback.commands,
        )

        val stop = controller.onTelemetry(
            heartRateBpm = 0,
            hasTrainerControl = true,
            elapsedRealtimeMs = 2_000L,
        ) ?: throw AssertionError("Expected zero-HR stop transition")

        assertEquals(ImportedHrRuntimeEvent.HR_SIGNAL_LOSS_FALLBACK_APPLIED, stop.event)
        assertEquals(listOf(ImportedHrRuntimeCommand.StopWorkout), stop.commands)
    }

    @Test
    fun trainerControlLossStopsImmediatelyFromRunningState() {
        val controller = startedController()

        val transition = controller.onTelemetry(
            heartRateBpm = 145,
            hasTrainerControl = false,
            elapsedRealtimeMs = 1_000L,
        ) ?: throw AssertionError("Expected trainer-control-loss transition")

        assertEquals(ImportedHrRuntimeEvent.TRAINER_CONTROL_LOST, transition.event)
        assertEquals(listOf(ImportedHrRuntimeCommand.StopWorkout), transition.commands)
        val state = controller.state() as? ImportedHrRuntimeState.Stopped
            ?: throw AssertionError("Expected stopped state after trainer-control loss")
        assertEquals(ImportedHrRuntimeStopReason.TRAINER_CONTROL_LOST, state.reason)
    }

    @Test
    fun persistentCapBreachThrottlesThenStops() {
        val controller = startedController()

        val throttle = controller.onTelemetry(
            heartRateBpm = 186,
            hasTrainerControl = true,
            elapsedRealtimeMs = 1_000L,
        ) ?: throw AssertionError("Expected safety-throttle transition")

        assertEquals(ImportedHrRuntimeEvent.HR_CAP_BREACHED, throttle.event)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.SetPower(72),
                ImportedHrRuntimeCommand.BlockIncrease,
            ),
            throttle.commands,
        )
        val throttledState = controller.state() as? ImportedHrRuntimeState.SafetyThrottle
            ?: throw AssertionError("Expected safety-throttle state")
        assertEquals(72, throttledState.throttledPowerWatts)

        val stop = controller.onTelemetry(
            heartRateBpm = 186,
            hasTrainerControl = true,
            elapsedRealtimeMs = 2_000L,
        ) ?: throw AssertionError("Expected persistent-cap stop transition")

        assertEquals(ImportedHrRuntimeEvent.HR_CAP_BREACH_PERSISTED, stop.event)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.StopWorkout,
                ImportedHrRuntimeCommand.RequireUserAcknowledgement,
            ),
            stop.commands,
        )
        val state = controller.state() as? ImportedHrRuntimeState.Stopped
            ?: throw AssertionError("Expected stopped state after persistent cap breach")
        assertEquals(ImportedHrRuntimeStopReason.HR_SAFETY_CAP_PERSISTED, state.reason)
        assertTrue(state.requiresUserAcknowledgement)
    }

    @Test
    fun inRangeTelemetryLeavesRunningStateUntouched() {
        val controller = startedController()

        val transition = controller.onTelemetry(
            heartRateBpm = 145,
            hasTrainerControl = true,
            elapsedRealtimeMs = 1_000L,
        )

        assertNull(transition)
        assertEquals(
            ImportedHrRuntimeState.Running(
                policy = policy(),
                currentPowerWatts = 180,
                increasesBlocked = false,
            ),
            controller.state(),
        )
    }

    @Test
    fun safetyThrottleRespectsCodeOwnedSafetyMaxPowerCap() {
        val controller = startedController(
            policy = policy(
                initialPowerWatts = 300,
                minPowerWatts = 120,
                signalLossPowerWatts = 220,
            ),
        )

        val throttle = controller.onTelemetry(
            heartRateBpm = 186,
            hasTrainerControl = true,
            elapsedRealtimeMs = 1_000L,
        ) ?: throw AssertionError("Expected safety-throttle transition")

        assertEquals(ImportedHrRuntimeEvent.HR_CAP_BREACHED, throttle.event)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.SetPower(100),
                ImportedHrRuntimeCommand.BlockIncrease,
            ),
            throttle.commands,
        )
    }

    @Test
    fun belowTargetIncreasesPowerInBoundedStepsWithHoldoff() {
        val controller = startedController()

        val firstIncrease = controller.onTelemetry(
            heartRateBpm = 130,
            hasTrainerControl = true,
            elapsedRealtimeMs = 1_000L,
        ) ?: throw AssertionError("Expected first below-target increase")

        assertEquals(ImportedHrRuntimeEvent.POWER_INCREASED, firstIncrease.event)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.SetPower(185),
                ImportedHrRuntimeCommand.ClearUnreachableTargetStatus,
            ),
            firstIncrease.commands,
        )
        assertEquals(185, (controller.state() as ImportedHrRuntimeState.Running).currentPowerWatts)

        val blockedByHoldoff = controller.onTelemetry(
            heartRateBpm = 130,
            hasTrainerControl = true,
            elapsedRealtimeMs = 10_000L,
        )

        assertNull(blockedByHoldoff)
        assertEquals(185, (controller.state() as ImportedHrRuntimeState.Running).currentPowerWatts)

        val secondIncrease = controller.onTelemetry(
            heartRateBpm = 130,
            hasTrainerControl = true,
            elapsedRealtimeMs = 16_000L,
        ) ?: throw AssertionError("Expected second below-target increase after holdoff")

        assertEquals(ImportedHrRuntimeEvent.POWER_INCREASED, secondIncrease.event)
        assertEquals(190, (controller.state() as ImportedHrRuntimeState.Running).currentPowerWatts)
    }

    @Test
    fun aboveTargetDecreasesPowerInBoundedStepsWithHoldoff() {
        val controller = startedController()

        val firstDecrease = controller.onTelemetry(
            heartRateBpm = 155,
            hasTrainerControl = true,
            elapsedRealtimeMs = 1_000L,
        ) ?: throw AssertionError("Expected first above-target decrease")

        assertEquals(ImportedHrRuntimeEvent.POWER_DECREASED, firstDecrease.event)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.SetPower(170),
                ImportedHrRuntimeCommand.ClearUnreachableTargetStatus,
            ),
            firstDecrease.commands,
        )
        assertEquals(170, (controller.state() as ImportedHrRuntimeState.Running).currentPowerWatts)

        val blockedByHoldoff = controller.onTelemetry(
            heartRateBpm = 155,
            hasTrainerControl = true,
            elapsedRealtimeMs = 10_000L,
        )

        assertNull(blockedByHoldoff)
        assertEquals(170, (controller.state() as ImportedHrRuntimeState.Running).currentPowerWatts)

        val secondDecrease = controller.onTelemetry(
            heartRateBpm = 155,
            hasTrainerControl = true,
            elapsedRealtimeMs = 11_000L,
        ) ?: throw AssertionError("Expected second above-target decrease after holdoff")

        assertEquals(ImportedHrRuntimeEvent.POWER_DECREASED, secondDecrease.event)
        assertEquals(160, (controller.state() as ImportedHrRuntimeState.Running).currentPowerWatts)
    }

    @Test
    fun belowTargetAtMaxPowerReportsUnreachableHighStatus() {
        val customPolicy = policy(
            initialPowerWatts = 255,
            maxPowerWatts = 260,
        )
        val controller = startedController(policy = customPolicy)

        controller.onTelemetry(
            heartRateBpm = 130,
            hasTrainerControl = true,
            elapsedRealtimeMs = 1_000L,
        ) ?: throw AssertionError("Expected increase to max power")

        val unreachable = controller.onTelemetry(
            heartRateBpm = 130,
            hasTrainerControl = true,
            elapsedRealtimeMs = 17_000L,
        ) ?: throw AssertionError("Expected unreachable-high status")

        assertEquals(ImportedHrRuntimeEvent.TARGET_UNREACHABLE_HIGH, unreachable.event)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.ReportUnreachableTarget(
                    ImportedHrRuntimeUnreachableTargetStatus.AT_MAX_POWER_BELOW_TARGET,
                ),
            ),
            unreachable.commands,
        )
        assertEquals(
            ImportedHrRuntimeState.AtMaxPowerBelowTarget(
                policy = customPolicy,
                currentPowerWatts = 260,
            ),
            controller.state(),
        )
    }

    @Test
    fun aboveTargetDecreasesPowerAndReportsUnreachableLowAtMinPower() {
        val customPolicy = policy(
            initialPowerWatts = 125,
            minPowerWatts = 120,
        )
        val controller = startedController(policy = customPolicy)

        controller.onTelemetry(
            heartRateBpm = 155,
            hasTrainerControl = true,
            elapsedRealtimeMs = 1_000L,
        ) ?: throw AssertionError("Expected decrease to min power")

        val unreachable = controller.onTelemetry(
            heartRateBpm = 155,
            hasTrainerControl = true,
            elapsedRealtimeMs = 3_000L,
        ) ?: throw AssertionError("Expected unreachable-low status")

        assertEquals(ImportedHrRuntimeEvent.TARGET_UNREACHABLE_LOW, unreachable.event)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.ReportUnreachableTarget(
                    ImportedHrRuntimeUnreachableTargetStatus.AT_MIN_POWER_ABOVE_TARGET,
                ),
            ),
            unreachable.commands,
        )
        assertEquals(
            ImportedHrRuntimeState.AtMinPowerAboveTarget(
                policy = customPolicy,
                currentPowerWatts = 120,
            ),
            controller.state(),
        )
    }

    @Test
    fun unreachableStatusClearsWhenHeartRateReturnsIntoTargetBand() {
        val customPolicy = policy(
            initialPowerWatts = 255,
            maxPowerWatts = 260,
        )
        val controller = startedController(policy = customPolicy)

        controller.onTelemetry(
            heartRateBpm = 130,
            hasTrainerControl = true,
            elapsedRealtimeMs = 1_000L,
        )
        controller.onTelemetry(
            heartRateBpm = 130,
            hasTrainerControl = true,
            elapsedRealtimeMs = 17_000L,
        )

        val cleared = controller.onTelemetry(
            heartRateBpm = 145,
            hasTrainerControl = true,
            elapsedRealtimeMs = 18_000L,
        ) ?: throw AssertionError("Expected unreachable status clear")

        assertEquals(ImportedHrRuntimeEvent.TARGET_REACHABLE_AGAIN, cleared.event)
        assertEquals(
            listOf(ImportedHrRuntimeCommand.ClearUnreachableTargetStatus),
            cleared.commands,
        )
        assertEquals(260, (controller.state() as ImportedHrRuntimeState.Running).currentPowerWatts)
    }

    @Test
    fun minPowerUnreachableClearsInRangeAndAllowsLaterNormalIncrease() {
        val customPolicy = policy(
            initialPowerWatts = 125,
            minPowerWatts = 120,
            maxPowerWatts = 260,
        )
        val controller = startedController(policy = customPolicy)

        controller.onTelemetry(
            heartRateBpm = 155,
            hasTrainerControl = true,
            elapsedRealtimeMs = 1_000L,
        ) ?: throw AssertionError("Expected decrease to min power")

        val unreachable = controller.onTelemetry(
            heartRateBpm = 155,
            hasTrainerControl = true,
            elapsedRealtimeMs = 2_000L,
        ) ?: throw AssertionError("Expected unreachable-low status")

        assertEquals(ImportedHrRuntimeEvent.TARGET_UNREACHABLE_LOW, unreachable.event)
        assertEquals(
            ImportedHrRuntimeState.AtMinPowerAboveTarget(
                policy = customPolicy,
                currentPowerWatts = 120,
            ),
            controller.state(),
        )

        val cleared = controller.onTelemetry(
            heartRateBpm = 145,
            hasTrainerControl = true,
            elapsedRealtimeMs = 3_000L,
        ) ?: throw AssertionError("Expected unreachable-low clear in range")

        assertEquals(ImportedHrRuntimeEvent.TARGET_REACHABLE_AGAIN, cleared.event)
        assertEquals(
            listOf(ImportedHrRuntimeCommand.ClearUnreachableTargetStatus),
            cleared.commands,
        )
        assertEquals(
            ImportedHrRuntimeState.Running(
                policy = customPolicy,
                currentPowerWatts = 120,
                increasesBlocked = false,
            ),
            controller.state(),
        )

        val increased = controller.onTelemetry(
            heartRateBpm = 130,
            hasTrainerControl = true,
            elapsedRealtimeMs = 4_000L,
        ) ?: throw AssertionError("Expected normal below-target increase after clear")

        assertEquals(ImportedHrRuntimeEvent.POWER_INCREASED, increased.event)
        assertEquals(
            listOf(
                ImportedHrRuntimeCommand.SetPower(125),
                ImportedHrRuntimeCommand.ClearUnreachableTargetStatus,
            ),
            increased.commands,
        )
        assertEquals(125, (controller.state() as ImportedHrRuntimeState.Running).currentPowerWatts)
    }

    private fun startedController(
        policy: ImportedHrExecutionPolicyV1 = policy(),
    ): ImportedHrRuntimeControllerV1 {
        return ImportedHrRuntimeControllerV1(policy).also { controller ->
            controller.start(
                snapshot = ImportedHrExecutionCapabilitySnapshot(
                    hasHeartRateSignal = true,
                    hasTrainerControl = true,
                ),
            )
        }
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
