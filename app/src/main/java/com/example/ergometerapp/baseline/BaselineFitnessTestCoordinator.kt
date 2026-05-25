package com.example.ergometerapp.baseline

import com.example.ergometerapp.logging.AppLog
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Side-effect bridge used by [BaselineFitnessTestCoordinator] for FTMS control actions.
 *
 * The coordinator owns protocol sequencing, while the runtime port remains a thin adapter to the
 * existing FTMS stack so tests can lock the protocol without BLE or Compose dependencies.
 */
internal interface BaselineFitnessTestRuntimePort {
    fun prepareTrainer(): Boolean
    fun releaseTrainer()
    fun trainerPreparationState(): BaselineFitnessTestTrainerPreparationState
    fun requestTrainerControl(): Boolean
    fun setErgTarget(watts: Int)
    fun resetTrainerToIdle()
    fun consumeTrainerControlRequestOutcome(): BaselineFitnessTestTrainerControlRequestOutcome?
}

/**
 * Latest trainer-preparation state exposed by the runtime adapter.
 *
 * The coordinator uses this to distinguish a trainer that is still connecting from one that
 * is truly unavailable, so baseline start can wait in PRECHECK instead of failing immediately.
 */
internal enum class BaselineFitnessTestTrainerPreparationState {
    IDLE,
    PENDING,
    READY,
    FAILED,
}

/**
 * Latest outcome from the runtime FTMS control-request adapter.
 */
internal enum class BaselineFitnessTestTrainerControlRequestOutcome {
    GRANTED,
    FAILED,
}

/**
 * Latest live telemetry snapshot consumed by the baseline runtime.
 *
 * Sample timestamps are carried separately from the values so the coordinator can distinguish a
 * repeated reading from a stale transport and keep power-gap handling deterministic.
 */
internal data class BaselineFitnessTestLiveMetricsSnapshot(
    val powerWatts: Int? = null,
    val powerRecordedAt: Instant? = null,
    val cadenceRpm: Double? = null,
    val heartRateBpm: Int? = null,
    val heartRateRecordedAt: Instant? = null,
    val trainerReady: Boolean = false,
    val controlGranted: Boolean = false,
)

/**
 * UI-facing phase summary for the baseline runtime.
 *
 * This keeps Compose/UI consumers decoupled from the richer reducer state objects that the
 * coordinator uses internally for protocol transitions.
 */
internal enum class BaselineFitnessTestUiPhase {
    IDLE,
    PRECHECK,
    REQUESTING_CONTROL,
    ADVISORY_FALLBACK_PROMPT,
    WARMUP,
    RAMP_ACTIVE,
    COOLDOWN,
    RESULT_READY,
    INVALID,
    CANCELLED,
    UNAVAILABLE,
}

/**
 * Latest availability snapshot exposed to UI code before and during an attempt.
 */
internal data class BaselineFitnessTestAvailability(
    val powerTelemetryAvailable: Boolean = false,
    val trainerControlAvailable: Boolean = false,
    val unavailableReason: BaselineFitnessTestUnavailableReason? = null,
)

/**
 * Immutable runtime snapshot exposed from the coordinator to ViewModel/UI code.
 */
internal data class BaselineFitnessTestRuntimeSnapshot(
    val phase: BaselineFitnessTestUiPhase = BaselineFitnessTestUiPhase.IDLE,
    val availability: BaselineFitnessTestAvailability = BaselineFitnessTestAvailability(),
    val controlMode: BaselineFitnessTestControlMode? = null,
    val startWatts: Int? = null,
    val targetWatts: Int? = null,
    val measuredPowerWatts: Int? = null,
    val measuredCadenceRpm: Int? = null,
    val measuredHeartRateBpm: Int? = null,
    val warmupRemainingSeconds: Int? = null,
    val rampElapsedSeconds: Int? = null,
    val cooldownRemainingSeconds: Int? = null,
    val currentRampMinuteNumber: Int? = null,
    val validRampMinutes: Int = 0,
    val awaitingWarmupStartSignal: Boolean = false,
    val advisoryFallbackPromptVisible: Boolean = false,
    val result: BaselineFitnessTestResult? = null,
    val sensorProfile: BaselineFitnessTestSensorProfile = emptySensorProfile(),
    val lastTransitionEvent: BaselineFitnessTestStateEvent? = null,
)

/**
 * Drives the live baseline-test runtime on top of the pure state machine and result calculator.
 *
 * The coordinator advances warm-up/ramp/cooldown time in one-second protocol slices so minute
 * boundaries stay exact even when UI/render timing jitters. Telemetry freshness is evaluated from
 * explicit sample timestamps rather than "last non-null value wins" heuristics.
 */
