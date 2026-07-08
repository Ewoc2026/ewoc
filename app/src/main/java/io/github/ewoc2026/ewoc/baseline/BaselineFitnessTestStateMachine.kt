package io.github.ewoc2026.ewoc.baseline

import java.time.Instant

/**
 * Pure phase machine for the baseline fitness test lifecycle.
 *
 * The surrounding coordinator is expected to perform side effects from emitted commands. Keeping the
 * state machine free of BLE and Compose concerns makes availability, fallback, and terminal outcome
 * rules unit-testable before UI wiring exists.
 */
internal object BaselineFitnessTestStateMachine {
    fun start(priorFtpWatts: Int?): BaselineFitnessTestTransition {
        val toState = BaselineFitnessTestState.Precheck(
            startWatts = BaselineFitnessTestProtocol.computeStartWatts(priorFtpWatts),
        )
        return BaselineFitnessTestTransition(
            fromState = BaselineFitnessTestState.Idle,
            toState = toState,
            event = BaselineFitnessTestStateEvent.START_REQUESTED,
            commands = emptyList(),
        )
    }

    fun onPowerTelemetryUnavailable(
        state: BaselineFitnessTestState.Precheck,
    ): BaselineFitnessTestTransition {
        return BaselineFitnessTestTransition(
            fromState = state,
            toState = BaselineFitnessTestState.Unavailable(
                reason = BaselineFitnessTestUnavailableReason.POWER_TELEMETRY_UNAVAILABLE,
                startWatts = state.startWatts,
            ),
            event = BaselineFitnessTestStateEvent.POWER_TELEMETRY_UNAVAILABLE,
            commands = emptyList(),
        )
    }

    fun onPrecheckPassed(
        state: BaselineFitnessTestState.Precheck,
        trainerControlAvailable: Boolean,
    ): BaselineFitnessTestTransition {
        if (trainerControlAvailable) {
            return BaselineFitnessTestTransition(
                fromState = state,
                toState = BaselineFitnessTestState.RequestingControl(startWatts = state.startWatts),
                event = BaselineFitnessTestStateEvent.CONTROL_REQUEST_REQUIRED,
                commands = listOf(BaselineFitnessTestCommand.RequestTrainerControl),
            )
        }

        return BaselineFitnessTestTransition(
            fromState = state,
            toState = BaselineFitnessTestState.AdvisoryFallbackPrompt(startWatts = state.startWatts),
            event = BaselineFitnessTestStateEvent.ADVISORY_FALLBACK_REQUIRED,
            commands = listOf(BaselineFitnessTestCommand.ShowAdvisoryFallbackPrompt),
        )
    }

    fun onControlGranted(
        state: BaselineFitnessTestState.RequestingControl,
        startedAt: Instant,
    ): BaselineFitnessTestTransition {
        return BaselineFitnessTestTransition(
            fromState = state,
            toState = BaselineFitnessTestState.Warmup(
                controlMode = BaselineFitnessTestControlMode.ERG,
                startedAt = startedAt,
                startWatts = state.startWatts,
            ),
            event = BaselineFitnessTestStateEvent.CONTROL_GRANTED,
            commands = listOf(
                BaselineFitnessTestCommand.HideAdvisoryFallbackPrompt,
                BaselineFitnessTestCommand.SetErgTarget(state.startWatts),
            ),
        )
    }

    fun onControlGrantFailed(
        state: BaselineFitnessTestState.RequestingControl,
        powerTelemetryStillAvailable: Boolean,
    ): BaselineFitnessTestTransition {
        if (!powerTelemetryStillAvailable) {
            return BaselineFitnessTestTransition(
                fromState = state,
                toState = BaselineFitnessTestState.Unavailable(
                    reason = BaselineFitnessTestUnavailableReason.POWER_TELEMETRY_UNAVAILABLE,
                    startWatts = state.startWatts,
                ),
                event = BaselineFitnessTestStateEvent.POWER_TELEMETRY_UNAVAILABLE,
                commands = emptyList(),
            )
        }

        return BaselineFitnessTestTransition(
            fromState = state,
            toState = BaselineFitnessTestState.AdvisoryFallbackPrompt(startWatts = state.startWatts),
            event = BaselineFitnessTestStateEvent.CONTROL_GRANT_FAILED,
            commands = listOf(BaselineFitnessTestCommand.ShowAdvisoryFallbackPrompt),
        )
    }

