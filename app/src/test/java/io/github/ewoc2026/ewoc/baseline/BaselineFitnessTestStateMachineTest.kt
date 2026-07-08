package io.github.ewoc2026.ewoc.baseline

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineFitnessTestStateMachineTest {
    @Test
    fun startCreatesPrecheckWithComputedStartWatts() {
        val transition = BaselineFitnessTestStateMachine.start(priorFtpWatts = 250)

        val precheck = transition.toState as? BaselineFitnessTestState.Precheck
            ?: throw AssertionError("Expected precheck state, got ${transition.toState}")
        assertEquals(BaselineFitnessTestStateEvent.START_REQUESTED, transition.event)
        assertEquals(115, precheck.startWatts)
        assertTrue(transition.commands.isEmpty())
    }

    @Test
    fun precheckMovesToUnavailableWhenPowerTelemetryIsMissing() {
        val transition = BaselineFitnessTestStateMachine.onPowerTelemetryUnavailable(
            BaselineFitnessTestState.Precheck(startWatts = 100),
        )

        val unavailable = transition.toState as? BaselineFitnessTestState.Unavailable
            ?: throw AssertionError("Expected unavailable state, got ${transition.toState}")
        assertEquals(
            BaselineFitnessTestUnavailableReason.POWER_TELEMETRY_UNAVAILABLE,
            unavailable.reason,
        )
    }

    @Test
    fun precheckRequestsControlWhenTrainerControlIsAvailable() {
        val transition = BaselineFitnessTestStateMachine.onPrecheckPassed(
            state = BaselineFitnessTestState.Precheck(startWatts = 100),
            trainerControlAvailable = true,
        )

        val requestingControl = transition.toState as? BaselineFitnessTestState.RequestingControl
            ?: throw AssertionError("Expected requesting-control state, got ${transition.toState}")
        assertEquals(100, requestingControl.startWatts)
        assertEquals(
            listOf(BaselineFitnessTestCommand.RequestTrainerControl),
            transition.commands,
        )
    }

    @Test
    fun failedControlGrantFallsBackToAdvisoryPromptWhilePowerRemainsAvailable() {
        val transition = BaselineFitnessTestStateMachine.onControlGrantFailed(
            state = BaselineFitnessTestState.RequestingControl(startWatts = 100),
            powerTelemetryStillAvailable = true,
        )

        val prompt = transition.toState as? BaselineFitnessTestState.AdvisoryFallbackPrompt
            ?: throw AssertionError("Expected advisory prompt, got ${transition.toState}")
        assertEquals(100, prompt.startWatts)
        assertEquals(
            listOf(BaselineFitnessTestCommand.ShowAdvisoryFallbackPrompt),
            transition.commands,
        )
    }

    @Test
    fun acceptedAdvisoryFallbackStartsWarmupWithoutErgCommand() {
        val transition = BaselineFitnessTestStateMachine.acceptAdvisoryFallback(
            state = BaselineFitnessTestState.AdvisoryFallbackPrompt(startWatts = 100),
            startedAt = Instant.parse("2026-03-15T10:00:00Z"),
        )

        val warmup = transition.toState as? BaselineFitnessTestState.Warmup
            ?: throw AssertionError("Expected warmup state, got ${transition.toState}")
        assertEquals(BaselineFitnessTestControlMode.ADVISORY, warmup.controlMode)
        assertEquals(
            listOf(BaselineFitnessTestCommand.HideAdvisoryFallbackPrompt),
            transition.commands,
        )
    }

    @Test
    fun decliningAdvisoryFallbackCancelsWithExplicitStopReason() {
        val transition = BaselineFitnessTestStateMachine.declineAdvisoryFallback(
            BaselineFitnessTestState.AdvisoryFallbackPrompt(startWatts = 100),
        )

        val cancelled = transition.toState as? BaselineFitnessTestState.Cancelled
            ?: throw AssertionError("Expected cancelled state, got ${transition.toState}")
        assertEquals(
            BaselineFitnessTestStopReason.CONTROL_GRANT_DECLINED,
            cancelled.stopReason,
        )
    }

    @Test
    fun deviceDisconnectInErgModeCancelsInsteadOfEnteringStopping() {
        val transition = BaselineFitnessTestStateMachine.stopDuringRamp(
            state = BaselineFitnessTestState.RampActive(
                controlMode = BaselineFitnessTestControlMode.ERG,
                startedAt = Instant.parse("2026-03-15T10:00:00Z"),
                rampStartedAt = Instant.parse("2026-03-15T10:05:00Z"),
                startWatts = 100,
            ),
            stopReason = BaselineFitnessTestStopReason.DEVICE_DISCONNECT,
            allowCooldown = true,
            stoppedAt = Instant.parse("2026-03-15T10:12:00Z"),
        )

        val cancelled = transition.toState as? BaselineFitnessTestState.Cancelled
            ?: throw AssertionError("Expected cancelled state, got ${transition.toState}")
        assertEquals(
            BaselineFitnessTestStopReason.CONTROL_LOST_MID_TEST,
            cancelled.stopReason,
        )
        assertEquals(
            listOf(BaselineFitnessTestCommand.ResetTrainerToIdle),
            transition.commands,
        )
    }

    @Test
    fun advisoryRampStopCanProceedToStoppingAndCooldown() {
        val stopTransition = BaselineFitnessTestStateMachine.stopDuringRamp(
            state = BaselineFitnessTestState.RampActive(
                controlMode = BaselineFitnessTestControlMode.ADVISORY,
                startedAt = Instant.parse("2026-03-15T10:00:00Z"),
                rampStartedAt = Instant.parse("2026-03-15T10:05:00Z"),
                startWatts = 100,
            ),
            stopReason = BaselineFitnessTestStopReason.CADENCE_DROP,
            allowCooldown = true,
            stoppedAt = Instant.parse("2026-03-15T10:14:00Z"),
        )
        val stopping = stopTransition.toState as? BaselineFitnessTestState.Stopping
            ?: throw AssertionError("Expected stopping state, got ${stopTransition.toState}")

        val cooldownTransition = BaselineFitnessTestStateMachine.startCooldown(
            state = stopping,
            cooldownStartedAt = Instant.parse("2026-03-15T10:14:05Z"),
        )

        assertEquals(BaselineFitnessTestStateEvent.RAMP_STOP_TRIGGERED, stopTransition.event)
        assertTrue(stopTransition.commands.isEmpty())
        assertTrue(cooldownTransition.toState is BaselineFitnessTestState.Cooldown)
    }
}