internal class BaselineFitnessTestCoordinator(
    private val runtimePort: BaselineFitnessTestRuntimePort,
    private val onResultRecorded: (BaselineFitnessTestResult) -> Unit = {},
    private val onSnapshotChanged: (BaselineFitnessTestRuntimeSnapshot) -> Unit = {},
) {
    private var state: BaselineFitnessTestState = BaselineFitnessTestState.Idle
    private var lastTransitionEvent: BaselineFitnessTestStateEvent? = null
    private var availability = BaselineFitnessTestAvailability()
    private var liveMetrics = BaselineFitnessTestLiveMetricsSnapshot()
    private var sensorProfile = emptySensorProfile()
    private var requestStartedAt: Instant? = null
    private var attemptStartedAt: Instant? = null
    private var warmupCountdownStartedAt: Instant? = null
    private var warmupProcessedSeconds = 0
    private var rampProcessedSeconds = 0
    private var cooldownProcessedSeconds = 0
    private var activeTestObservedSeconds = 0
    private var hrObservedSeconds = 0
    private var cadenceBelowThresholdSince: Instant? = null
    private var hasObservedErgControlOwnership = false
    private val finalizedRampSteps = mutableListOf<RampStepAccumulator>()
    private var activeRampStep: RampStepAccumulator? = null
    private var terminalResult: BaselineFitnessTestResult? = null
    private var latestSnapshot = BaselineFitnessTestRuntimeSnapshot()

    fun snapshot(): BaselineFitnessTestRuntimeSnapshot = latestSnapshot

    fun refreshLiveMetrics(
        now: Instant,
        liveMetrics: BaselineFitnessTestLiveMetricsSnapshot,
    ) {
        this.liveMetrics = liveMetrics
        availability = computeAvailability(now, liveMetrics)
        sensorProfile = sensorProfile.merge(liveMetrics)
        publishSnapshot()
    }

    fun resetToIdle() {
        resetRuntime()
    }

    fun start(
        priorFtpWatts: Int?,
        now: Instant,
        liveMetrics: BaselineFitnessTestLiveMetricsSnapshot,
    ) {
        resetRuntime()
        requestStartedAt = now
        this.liveMetrics = liveMetrics
        availability = computeAvailability(now, liveMetrics)
        sensorProfile = sensorProfile.merge(liveMetrics)
        AppLog.testMarker(
            event = "baseline_start_pressed",
            context = mapOf(
                "priorFtpWatts" to (priorFtpWatts?.toString() ?: "none"),
                "startWatts" to BaselineFitnessTestProtocol.computeStartWatts(priorFtpWatts).toString(),
                "trainerReady" to availability.powerTelemetryAvailable.toString(),
                "controlAvailable" to availability.trainerControlAvailable.toString(),
            ),
        )

        applyTransition(
            transition = BaselineFitnessTestStateMachine.start(priorFtpWatts = priorFtpWatts),
            now = now,
        )

        val precheck = state as? BaselineFitnessTestState.Precheck ?: run {
            publishSnapshot()
            return
        }
        if (!availability.powerTelemetryAvailable) {
            if (!runtimePort.prepareTrainer()) {
                applyTransition(
                    transition = BaselineFitnessTestStateMachine.onPowerTelemetryUnavailable(precheck),
                    now = now,
                )
                return
            }
            publishSnapshot()
            return
        }

        applyTransition(
            transition = BaselineFitnessTestStateMachine.onPrecheckPassed(
                state = precheck,
                trainerControlAvailable = availability.trainerControlAvailable,
            ),
            now = now,
        )
    }

    fun acceptAdvisoryFallback(now: Instant) {
        val promptState = state as? BaselineFitnessTestState.AdvisoryFallbackPrompt ?: return
        applyTransition(
            transition = BaselineFitnessTestStateMachine.acceptAdvisoryFallback(
                state = promptState,
                startedAt = now,
            ),
            now = now,
        )
    }

    fun declineAdvisoryFallback(now: Instant) {
        val promptState = state as? BaselineFitnessTestState.AdvisoryFallbackPrompt ?: return
        applyTransition(
            transition = BaselineFitnessTestStateMachine.declineAdvisoryFallback(promptState),
            now = now,
        )
        finalizeDirectCancellation(
            stopReason = BaselineFitnessTestStopReason.CONTROL_GRANT_DECLINED,
            controlMode = null,
            startWatts = promptState.startWatts,
            warmupCompleted = false,
            completedAt = now,
        )
    }

    fun stopRamp(now: Instant) {
        val rampState = state as? BaselineFitnessTestState.RampActive ?: return
        stopRampInternal(
            rampState = rampState,
            stopReason = BaselineFitnessTestStopReason.MANUAL_STOP,
            completedAt = now,
        )
    }

    fun cancelAttempt(now: Instant) {
        when (val currentState = state) {
            is BaselineFitnessTestState.Precheck -> {
                runtimePort.releaseTrainer()
                state = BaselineFitnessTestState.Cancelled(
                    stopReason = BaselineFitnessTestStopReason.USER_CANCEL,
                    controlMode = null,
                    startWatts = currentState.startWatts,
                )
                lastTransitionEvent = BaselineFitnessTestStateEvent.USER_CANCELLED
                finalizeDirectCancellation(
                    stopReason = BaselineFitnessTestStopReason.USER_CANCEL,
                    controlMode = null,
                    startWatts = currentState.startWatts,
                    warmupCompleted = false,
                    completedAt = now,
                )
            }

            is BaselineFitnessTestState.AdvisoryFallbackPrompt -> {
                declineAdvisoryFallback(now)
            }

            is BaselineFitnessTestState.RequestingControl -> {
                state = BaselineFitnessTestState.Cancelled(
                    stopReason = BaselineFitnessTestStopReason.USER_CANCEL,
                    controlMode = null,
                    startWatts = currentState.startWatts,
                )
                lastTransitionEvent = BaselineFitnessTestStateEvent.USER_CANCELLED
                finalizeDirectCancellation(
                    stopReason = BaselineFitnessTestStopReason.USER_CANCEL,
                    controlMode = null,
                    startWatts = currentState.startWatts,
                    warmupCompleted = false,
                    completedAt = now,
                )
            }

            is BaselineFitnessTestState.Warmup -> {
                applyTransition(
                    transition = BaselineFitnessTestStateMachine.cancelDuringWarmup(currentState),
                    now = now,
                )
                finalizeDirectCancellation(
                    stopReason = BaselineFitnessTestStopReason.USER_CANCEL,
                    controlMode = currentState.controlMode,
                    startWatts = currentState.startWatts,
                    warmupCompleted = false,
                    completedAt = now,
                )
            }

            is BaselineFitnessTestState.RampActive -> {
                applyTransition(
                    transition = BaselineFitnessTestStateMachine.stopDuringRamp(
                        state = currentState,
                        stopReason = BaselineFitnessTestStopReason.USER_CANCEL,
                        allowCooldown = false,
                        stoppedAt = now,
                    ),
                    now = now,
                )
                finalizeDirectCancellation(
                    stopReason = BaselineFitnessTestStopReason.USER_CANCEL,
                    controlMode = currentState.controlMode,
                    startWatts = currentState.startWatts,
                    warmupCompleted = true,
                    completedAt = now,
                )
            }

            is BaselineFitnessTestState.Cooldown -> {
                if (currentState.controlMode == BaselineFitnessTestControlMode.ERG) {
                    runtimePort.resetTrainerToIdle()
                }
                state = BaselineFitnessTestState.Cancelled(
                    stopReason = BaselineFitnessTestStopReason.USER_CANCEL,
                    controlMode = currentState.controlMode,
                    startWatts = currentState.startWatts,
                )
                lastTransitionEvent = BaselineFitnessTestStateEvent.USER_CANCELLED
                finalizeDirectCancellation(
                    stopReason = BaselineFitnessTestStopReason.USER_CANCEL,
                    controlMode = currentState.controlMode,
                    startWatts = currentState.startWatts,
                    warmupCompleted = true,
                    completedAt = now,
                )
            }

            else -> Unit
        }
    }

    fun skipCooldown(now: Instant) {
        when (val currentState = state) {
            is BaselineFitnessTestState.Stopping -> {
                applyTransition(
                    transition = BaselineFitnessTestStateMachine.skipCooldown(
                        state = currentState,
                        resultComputeStartedAt = now,
                    ),
                    now = now,
                )
                computeResult(now)
            }

            is BaselineFitnessTestState.Cooldown -> {
                applyTransition(
                    transition = BaselineFitnessTestStateMachine.completeCooldown(
                        state = currentState.copy(
                            cooldownStartedAt = now.minusSeconds(
                                BaselineFitnessTestProtocol.COOLDOWN_DURATION_SEC.toLong(),
                            ),
                        ),
                        resultComputeStartedAt = now,
                    ),
                    now = now,
                )
                computeResult(now)
            }

            else -> Unit
        }
    }

    fun tick(
        now: Instant,
        liveMetrics: BaselineFitnessTestLiveMetricsSnapshot,
    ) {
        this.liveMetrics = liveMetrics
        availability = computeAvailability(now, liveMetrics)
        sensorProfile = sensorProfile.merge(liveMetrics)

        when (val outcome = runtimePort.consumeTrainerControlRequestOutcome()) {
            BaselineFitnessTestTrainerControlRequestOutcome.GRANTED -> {
                val requestingControl = state as? BaselineFitnessTestState.RequestingControl
                if (requestingControl != null) {
                    applyTransition(
                        transition = BaselineFitnessTestStateMachine.onControlGranted(
                            state = requestingControl,
                            startedAt = now,
                        ),
                        now = now,
                    )
                }
            }

            BaselineFitnessTestTrainerControlRequestOutcome.FAILED -> {
                val requestingControl = state as? BaselineFitnessTestState.RequestingControl
                if (requestingControl != null) {
                    applyTransition(
                        transition = BaselineFitnessTestStateMachine.onControlGrantFailed(
                            state = requestingControl,
                            powerTelemetryStillAvailable = availability.powerTelemetryAvailable,
                        ),
                        now = now,
                    )
                }
            }

            null -> Unit
        }

        when (val currentState = state) {
            is BaselineFitnessTestState.Precheck -> processPrecheckTick(
                state = currentState,
                now = now,
            )

            is BaselineFitnessTestState.Warmup -> processWarmupTicks(
                state = currentState,
                now = now,
            )

            is BaselineFitnessTestState.RampActive -> processRampTicks(
                state = currentState,
                now = now,
            )

            is BaselineFitnessTestState.Cooldown -> processCooldownTicks(
                state = currentState,
                now = now,
            )

            else -> publishSnapshot()
        }
    }

    private fun processPrecheckTick(
        state: BaselineFitnessTestState.Precheck,
        now: Instant,
    ) {
        val trainerPreparationState = runtimePort.trainerPreparationState()
        if (!availability.powerTelemetryAvailable) {
            if (trainerPreparationState == BaselineFitnessTestTrainerPreparationState.FAILED) {
                applyTransition(
                    transition = BaselineFitnessTestStateMachine.onPowerTelemetryUnavailable(state),
                    now = now,
                )
                return
            }
            if (
                trainerPreparationState == BaselineFitnessTestTrainerPreparationState.READY &&
                availability.trainerControlAvailable
            ) {
                applyTransition(
                    transition = BaselineFitnessTestStateMachine.onPrecheckPassed(
                        state = state,
                        trainerControlAvailable = true,
                    ),
                    now = now,
                )
                return
            }
            publishSnapshot()
            return
        }

        if (
            !availability.trainerControlAvailable &&
            trainerPreparationState == BaselineFitnessTestTrainerPreparationState.PENDING
        ) {
            publishSnapshot()
            return
        }

        applyTransition(
            transition = BaselineFitnessTestStateMachine.onPrecheckPassed(
                state = state,
                trainerControlAvailable = availability.trainerControlAvailable,
            ),
            now = now,
        )
    }

    private fun processWarmupTicks(
        state: BaselineFitnessTestState.Warmup,
        now: Instant,
    ) {
        if (shouldCancelErgAttempt(controlMode = state.controlMode, liveMetrics = liveMetrics)) {
            cancelForControlLoss(
                startWatts = state.startWatts,
                completedAt = now,
            )
            return
        }
        if (state.controlMode == BaselineFitnessTestControlMode.ADVISORY && !liveMetrics.trainerReady) {
            cancelForWarmupDisconnect(
                controlMode = state.controlMode,
                startWatts = state.startWatts,
                completedAt = now,
            )
            return
        }

        val countdownStartedAt = warmupCountdownStartedAt ?: if (hasWarmupStartSignal(now, liveMetrics)) {
            now.also { startedAt ->
                warmupCountdownStartedAt = startedAt
                attemptStartedAt = startedAt
            }
        } else {
            publishSnapshot()
            return
        }

        while (warmupProcessedSeconds < elapsedSecondsSince(countdownStartedAt, now)) {
            warmupProcessedSeconds += 1
            recordActiveTestSecond()
            val sampleTime = countdownStartedAt.plusSeconds(warmupProcessedSeconds.toLong())
            recordHeartRateIfAvailable(sampleTime)
        }

        if (warmupProcessedSeconds < BaselineFitnessTestProtocol.WARMUP_DURATION_SEC) {
            publishSnapshot()
            return
        }

        applyTransition(
            transition = BaselineFitnessTestStateMachine.completeWarmup(
                state = state,
                rampStartedAt = countdownStartedAt.plusSeconds(
                    BaselineFitnessTestProtocol.WARMUP_DURATION_SEC.toLong(),
                ),
            ),
            now = now,
        )
    }

    private fun processRampTicks(
        state: BaselineFitnessTestState.RampActive,
        now: Instant,
    ) {
        if (shouldCancelErgAttempt(controlMode = state.controlMode, liveMetrics = liveMetrics)) {
            cancelForControlLoss(
                startWatts = state.startWatts,
                completedAt = now,
            )
            return
        }
        if (state.controlMode == BaselineFitnessTestControlMode.ADVISORY && !liveMetrics.trainerReady) {
            stopRampInternal(
                rampState = state,
                stopReason = BaselineFitnessTestStopReason.DEVICE_DISCONNECT,
                completedAt = now,
            )
            return
        }

        while (rampProcessedSeconds < elapsedSecondsSince(state.rampStartedAt, now)) {
            rampProcessedSeconds += 1
            val sampleTime = state.rampStartedAt.plusSeconds(rampProcessedSeconds.toLong())
            val stepIndex = (rampProcessedSeconds - 1) / BaselineFitnessTestProtocol.RAMP_STEP_DURATION_SEC
            val targetWatts = BaselineFitnessTestProtocol.targetWattsForRampMinute(
                startWatts = state.startWatts,
                rampMinuteIndex = stepIndex,
            )
            ensureActiveRampStep(
                targetWatts = targetWatts,
                controlMode = state.controlMode,
            )
            recordActiveTestSecond()
            recordHeartRateIfAvailable(sampleTime)
            val powerGapSec = powerGapSecondsAtSampleTime(sampleTime)
            activeRampStep = activeRampStep?.apply {
                completedSeconds += 1
                maxPowerGapSec = max(maxPowerGapSec, powerGapSec)
            }
            updateCadenceAutoStop(sampleTime)

            if (powerGapSec > BaselineFitnessTestProtocol.POWER_SIGNAL_LOSS_THRESHOLD_SEC.toDouble()) {
                stopRampInternal(
                    rampState = state,
                    stopReason = BaselineFitnessTestStopReason.POWER_SIGNAL_LOST,
                    completedAt = sampleTime,
                )
                return
            }

            if (shouldStopForCadenceDrop(sampleTime)) {
                stopRampInternal(
                    rampState = state,
                    stopReason = BaselineFitnessTestStopReason.CADENCE_DROP,
                    completedAt = sampleTime,
                )
                return
            }
        }

        publishSnapshot()
    }

    private fun processCooldownTicks(
        state: BaselineFitnessTestState.Cooldown,
        now: Instant,
    ) {
        if (shouldCancelErgAttempt(controlMode = state.controlMode, liveMetrics = liveMetrics)) {
            cancelForControlLoss(
                startWatts = state.startWatts,
                completedAt = now,
            )
            return
        }
        if (state.controlMode == BaselineFitnessTestControlMode.ADVISORY && !liveMetrics.trainerReady) {
            applyTransition(
                transition = BaselineFitnessTestStateMachine.completeCooldown(
                    state = state.copy(
                        cooldownStartedAt = now.minusSeconds(
                            BaselineFitnessTestProtocol.COOLDOWN_DURATION_SEC.toLong(),
                        ),
                    ),
                    resultComputeStartedAt = now,
                ),
                now = now,
            )
            computeResult(now)
            return
        }

        while (cooldownProcessedSeconds < elapsedSecondsSince(state.cooldownStartedAt, now)) {
            cooldownProcessedSeconds += 1
        }

        if (cooldownProcessedSeconds < BaselineFitnessTestProtocol.COOLDOWN_DURATION_SEC) {
            publishSnapshot()
            return
        }

        applyTransition(
            transition = BaselineFitnessTestStateMachine.completeCooldown(
                state = state,
                resultComputeStartedAt = now,
            ),
            now = now,
        )
        computeResult(now)
    }

    private fun stopRampInternal(
        rampState: BaselineFitnessTestState.RampActive,
        stopReason: BaselineFitnessTestStopReason,
        completedAt: Instant,
    ) {
        applyTransition(
            transition = BaselineFitnessTestStateMachine.stopDuringRamp(
                state = rampState,
                stopReason = stopReason,
                allowCooldown = true,
                stoppedAt = completedAt,
            ),
            now = completedAt,
        )

        val cancelled = state as? BaselineFitnessTestState.Cancelled
        if (cancelled != null) {
            finalizeDirectCancellation(
                stopReason = cancelled.stopReason,
                controlMode = cancelled.controlMode,
                startWatts = cancelled.startWatts,
                warmupCompleted = true,
                completedAt = completedAt,
            )
            return
        }

        val stopping = state as? BaselineFitnessTestState.Stopping ?: run {
            publishSnapshot()
            return
        }
        applyTransition(
            transition = BaselineFitnessTestStateMachine.startCooldown(
                state = stopping,
                cooldownStartedAt = completedAt,
            ),
            now = completedAt,
        )
        if (stopping.controlMode == BaselineFitnessTestControlMode.ERG) {
            runtimePort.setErgTarget(stopping.startWatts)
        }
    }

    private fun computeResult(completedAt: Instant) {
        val resultComputeState = state as? BaselineFitnessTestState.ResultCompute ?: run {
            publishSnapshot()
            return
        }
        if (resultComputeState.controlMode == BaselineFitnessTestControlMode.ERG) {
            runtimePort.resetTrainerToIdle()
        }
        finalizeActiveRampStep()
        val result = BaselineFitnessTestResultCalculator.calculate(
            input = BaselineFitnessTestComputationInput(
                controlMode = resultComputeState.controlMode,
                stopReason = resultComputeState.stopReason,
                startedAt = attemptStartedAt ?: requestStartedAt ?: completedAt,
                completedAt = completedAt,
                startWatts = resultComputeState.startWatts,
                warmupCompleted = warmupProcessedSeconds >= BaselineFitnessTestProtocol.WARMUP_DURATION_SEC,
                rampSteps = finalizedRampSteps.map { it.toResult() },
                hrCoverageRatio = hrCoverageRatio(),
                sensorProfile = sensorProfile,
            ),
        )
        terminalResult = result
        onResultRecorded(result)
        applyTransition(
            transition = BaselineFitnessTestStateMachine.onResultComputed(
                state = resultComputeState,
                result = result,
            ),
            now = completedAt,
        )
    }

    private fun finalizeDirectCancellation(
        stopReason: BaselineFitnessTestStopReason,
        controlMode: BaselineFitnessTestControlMode?,
        startWatts: Int,
        warmupCompleted: Boolean,
        completedAt: Instant,
    ) {
        finalizeActiveRampStep()
        val result = BaselineFitnessTestResultCalculator.calculate(
            input = BaselineFitnessTestComputationInput(
                controlMode = controlMode ?: BaselineFitnessTestControlMode.ADVISORY,
                stopReason = stopReason,
                startedAt = attemptStartedAt ?: requestStartedAt ?: completedAt,
                completedAt = completedAt,
                startWatts = startWatts,
                warmupCompleted = warmupCompleted,
                rampSteps = finalizedRampSteps.map { it.toResult() },
                hrCoverageRatio = hrCoverageRatio(),
                sensorProfile = sensorProfile,
            ),
        )
        terminalResult = result
        onResultRecorded(result)
        publishSnapshot()
    }

    private fun cancelForControlLoss(
        startWatts: Int,
        completedAt: Instant,
    ) {
        state = BaselineFitnessTestState.Cancelled(
            stopReason = BaselineFitnessTestStopReason.CONTROL_LOST_MID_TEST,
            controlMode = BaselineFitnessTestControlMode.ERG,
            startWatts = startWatts,
        )
        lastTransitionEvent = BaselineFitnessTestStateEvent.CONTROL_LOST_MID_TEST
        runtimePort.resetTrainerToIdle()
        finalizeDirectCancellation(
            stopReason = BaselineFitnessTestStopReason.CONTROL_LOST_MID_TEST,
            controlMode = BaselineFitnessTestControlMode.ERG,
            startWatts = startWatts,
            warmupCompleted = warmupProcessedSeconds >= BaselineFitnessTestProtocol.WARMUP_DURATION_SEC,
            completedAt = completedAt,
        )
    }

    private fun cancelForWarmupDisconnect(
        controlMode: BaselineFitnessTestControlMode,
        startWatts: Int,
        completedAt: Instant,
    ) {
        state = BaselineFitnessTestState.Cancelled(
            stopReason = BaselineFitnessTestStopReason.DEVICE_DISCONNECT,
            controlMode = controlMode,
            startWatts = startWatts,
        )
        lastTransitionEvent = BaselineFitnessTestStateEvent.RESULT_CANCELLED
        if (controlMode == BaselineFitnessTestControlMode.ERG) {
            runtimePort.resetTrainerToIdle()
        }
        finalizeDirectCancellation(
            stopReason = BaselineFitnessTestStopReason.DEVICE_DISCONNECT,
            controlMode = controlMode,
            startWatts = startWatts,
            warmupCompleted = false,
            completedAt = completedAt,
        )
    }

    private fun applyTransition(
        transition: BaselineFitnessTestTransition,
        now: Instant,
    ) {
        state = transition.toState
        lastTransitionEvent = transition.event
        transition.commands.forEach { command ->
            when (command) {
                BaselineFitnessTestCommand.RequestTrainerControl -> {
                    val requestingControl = state as? BaselineFitnessTestState.RequestingControl
                    AppLog.testMarker(
                        event = "baseline_control_requested",
                        context = mapOf(
                            "startWatts" to (requestingControl?.startWatts?.toString() ?: "unknown"),
                            "trainerReady" to liveMetrics.trainerReady.toString(),
                            "controlGranted" to liveMetrics.controlGranted.toString(),
                        ),
                    )
                    val requestStarted = runtimePort.requestTrainerControl()
                    if (!requestStarted) {
                        if (requestingControl != null) {
                            applyTransition(
                                transition = BaselineFitnessTestStateMachine.onControlGrantFailed(
                                    state = requestingControl,
                                    powerTelemetryStillAvailable = availability.powerTelemetryAvailable,
                                ),
                                now = now,
                            )
                            return
                        }
                    }
                }

                BaselineFitnessTestCommand.ShowAdvisoryFallbackPrompt -> Unit
                BaselineFitnessTestCommand.HideAdvisoryFallbackPrompt -> Unit
                BaselineFitnessTestCommand.ComputeResult -> Unit
                BaselineFitnessTestCommand.ResetTrainerToIdle -> runtimePort.resetTrainerToIdle()
                is BaselineFitnessTestCommand.SetErgTarget -> runtimePort.setErgTarget(command.watts)
            }
        }
        when (state) {
            is BaselineFitnessTestState.Warmup -> {
                if (warmupCountdownStartedAt == null && hasWarmupStartSignal(now, liveMetrics)) {
                    warmupCountdownStartedAt = now
                    attemptStartedAt = now
                }
            }

            else -> {
                warmupCountdownStartedAt = null
            }
        }
        if (liveMetrics.controlGranted) {
            hasObservedErgControlOwnership = true
        }
        publishSnapshot()
    }

    private fun computeAvailability(
        now: Instant,
        liveMetrics: BaselineFitnessTestLiveMetricsSnapshot,
    ): BaselineFitnessTestAvailability {
        val powerAvailable = isPowerTelemetryAvailable(now, liveMetrics)
        return BaselineFitnessTestAvailability(
            powerTelemetryAvailable = powerAvailable,
            trainerControlAvailable = liveMetrics.trainerReady,
            unavailableReason = if (powerAvailable) null else {
                BaselineFitnessTestUnavailableReason.POWER_TELEMETRY_UNAVAILABLE
            },
        )
    }

    private fun ensureActiveRampStep(
        targetWatts: Int,
        controlMode: BaselineFitnessTestControlMode,
    ) {
        val existingStep = activeRampStep
        if (existingStep?.targetWatts == targetWatts) return
        finalizeActiveRampStep()
        activeRampStep = RampStepAccumulator(targetWatts = targetWatts)
        cadenceBelowThresholdSince = null
        if (controlMode == BaselineFitnessTestControlMode.ERG) {
            runtimePort.setErgTarget(targetWatts)
        }
    }

    private fun finalizeActiveRampStep() {
        val step = activeRampStep ?: return
        finalizedRampSteps += step
        activeRampStep = null
    }

    private fun recordActiveTestSecond() {
        activeTestObservedSeconds += 1
    }

    private fun recordHeartRateIfAvailable(sampleTime: Instant) {
        val heartRate = liveHeartRate(sampleTime) ?: return
        hrObservedSeconds += 1
        activeRampStep = activeRampStep?.apply { recordHeartRate(heartRate) }
    }

    private fun liveHeartRate(sampleTime: Instant): Int? {
        val value = liveMetrics.heartRateBpm ?: return null
        val recordedAt = liveMetrics.heartRateRecordedAt ?: return null
        if (recordedAt.isAfter(sampleTime)) return null
        return value
    }

    private fun powerGapSecondsAtSampleTime(sampleTime: Instant): Double {
        val recordedAt = liveMetrics.powerRecordedAt ?: return Double.POSITIVE_INFINITY
        val gapMillis = max(0L, Duration.between(recordedAt, sampleTime).toMillis())
        return gapMillis / 1000.0
    }

    private fun updateCadenceAutoStop(sampleTime: Instant) {
        if (!sensorProfile.cadence) {
            cadenceBelowThresholdSince = null
            return
        }
        val cadenceRpm = liveMetrics.cadenceRpm
        if (cadenceRpm == null || cadenceRpm >= BaselineFitnessTestProtocol.CADENCE_AUTO_STOP_THRESHOLD_RPM) {
            cadenceBelowThresholdSince = null
            return
        }
        if (cadenceBelowThresholdSince == null) {
            cadenceBelowThresholdSince = sampleTime
        }
    }

    private fun shouldStopForCadenceDrop(sampleTime: Instant): Boolean {
        val belowSince = cadenceBelowThresholdSince ?: return false
        val heldSeconds = Duration.between(belowSince, sampleTime).seconds + 1
        return heldSeconds >= BaselineFitnessTestProtocol.CADENCE_AUTO_STOP_HOLD_SEC
    }

    private fun shouldCancelErgAttempt(
        controlMode: BaselineFitnessTestControlMode,
        liveMetrics: BaselineFitnessTestLiveMetricsSnapshot,
    ): Boolean {
        if (controlMode != BaselineFitnessTestControlMode.ERG) return false
        if (!liveMetrics.trainerReady) return true
        if (liveMetrics.controlGranted) {
            hasObservedErgControlOwnership = true
            return false
        }
        return hasObservedErgControlOwnership
    }

    private fun hrCoverageRatio(): Double {
        if (activeTestObservedSeconds <= 0) return 0.0
        return hrObservedSeconds.toDouble() / activeTestObservedSeconds.toDouble()
    }

    private fun hasWarmupStartSignal(
        now: Instant,
        liveMetrics: BaselineFitnessTestLiveMetricsSnapshot,
    ): Boolean {
        val cadenceRpm = liveMetrics.cadenceRpm
        if (cadenceRpm != null && cadenceRpm > 0.0) return true
        val powerWatts = liveMetrics.powerWatts ?: return false
        return powerWatts > 0 && isPowerTelemetryAvailable(now, liveMetrics)
    }

    private fun isPowerTelemetryAvailable(
        now: Instant,
        liveMetrics: BaselineFitnessTestLiveMetricsSnapshot,
    ): Boolean {
        val powerWatts = liveMetrics.powerWatts ?: return false
        if (powerWatts < 0) return false
        val recordedAt = liveMetrics.powerRecordedAt ?: return false
        return powerGapSeconds(now) <= BaselineFitnessTestProtocol.POWER_SIGNAL_LOSS_THRESHOLD_SEC.toDouble()
    }

    private fun powerGapSeconds(now: Instant): Double {
        val recordedAt = liveMetrics.powerRecordedAt ?: return Double.POSITIVE_INFINITY
        val gapMillis = max(0L, Duration.between(recordedAt, now).toMillis())
        return gapMillis / 1000.0
    }

    private fun elapsedSecondsSince(
        start: Instant,
        now: Instant,
    ): Int {
        return Duration.between(start, now).seconds.toInt().coerceAtLeast(0)
    }

    private fun publishSnapshot() {
        latestSnapshot = BaselineFitnessTestRuntimeSnapshot(
            phase = state.toUiPhase(),
            availability = availability,
            controlMode = when (val currentState = state) {
                is BaselineFitnessTestState.Warmup -> currentState.controlMode
                is BaselineFitnessTestState.RampActive -> currentState.controlMode
                is BaselineFitnessTestState.Stopping -> currentState.controlMode
                is BaselineFitnessTestState.Cooldown -> currentState.controlMode
                is BaselineFitnessTestState.ResultCompute -> currentState.controlMode
                is BaselineFitnessTestState.ResultReady -> currentState.result.controlMode
                is BaselineFitnessTestState.Invalid -> currentState.result.controlMode
                is BaselineFitnessTestState.Cancelled -> terminalResult?.controlMode ?: currentState.controlMode
                else -> terminalResult?.controlMode
            },
            startWatts = when (val currentState = state) {
                is BaselineFitnessTestState.Precheck -> currentState.startWatts
                is BaselineFitnessTestState.RequestingControl -> currentState.startWatts
                is BaselineFitnessTestState.AdvisoryFallbackPrompt -> currentState.startWatts
                is BaselineFitnessTestState.Warmup -> currentState.startWatts
                is BaselineFitnessTestState.RampActive -> currentState.startWatts
                is BaselineFitnessTestState.Stopping -> currentState.startWatts
                is BaselineFitnessTestState.Cooldown -> currentState.startWatts
                is BaselineFitnessTestState.ResultCompute -> currentState.startWatts
                is BaselineFitnessTestState.Cancelled -> currentState.startWatts
                is BaselineFitnessTestState.Unavailable -> currentState.startWatts
                is BaselineFitnessTestState.ResultReady -> currentState.result.startWatts
                is BaselineFitnessTestState.Invalid -> currentState.result.startWatts
                BaselineFitnessTestState.Idle -> terminalResult?.startWatts
            },
            targetWatts = currentTargetWatts(),
            measuredPowerWatts = liveMetrics.powerWatts,
            measuredCadenceRpm = liveMetrics.cadenceRpm?.roundToInt(),
            measuredHeartRateBpm = liveMetrics.heartRateBpm,
            warmupRemainingSeconds = when (state) {
                is BaselineFitnessTestState.Warmup -> {
                    BaselineFitnessTestProtocol.WARMUP_DURATION_SEC - warmupProcessedSeconds
                }

                else -> null
            },
            rampElapsedSeconds = if (rampProcessedSeconds > 0 || state is BaselineFitnessTestState.RampActive) {
                rampProcessedSeconds
            } else {
                null
            },
            cooldownRemainingSeconds = when (state) {
                is BaselineFitnessTestState.Cooldown -> {
                    BaselineFitnessTestProtocol.COOLDOWN_DURATION_SEC - cooldownProcessedSeconds
                }

                else -> null
            },
            currentRampMinuteNumber = currentRampMinuteNumber(),
            validRampMinutes = finalizedRampSteps.count { step ->
                step.completedSeconds >= BaselineFitnessTestProtocol.RAMP_STEP_DURATION_SEC
            } + listOfNotNull(activeRampStep).count { step ->
                step.completedSeconds >= BaselineFitnessTestProtocol.RAMP_STEP_DURATION_SEC
            },
            awaitingWarmupStartSignal = state is BaselineFitnessTestState.Warmup &&
                warmupCountdownStartedAt == null,
            advisoryFallbackPromptVisible = state is BaselineFitnessTestState.AdvisoryFallbackPrompt,
            result = terminalResult ?: when (val currentState = state) {
                is BaselineFitnessTestState.ResultReady -> currentState.result
                is BaselineFitnessTestState.Invalid -> currentState.result
                else -> null
            },
            sensorProfile = sensorProfile,
            lastTransitionEvent = lastTransitionEvent,
        )
        onSnapshotChanged(latestSnapshot)
    }

    private fun currentTargetWatts(): Int? {
        return when (val currentState = state) {
            is BaselineFitnessTestState.Warmup -> currentState.startWatts
            is BaselineFitnessTestState.RampActive -> activeRampStep?.targetWatts ?: BaselineFitnessTestProtocol.targetWattsForRampMinute(
                startWatts = currentState.startWatts,
                rampMinuteIndex = ((rampProcessedSeconds - 1).coerceAtLeast(0)) /
                    BaselineFitnessTestProtocol.RAMP_STEP_DURATION_SEC,
            )

            is BaselineFitnessTestState.Cooldown -> currentState.startWatts
            is BaselineFitnessTestState.Stopping -> currentState.startWatts
            else -> null
        }
    }

    private fun currentRampMinuteNumber(): Int? {
        if (state !is BaselineFitnessTestState.RampActive) return null
        val target = activeRampStep ?: return null
        val finalizedBeforeActive = finalizedRampSteps.size
        return finalizedBeforeActive + if (target.completedSeconds > 0) 1 else 0
    }

    private fun resetRuntime() {
        state = BaselineFitnessTestState.Idle
        lastTransitionEvent = null
        availability = BaselineFitnessTestAvailability()
        liveMetrics = BaselineFitnessTestLiveMetricsSnapshot()
        sensorProfile = emptySensorProfile()
        requestStartedAt = null
        attemptStartedAt = null
        warmupCountdownStartedAt = null
        warmupProcessedSeconds = 0
        rampProcessedSeconds = 0
        cooldownProcessedSeconds = 0
        activeTestObservedSeconds = 0
        hrObservedSeconds = 0
        cadenceBelowThresholdSince = null
        hasObservedErgControlOwnership = false
        finalizedRampSteps.clear()
        activeRampStep = null
        terminalResult = null
        publishSnapshot()
    }

    private fun BaselineFitnessTestState.toUiPhase(): BaselineFitnessTestUiPhase {
        return when (this) {
            BaselineFitnessTestState.Idle -> BaselineFitnessTestUiPhase.IDLE
            is BaselineFitnessTestState.Precheck -> BaselineFitnessTestUiPhase.PRECHECK
            is BaselineFitnessTestState.RequestingControl -> BaselineFitnessTestUiPhase.REQUESTING_CONTROL
            is BaselineFitnessTestState.AdvisoryFallbackPrompt ->
                BaselineFitnessTestUiPhase.ADVISORY_FALLBACK_PROMPT

            is BaselineFitnessTestState.Warmup -> BaselineFitnessTestUiPhase.WARMUP
            is BaselineFitnessTestState.RampActive -> BaselineFitnessTestUiPhase.RAMP_ACTIVE
            is BaselineFitnessTestState.Stopping,
            is BaselineFitnessTestState.Cooldown -> BaselineFitnessTestUiPhase.COOLDOWN

            is BaselineFitnessTestState.ResultCompute,
            is BaselineFitnessTestState.ResultReady -> BaselineFitnessTestUiPhase.RESULT_READY

            is BaselineFitnessTestState.Unavailable -> BaselineFitnessTestUiPhase.UNAVAILABLE
            is BaselineFitnessTestState.Invalid -> BaselineFitnessTestUiPhase.INVALID
            is BaselineFitnessTestState.Cancelled -> BaselineFitnessTestUiPhase.CANCELLED
        }
    }

    /**
     * Per-minute ramp accumulator.
     *
     * HR smoothing uses a 30-sample sliding window. Because the coordinator records exactly one
     * sample per protocol second, this is equivalent to the spec's "30-second smoothed HR max".
     * The invariant breaks if the sample rate changes from 1 Hz.
     */
    private data class RampStepAccumulator(
        val targetWatts: Int,
        var completedSeconds: Int = 0,
        var maxPowerGapSec: Double = 0.0,
        val heartRateSamples: MutableList<Int> = mutableListOf(),
        var maxSmoothedHeartRateBpm: Int? = null,
    ) {
        fun recordHeartRate(heartRateBpm: Int) {
            heartRateSamples += heartRateBpm
            if (heartRateSamples.size < HR_SMOOTHING_WINDOW_SAMPLES) return
            val windowAverage = heartRateSamples
                .takeLast(HR_SMOOTHING_WINDOW_SAMPLES)
                .average()
                .roundToInt()
            maxSmoothedHeartRateBpm = maxOf(
                maxSmoothedHeartRateBpm ?: windowAverage,
                windowAverage,
            )
        }

        fun toResult(): BaselineFitnessTestRampStepResult {
            return BaselineFitnessTestRampStepResult(
                targetWatts = targetWatts,
                completedSeconds = completedSeconds,
                maxPowerGapSec = maxPowerGapSec,
                maxSmoothedHeartRateBpm = maxSmoothedHeartRateBpm,
            )
        }
    }
}

private fun BaselineFitnessTestSensorProfile.merge(
    liveMetrics: BaselineFitnessTestLiveMetricsSnapshot,
): BaselineFitnessTestSensorProfile {
    return BaselineFitnessTestSensorProfile(
        power = power || liveMetrics.powerWatts != null,
        heartRate = heartRate || liveMetrics.heartRateBpm != null,
        cadence = cadence || liveMetrics.cadenceRpm != null,
    )
}

/** Smoothing window size — must match the protocol tick rate (1 Hz → 30 samples = 30 seconds). */
private const val HR_SMOOTHING_WINDOW_SAMPLES = 30

private fun emptySensorProfile(): BaselineFitnessTestSensorProfile {
    return BaselineFitnessTestSensorProfile(
        power = false,
        heartRate = false,
        cadence = false,
    )
}