    fun acceptAdvisoryFallback(
        state: BaselineFitnessTestState.AdvisoryFallbackPrompt,
        startedAt: Instant,
    ): BaselineFitnessTestTransition {
        return BaselineFitnessTestTransition(
            fromState = state,
            toState = BaselineFitnessTestState.Warmup(
                controlMode = BaselineFitnessTestControlMode.ADVISORY,
                startedAt = startedAt,
                startWatts = state.startWatts,
            ),
            event = BaselineFitnessTestStateEvent.ADVISORY_FALLBACK_ACCEPTED,
            commands = listOf(BaselineFitnessTestCommand.HideAdvisoryFallbackPrompt),
        )
    }

    fun declineAdvisoryFallback(
        state: BaselineFitnessTestState.AdvisoryFallbackPrompt,
    ): BaselineFitnessTestTransition {
        return BaselineFitnessTestTransition(
            fromState = state,
            toState = BaselineFitnessTestState.Cancelled(
                stopReason = BaselineFitnessTestStopReason.CONTROL_GRANT_DECLINED,
                controlMode = null,
                startWatts = state.startWatts,
            ),
            event = BaselineFitnessTestStateEvent.ADVISORY_FALLBACK_DECLINED,
            commands = listOf(BaselineFitnessTestCommand.HideAdvisoryFallbackPrompt),
        )
    }

    fun completeWarmup(
        state: BaselineFitnessTestState.Warmup,
        rampStartedAt: Instant,
    ): BaselineFitnessTestTransition {
        return BaselineFitnessTestTransition(
            fromState = state,
            toState = BaselineFitnessTestState.RampActive(
                controlMode = state.controlMode,
                startedAt = state.startedAt,
                rampStartedAt = rampStartedAt,
                startWatts = state.startWatts,
            ),
            event = BaselineFitnessTestStateEvent.WARMUP_COMPLETED,
            commands = if (state.controlMode == BaselineFitnessTestControlMode.ERG) {
                listOf(BaselineFitnessTestCommand.SetErgTarget(state.startWatts))
            } else {
                emptyList()
            },
        )
    }

    fun cancelDuringWarmup(
        state: BaselineFitnessTestState.Warmup,
    ): BaselineFitnessTestTransition {
        return BaselineFitnessTestTransition(
            fromState = state,
            toState = BaselineFitnessTestState.Cancelled(
                stopReason = BaselineFitnessTestStopReason.USER_CANCEL,
                controlMode = state.controlMode,
                startWatts = state.startWatts,
            ),
            event = BaselineFitnessTestStateEvent.USER_CANCELLED,
            commands = state.terminalCommands(),
        )
    }

    fun stopDuringRamp(
        state: BaselineFitnessTestState.RampActive,
        stopReason: BaselineFitnessTestStopReason,
        allowCooldown: Boolean,
        stoppedAt: Instant,
    ): BaselineFitnessTestTransition {
        val normalizedReason = normalizeStopReason(
            controlMode = state.controlMode,
            stopReason = stopReason,
        )
        if (normalizedReason == BaselineFitnessTestStopReason.CONTROL_LOST_MID_TEST) {
            return BaselineFitnessTestTransition(
                fromState = state,
                toState = BaselineFitnessTestState.Cancelled(
                    stopReason = normalizedReason,
                    controlMode = state.controlMode,
                    startWatts = state.startWatts,
                ),
                event = BaselineFitnessTestStateEvent.CONTROL_LOST_MID_TEST,
                commands = state.terminalCommands(),
            )
        }

        return BaselineFitnessTestTransition(
            fromState = state,
            toState = BaselineFitnessTestState.Stopping(
                controlMode = state.controlMode,
                startedAt = state.startedAt,
                stoppedAt = stoppedAt,
                startWatts = state.startWatts,
                stopReason = normalizedReason,
                cooldownAllowed = allowCooldown,
            ),
            event = BaselineFitnessTestStateEvent.RAMP_STOP_TRIGGERED,
            commands = state.terminalCommands(),
        )
    }

    fun startCooldown(
        state: BaselineFitnessTestState.Stopping,
        cooldownStartedAt: Instant,
    ): BaselineFitnessTestTransition {
        require(state.cooldownAllowed) {
            "Cooldown can only start when the stopping state marked it as allowed."
        }
        return BaselineFitnessTestTransition(
            fromState = state,
            toState = BaselineFitnessTestState.Cooldown(
                controlMode = state.controlMode,
                startedAt = state.startedAt,
                cooldownStartedAt = cooldownStartedAt,
                startWatts = state.startWatts,
                stopReason = state.stopReason,
            ),
            event = BaselineFitnessTestStateEvent.COOLDOWN_STARTED,
            commands = emptyList(),
        )
    }

    fun skipCooldown(
        state: BaselineFitnessTestState.Stopping,
        resultComputeStartedAt: Instant,
    ): BaselineFitnessTestTransition {
        return BaselineFitnessTestTransition(
            fromState = state,
            toState = BaselineFitnessTestState.ResultCompute(
                controlMode = state.controlMode,
                startedAt = state.startedAt,
                resultComputeStartedAt = resultComputeStartedAt,
                stoppedAt = state.stoppedAt,
                startWatts = state.startWatts,
                stopReason = state.stopReason,
            ),
            event = BaselineFitnessTestStateEvent.COOLDOWN_SKIPPED,
            commands = listOf(BaselineFitnessTestCommand.ComputeResult),
        )
    }

    fun completeCooldown(
        state: BaselineFitnessTestState.Cooldown,
        resultComputeStartedAt: Instant,
    ): BaselineFitnessTestTransition {
        return BaselineFitnessTestTransition(
            fromState = state,
            toState = BaselineFitnessTestState.ResultCompute(
                controlMode = state.controlMode,
                startedAt = state.startedAt,
                resultComputeStartedAt = resultComputeStartedAt,
                stoppedAt = state.cooldownStartedAt.plusSeconds(
                    BaselineFitnessTestProtocol.COOLDOWN_DURATION_SEC.toLong(),
                ),
                startWatts = state.startWatts,
                stopReason = state.stopReason,
            ),
            event = BaselineFitnessTestStateEvent.COOLDOWN_COMPLETED,
            commands = listOf(BaselineFitnessTestCommand.ComputeResult),
        )
    }

    fun onResultComputed(
        state: BaselineFitnessTestState.ResultCompute,
        result: BaselineFitnessTestResult,
    ): BaselineFitnessTestTransition {
        return when (result.status) {
            BaselineFitnessTestStatus.COMPLETED -> BaselineFitnessTestTransition(
                fromState = state,
                toState = BaselineFitnessTestState.ResultReady(result = result),
                event = BaselineFitnessTestStateEvent.RESULT_READY,
                commands = emptyList(),
            )

            BaselineFitnessTestStatus.INVALID -> BaselineFitnessTestTransition(
                fromState = state,
                toState = BaselineFitnessTestState.Invalid(result = result),
                event = BaselineFitnessTestStateEvent.RESULT_INVALID,
                commands = emptyList(),
            )

            BaselineFitnessTestStatus.CANCELLED -> BaselineFitnessTestTransition(
                fromState = state,
                toState = BaselineFitnessTestState.Cancelled(
                    stopReason = result.stopReason,
                    controlMode = result.controlMode,
                    startWatts = result.startWatts,
                ),
                event = BaselineFitnessTestStateEvent.RESULT_CANCELLED,
                commands = emptyList(),
            )
        }
    }

    private fun normalizeStopReason(
        controlMode: BaselineFitnessTestControlMode,
        stopReason: BaselineFitnessTestStopReason,
    ): BaselineFitnessTestStopReason {
        return if (
            controlMode == BaselineFitnessTestControlMode.ERG &&
            stopReason == BaselineFitnessTestStopReason.DEVICE_DISCONNECT
        ) {
            BaselineFitnessTestStopReason.CONTROL_LOST_MID_TEST
        } else {
            stopReason
        }
    }

    private fun BaselineFitnessTestActiveState.terminalCommands(): List<BaselineFitnessTestCommand> {
        return if (controlMode == BaselineFitnessTestControlMode.ERG) {
            listOf(BaselineFitnessTestCommand.ResetTrainerToIdle)
        } else {
            emptyList()
        }
    }
}

/**
 * Explicit state vocabulary for the baseline test lifecycle.
 */
internal sealed interface BaselineFitnessTestState {
    object Idle : BaselineFitnessTestState

    data class Precheck(
        val startWatts: Int,
    ) : BaselineFitnessTestState

    data class RequestingControl(
        val startWatts: Int,
    ) : BaselineFitnessTestState

    data class AdvisoryFallbackPrompt(
        val startWatts: Int,
    ) : BaselineFitnessTestState

    data class Warmup(
        override val controlMode: BaselineFitnessTestControlMode,
        override val startedAt: Instant,
        override val startWatts: Int,
    ) : BaselineFitnessTestActiveState

    data class RampActive(
        override val controlMode: BaselineFitnessTestControlMode,
        override val startedAt: Instant,
        val rampStartedAt: Instant,
        override val startWatts: Int,
    ) : BaselineFitnessTestActiveState

    data class Stopping(
        override val controlMode: BaselineFitnessTestControlMode,
        override val startedAt: Instant,
        val stoppedAt: Instant,
        override val startWatts: Int,
        val stopReason: BaselineFitnessTestStopReason,
        val cooldownAllowed: Boolean,
    ) : BaselineFitnessTestActiveState

    data class Cooldown(
        override val controlMode: BaselineFitnessTestControlMode,
        override val startedAt: Instant,
        val cooldownStartedAt: Instant,
        override val startWatts: Int,
        val stopReason: BaselineFitnessTestStopReason,
    ) : BaselineFitnessTestActiveState

    data class ResultCompute(
        val controlMode: BaselineFitnessTestControlMode,
        val startedAt: Instant,
        val resultComputeStartedAt: Instant,
        val stoppedAt: Instant,
        val startWatts: Int,
        val stopReason: BaselineFitnessTestStopReason,
    ) : BaselineFitnessTestState

    data class ResultReady(
        val result: BaselineFitnessTestResult,
    ) : BaselineFitnessTestState

    data class Unavailable(
        val reason: BaselineFitnessTestUnavailableReason,
        val startWatts: Int,
    ) : BaselineFitnessTestState

    data class Invalid(
        val result: BaselineFitnessTestResult,
    ) : BaselineFitnessTestState

    data class Cancelled(
        val stopReason: BaselineFitnessTestStopReason,
        val controlMode: BaselineFitnessTestControlMode?,
        val startWatts: Int,
    ) : BaselineFitnessTestState
}

/**
 * Shared subset for states that own an active attempt and therefore may emit trainer commands.
 */
internal sealed interface BaselineFitnessTestActiveState : BaselineFitnessTestState {
    val controlMode: BaselineFitnessTestControlMode
    val startedAt: Instant
    val startWatts: Int
}

/**
 * Reducer event label used by tests and later diagnostics.
 */
internal enum class BaselineFitnessTestStateEvent {
    START_REQUESTED,
    POWER_TELEMETRY_UNAVAILABLE,
    CONTROL_REQUEST_REQUIRED,
    CONTROL_GRANTED,
    CONTROL_GRANT_FAILED,
    ADVISORY_FALLBACK_REQUIRED,
    ADVISORY_FALLBACK_ACCEPTED,
    ADVISORY_FALLBACK_DECLINED,
    USER_CANCELLED,
    WARMUP_COMPLETED,
    RAMP_STOP_TRIGGERED,
    CONTROL_LOST_MID_TEST,
    COOLDOWN_STARTED,
    COOLDOWN_SKIPPED,
    COOLDOWN_COMPLETED,
    RESULT_READY,
    RESULT_INVALID,
    RESULT_CANCELLED,
}

/**
 * Coordinator-owned side effects emitted from pure state transitions.
 */
internal sealed interface BaselineFitnessTestCommand {
    object RequestTrainerControl : BaselineFitnessTestCommand
    object ShowAdvisoryFallbackPrompt : BaselineFitnessTestCommand
    object HideAdvisoryFallbackPrompt : BaselineFitnessTestCommand
    object ResetTrainerToIdle : BaselineFitnessTestCommand
    object ComputeResult : BaselineFitnessTestCommand

    data class SetErgTarget(
        val watts: Int,
    ) : BaselineFitnessTestCommand
}

/**
 * Reducer output used to keep state changes and emitted commands coupled in tests.
 */
internal data class BaselineFitnessTestTransition(
    val fromState: BaselineFitnessTestState,
    val toState: BaselineFitnessTestState,
    val event: BaselineFitnessTestStateEvent,
    val commands: List<BaselineFitnessTestCommand>,
)
