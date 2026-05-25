package com.example.ergometerapp.baseline

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineFitnessTestCoordinatorTest {
    @Test
    fun startWithoutLivePowerWaitsInPrecheckUntilTrainerPreparationCompletes() {
        val runtimePort = FakeRuntimePort()
        val coordinator = BaselineFitnessTestCoordinator(runtimePort = runtimePort)
        val startAt = Instant.parse("2026-03-15T10:00:00Z")

        runtimePort.prepareTrainerResult = true
        runtimePort.preparationState = BaselineFitnessTestTrainerPreparationState.PENDING

        coordinator.start(
            priorFtpWatts = 220,
            now = startAt,
            liveMetrics = liveMetrics(
                now = startAt,
                powerWatts = null,
                cadenceRpm = null,
                trainerReady = false,
                controlGranted = false,
            ),
        )

        assertEquals(listOf("prepare_trainer"), runtimePort.events)
        assertEquals(BaselineFitnessTestUiPhase.PRECHECK, coordinator.snapshot().phase)

        coordinator.tick(
            now = startAt.plusSeconds(1),
            liveMetrics = liveMetrics(
                now = startAt.plusSeconds(1),
                powerWatts = 150,
                cadenceRpm = 90.0,
                trainerReady = false,
                controlGranted = false,
            ),
        )

        assertEquals(BaselineFitnessTestUiPhase.PRECHECK, coordinator.snapshot().phase)

        runtimePort.preparationState = BaselineFitnessTestTrainerPreparationState.READY
        coordinator.tick(
            now = startAt.plusSeconds(2),
            liveMetrics = liveMetrics(
                now = startAt.plusSeconds(2),
                powerWatts = 150,
                cadenceRpm = 90.0,
                trainerReady = true,
                controlGranted = false,
            ),
        )

        assertEquals(BaselineFitnessTestUiPhase.REQUESTING_CONTROL, coordinator.snapshot().phase)
        assertTrue(runtimePort.events.contains("request_control"))
    }

    @Test
    fun startWithoutLivePowerRequestsControlOnceTrainerIsReady() {
        val runtimePort = FakeRuntimePort()
        val coordinator = BaselineFitnessTestCoordinator(runtimePort = runtimePort)
        val startAt = Instant.parse("2026-03-15T10:00:00Z")

        runtimePort.prepareTrainerResult = true
        runtimePort.preparationState = BaselineFitnessTestTrainerPreparationState.PENDING

        coordinator.start(
            priorFtpWatts = 220,
            now = startAt,
            liveMetrics = liveMetrics(
                now = startAt,
                powerWatts = null,
                cadenceRpm = null,
                trainerReady = false,
                controlGranted = false,
            ),
        )

        runtimePort.preparationState = BaselineFitnessTestTrainerPreparationState.READY
        coordinator.tick(
            now = startAt.plusSeconds(1),
            liveMetrics = liveMetrics(
                now = startAt.plusSeconds(1),
                powerWatts = null,
                cadenceRpm = null,
                trainerReady = true,
                controlGranted = false,
                powerRecordedAt = startAt,
            ),
        )

        assertEquals(BaselineFitnessTestUiPhase.REQUESTING_CONTROL, coordinator.snapshot().phase)
        assertTrue(runtimePort.events.contains("request_control"))
    }

    @Test
    fun cancelDuringPrecheckReleasesPreparedTrainer() {
        val runtimePort = FakeRuntimePort()
        val coordinator = BaselineFitnessTestCoordinator(runtimePort = runtimePort)
        val startAt = Instant.parse("2026-03-15T10:00:00Z")

        runtimePort.prepareTrainerResult = true
        runtimePort.preparationState = BaselineFitnessTestTrainerPreparationState.PENDING

        coordinator.start(
            priorFtpWatts = 220,
            now = startAt,
            liveMetrics = liveMetrics(
                now = startAt,
                powerWatts = null,
                cadenceRpm = null,
                trainerReady = false,
                controlGranted = false,
            ),
        )

        coordinator.cancelAttempt(startAt.plusSeconds(1))

        assertEquals(BaselineFitnessTestUiPhase.CANCELLED, coordinator.snapshot().phase)
        assertTrue(runtimePort.events.contains("release_trainer"))
    }

    @Test
    fun ergWarmupCountdownWaitsForFirstActivePedalingSignal() {
        val runtimePort = FakeRuntimePort()
        val coordinator = BaselineFitnessTestCoordinator(runtimePort = runtimePort)
        val startAt = Instant.parse("2026-03-15T10:00:00Z")

        coordinator.start(
            priorFtpWatts = 220,
            now = startAt,
            liveMetrics = liveMetrics(
                now = startAt,
                powerWatts = 150,
                cadenceRpm = 90.0,
                trainerReady = true,
                controlGranted = false,
            ),
        )

        runtimePort.queueControlOutcome(BaselineFitnessTestTrainerControlRequestOutcome.GRANTED)
        coordinator.tick(
            now = startAt.plusSeconds(1),
            liveMetrics = liveMetrics(
                now = startAt.plusSeconds(1),
                powerWatts = 0,
                cadenceRpm = 0.0,
                trainerReady = true,
                controlGranted = true,
            ),
        )

        assertEquals(BaselineFitnessTestUiPhase.WARMUP, coordinator.snapshot().phase)
        assertEquals(300, coordinator.snapshot().warmupRemainingSeconds)
        assertTrue(coordinator.snapshot().awaitingWarmupStartSignal)

        coordinator.tick(
            now = startAt.plusSeconds(5),
            liveMetrics = liveMetrics(
                now = startAt.plusSeconds(5),
                powerWatts = 0,
                cadenceRpm = 0.0,
                trainerReady = true,
                controlGranted = true,
            ),
        )

        assertEquals(300, coordinator.snapshot().warmupRemainingSeconds)
        assertTrue(coordinator.snapshot().awaitingWarmupStartSignal)

        coordinator.tick(
            now = startAt.plusSeconds(6),
            liveMetrics = liveMetrics(
                now = startAt.plusSeconds(6),
                powerWatts = 155,
                cadenceRpm = 86.0,
                trainerReady = true,
                controlGranted = true,
            ),
        )

        assertEquals(300, coordinator.snapshot().warmupRemainingSeconds)
        assertFalse(coordinator.snapshot().awaitingWarmupStartSignal)

        coordinator.tick(
            now = startAt.plusSeconds(7),
            liveMetrics = liveMetrics(
                now = startAt.plusSeconds(7),
                powerWatts = 155,
                cadenceRpm = 86.0,
                trainerReady = true,
                controlGranted = true,
            ),
        )

        assertEquals(299, coordinator.snapshot().warmupRemainingSeconds)
    }

    @Test
    fun resetToIdleClearsCancelledStateForImmediateRetry() {
        val runtimePort = FakeRuntimePort()
        val coordinator = BaselineFitnessTestCoordinator(runtimePort = runtimePort)
        val startAt = Instant.parse("2026-03-15T10:00:00Z")

        coordinator.start(
            priorFtpWatts = 220,
            now = startAt,
            liveMetrics = liveMetrics(
                now = startAt,
                powerWatts = 150,
                cadenceRpm = 90.0,
                trainerReady = true,
                controlGranted = false,
            ),
        )

        runtimePort.queueControlOutcome(BaselineFitnessTestTrainerControlRequestOutcome.GRANTED)
        coordinator.tick(
            now = startAt.plusSeconds(1),
            liveMetrics = liveMetrics(
                now = startAt.plusSeconds(1),
                powerWatts = 150,
                cadenceRpm = 90.0,
                trainerReady = true,
                controlGranted = true,
            ),
        )

        coordinator.cancelAttempt(startAt.plusSeconds(2))
        assertEquals(BaselineFitnessTestUiPhase.CANCELLED, coordinator.snapshot().phase)

        coordinator.resetToIdle()

        assertEquals(BaselineFitnessTestUiPhase.IDLE, coordinator.snapshot().phase)
        assertFalse(coordinator.snapshot().advisoryFallbackPromptVisible)
        assertEquals(null, coordinator.snapshot().result)
    }

    @Test
    fun ergFlowCompletesRampAndPromotesCompletedResult() {
        val runtimePort = FakeRuntimePort()
        val recordedResults = mutableListOf<BaselineFitnessTestResult>()
        val coordinator = BaselineFitnessTestCoordinator(
            runtimePort = runtimePort,
            onResultRecorded = recordedResults::add,
        )
        val startAt = Instant.parse("2026-03-15T10:00:00Z")

        coordinator.start(
            priorFtpWatts = 220,
            now = startAt,
            liveMetrics = liveMetrics(
                now = startAt,
                powerWatts = 150,
                cadenceRpm = 92.0,
                heartRateBpm = 128,
                trainerReady = true,
                controlGranted = false,
            ),
        )

        assertEquals(listOf("request_control"), runtimePort.events)
        assertEquals(BaselineFitnessTestUiPhase.REQUESTING_CONTROL, coordinator.snapshot().phase)

        runtimePort.queueControlOutcome(BaselineFitnessTestTrainerControlRequestOutcome.GRANTED)
        tickRange(
            coordinator = coordinator,
            startExclusive = startAt,
            endInclusive = startAt.plusSeconds(301),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 150,
                    cadenceRpm = 92.0,
                    heartRateBpm = 130,
                    trainerReady = true,
                    controlGranted = true,
                )
            },
        )

        assertEquals(BaselineFitnessTestUiPhase.RAMP_ACTIVE, coordinator.snapshot().phase)
        assertTrue(runtimePort.events.contains("set_target:100"))

        val rampStart = startAt.plusSeconds(301)
        tickRange(
            coordinator = coordinator,
            startExclusive = rampStart,
            endInclusive = rampStart.plusSeconds(6 * 60),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 255,
                    cadenceRpm = 95.0,
                    heartRateBpm = 168,
                    trainerReady = true,
                    controlGranted = true,
                )
            },
        )

        coordinator.stopRamp(rampStart.plusSeconds(6 * 60))

        assertEquals(BaselineFitnessTestUiPhase.COOLDOWN, coordinator.snapshot().phase)
        coordinator.skipCooldown(rampStart.plusSeconds(6 * 60 + 5))

        val result = recordedResults.single()
        assertEquals(BaselineFitnessTestStatus.COMPLETED, result.status)
        assertEquals(6, result.validRampMinutes)
        assertEquals(200, result.lastFullStepWatts)
        assertEquals(150, result.ftpEstimateWatts)
        assertEquals(BaselineFitnessTestStopReason.MANUAL_STOP, result.stopReason)
        assertEquals(BaselineFitnessTestUiPhase.RESULT_READY, coordinator.snapshot().phase)
        assertTrue(runtimePort.events.contains("reset_trainer_to_idle"))
        assertTrue(runtimePort.events.contains("set_target:200"))
    }

    @Test
    fun failedControlGrantShowsAdvisoryPromptAndAcceptedFallbackAvoidsErgWrites() {
        val runtimePort = FakeRuntimePort()
        val coordinator = BaselineFitnessTestCoordinator(runtimePort = runtimePort)
        val startAt = Instant.parse("2026-03-15T10:00:00Z")

        coordinator.start(
            priorFtpWatts = 250,
            now = startAt,
            liveMetrics = liveMetrics(
                now = startAt,
                powerWatts = 190,
                cadenceRpm = 88.0,
                trainerReady = true,
                controlGranted = false,
            ),
        )

        runtimePort.queueControlOutcome(BaselineFitnessTestTrainerControlRequestOutcome.FAILED)
        coordinator.tick(
            now = startAt.plusSeconds(1),
            liveMetrics = liveMetrics(
                now = startAt.plusSeconds(1),
                powerWatts = 188,
                cadenceRpm = 90.0,
                trainerReady = true,
                controlGranted = false,
            ),
        )

        assertEquals(
            BaselineFitnessTestUiPhase.ADVISORY_FALLBACK_PROMPT,
            coordinator.snapshot().phase,
        )
        assertTrue(coordinator.snapshot().advisoryFallbackPromptVisible)

        coordinator.acceptAdvisoryFallback(startAt.plusSeconds(2))

        assertEquals(BaselineFitnessTestUiPhase.WARMUP, coordinator.snapshot().phase)
        assertEquals(BaselineFitnessTestControlMode.ADVISORY, coordinator.snapshot().controlMode)
        assertFalse(runtimePort.events.any { it.startsWith("set_target:") })
    }

    @Test
    fun powerSignalLossAfterValidRampProducesCompletedResult() {
        val runtimePort = FakeRuntimePort()
        val recordedResults = mutableListOf<BaselineFitnessTestResult>()
        val coordinator = BaselineFitnessTestCoordinator(
            runtimePort = runtimePort,
            onResultRecorded = recordedResults::add,
        )
        val startAt = Instant.parse("2026-03-15T10:00:00Z")

        coordinator.start(
            priorFtpWatts = 220,
            now = startAt,
            liveMetrics = liveMetrics(
                now = startAt,
                powerWatts = 170,
                cadenceRpm = 90.0,
                trainerReady = false,
                controlGranted = false,
            ),
        )
        coordinator.acceptAdvisoryFallback(startAt.plusSeconds(1))

        tickRange(
            coordinator = coordinator,
            startExclusive = startAt.plusSeconds(1),
            endInclusive = startAt.plusSeconds(301),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 170,
                    cadenceRpm = 90.0,
                    trainerReady = true,
                    controlGranted = false,
                )
            },
        )

        val rampStart = startAt.plusSeconds(301)
        tickRange(
            coordinator = coordinator,
            startExclusive = rampStart,
            endInclusive = rampStart.plusSeconds(8 * 60),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 260,
                    cadenceRpm = 92.0,
                    trainerReady = true,
                    controlGranted = false,
                )
            },
        )

        val lastFreshPowerAt = rampStart.plusSeconds(8 * 60)
        tickRange(
            coordinator = coordinator,
            startExclusive = lastFreshPowerAt,
            endInclusive = lastFreshPowerAt.plusSeconds(9),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 260,
                    powerRecordedAt = lastFreshPowerAt,
                    cadenceRpm = 92.0,
                    trainerReady = true,
                    controlGranted = false,
                )
            },
        )

        coordinator.skipCooldown(lastFreshPowerAt.plusSeconds(10))

        val result = recordedResults.single()
        assertEquals(BaselineFitnessTestStatus.COMPLETED, result.status)
        assertEquals(BaselineFitnessTestStopReason.POWER_SIGNAL_LOST, result.stopReason)
        assertEquals(8, result.validRampMinutes)
        assertEquals(BaselineFitnessTestConfidence.LOW, result.confidence)
    }

    @Test
    fun ergControlLossAfterOwnershipCancelsAttempt() {
        val runtimePort = FakeRuntimePort()
        val recordedResults = mutableListOf<BaselineFitnessTestResult>()
        val coordinator = BaselineFitnessTestCoordinator(
            runtimePort = runtimePort,
            onResultRecorded = recordedResults::add,
        )
        val startAt = Instant.parse("2026-03-15T10:00:00Z")

        coordinator.start(
            priorFtpWatts = 200,
            now = startAt,
            liveMetrics = liveMetrics(
                now = startAt,
                powerWatts = 140,
                cadenceRpm = 90.0,
                trainerReady = true,
                controlGranted = false,
            ),
        )
        runtimePort.queueControlOutcome(BaselineFitnessTestTrainerControlRequestOutcome.GRANTED)
        coordinator.tick(
            now = startAt.plusSeconds(1),
            liveMetrics = liveMetrics(
                now = startAt.plusSeconds(1),
                powerWatts = 140,
                cadenceRpm = 90.0,
                trainerReady = true,
                controlGranted = true,
            ),
        )

        coordinator.tick(
            now = startAt.plusSeconds(2),
            liveMetrics = liveMetrics(
                now = startAt.plusSeconds(2),
                powerWatts = 140,
                cadenceRpm = 88.0,
                trainerReady = true,
                controlGranted = false,
            ),
        )

        val result = recordedResults.single()
        assertEquals(BaselineFitnessTestUiPhase.CANCELLED, coordinator.snapshot().phase)
        assertEquals(BaselineFitnessTestStatus.CANCELLED, result.status)
        assertEquals(BaselineFitnessTestStopReason.CONTROL_LOST_MID_TEST, result.stopReason)
        assertNotNull(coordinator.snapshot().result)
    }

    @Test
    fun cadenceDropAutoStopsRampAfterHoldPeriod() {
        val runtimePort = FakeRuntimePort()
        val recordedResults = mutableListOf<BaselineFitnessTestResult>()
        val coordinator = BaselineFitnessTestCoordinator(
            runtimePort = runtimePort,
            onResultRecorded = recordedResults::add,
        )
        val startAt = Instant.parse("2026-03-15T10:00:00Z")

        coordinator.start(
            priorFtpWatts = 200,
            now = startAt,
            liveMetrics = liveMetrics(
                now = startAt,
                powerWatts = 130,
                cadenceRpm = 90.0,
                trainerReady = false,
                controlGranted = false,
            ),
        )
        coordinator.acceptAdvisoryFallback(startAt.plusSeconds(1))

        // Warmup
        tickRange(
            coordinator = coordinator,
            startExclusive = startAt.plusSeconds(1),
            endInclusive = startAt.plusSeconds(301),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 130,
                    cadenceRpm = 90.0,
                    trainerReady = true,
                    controlGranted = false,
                )
            },
        )

        // 6 full ramp minutes with normal cadence
        val rampStart = startAt.plusSeconds(301)
        tickRange(
            coordinator = coordinator,
            startExclusive = rampStart,
            endInclusive = rampStart.plusSeconds(6 * 60),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 240,
                    cadenceRpm = 90.0,
                    trainerReady = true,
                    controlGranted = false,
                )
            },
        )

        // Cadence drops below 30 for 10 seconds → auto-stop
        val dropStart = rampStart.plusSeconds(6 * 60)
        tickRange(
            coordinator = coordinator,
            startExclusive = dropStart,
            endInclusive = dropStart.plusSeconds(10),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 240,
                    cadenceRpm = 25.0,
                    trainerReady = true,
                    controlGranted = false,
                )
            },
        )

        coordinator.skipCooldown(dropStart.plusSeconds(11))

        val result = recordedResults.single()
        assertEquals(BaselineFitnessTestStatus.COMPLETED, result.status)
        assertEquals(BaselineFitnessTestStopReason.CADENCE_DROP, result.stopReason)
        assertEquals(6, result.validRampMinutes)
    }

    @Test
    fun cancelDuringCooldownProducesCancelledResult() {
        val runtimePort = FakeRuntimePort()
        val recordedResults = mutableListOf<BaselineFitnessTestResult>()
        val coordinator = BaselineFitnessTestCoordinator(
            runtimePort = runtimePort,
            onResultRecorded = recordedResults::add,
        )
        val startAt = Instant.parse("2026-03-15T10:00:00Z")

        coordinator.start(
            priorFtpWatts = 200,
            now = startAt,
            liveMetrics = liveMetrics(
                now = startAt,
                powerWatts = 130,
                cadenceRpm = 90.0,
                trainerReady = false,
                controlGranted = false,
            ),
        )
        coordinator.acceptAdvisoryFallback(startAt.plusSeconds(1))

        tickRange(
            coordinator = coordinator,
            startExclusive = startAt.plusSeconds(1),
            endInclusive = startAt.plusSeconds(301),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 130,
                    cadenceRpm = 90.0,
                    trainerReady = true,
                    controlGranted = false,
                )
            },
        )

        val rampStart = startAt.plusSeconds(301)
        tickRange(
            coordinator = coordinator,
            startExclusive = rampStart,
            endInclusive = rampStart.plusSeconds(6 * 60),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 240,
                    cadenceRpm = 90.0,
                    trainerReady = true,
                    controlGranted = false,
                )
            },
        )

        coordinator.stopRamp(rampStart.plusSeconds(6 * 60))
        assertEquals(BaselineFitnessTestUiPhase.COOLDOWN, coordinator.snapshot().phase)

        coordinator.cancelAttempt(rampStart.plusSeconds(6 * 60 + 5))

        val result = recordedResults.single()
        assertEquals(BaselineFitnessTestStatus.CANCELLED, result.status)
        assertEquals(BaselineFitnessTestStopReason.USER_CANCEL, result.stopReason)
        assertEquals(BaselineFitnessTestUiPhase.CANCELLED, coordinator.snapshot().phase)
    }

    @Test
    fun cancelDuringErgCooldownClearsErgTargetAndProducesCancelledResult() {
        val runtimePort = FakeRuntimePort()
        val recordedResults = mutableListOf<BaselineFitnessTestResult>()
        val coordinator = BaselineFitnessTestCoordinator(
            runtimePort = runtimePort,
            onResultRecorded = recordedResults::add,
        )
        val startAt = Instant.parse("2026-03-15T10:00:00Z")

        coordinator.start(
            priorFtpWatts = 220,
            now = startAt,
            liveMetrics = liveMetrics(
                now = startAt,
                powerWatts = 150,
                cadenceRpm = 92.0,
                trainerReady = true,
                controlGranted = false,
            ),
        )

        runtimePort.queueControlOutcome(BaselineFitnessTestTrainerControlRequestOutcome.GRANTED)
        tickRange(
            coordinator = coordinator,
            startExclusive = startAt,
            endInclusive = startAt.plusSeconds(301),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 150,
                    cadenceRpm = 92.0,
                    trainerReady = true,
                    controlGranted = true,
                )
            },
        )

        val rampStart = startAt.plusSeconds(301)
        tickRange(
            coordinator = coordinator,
            startExclusive = rampStart,
            endInclusive = rampStart.plusSeconds(6 * 60),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 250,
                    cadenceRpm = 95.0,
                    trainerReady = true,
                    controlGranted = true,
                )
            },
        )

        coordinator.stopRamp(rampStart.plusSeconds(6 * 60))
        assertEquals(BaselineFitnessTestUiPhase.COOLDOWN, coordinator.snapshot().phase)

        // ERG target was set to startWatts during cooldown — cancel must clear it
        val eventsBeforeCancel = runtimePort.events.toList()
        assertTrue(eventsBeforeCancel.contains("set_target:100"))

        coordinator.cancelAttempt(rampStart.plusSeconds(6 * 60 + 5))

        assertEquals(BaselineFitnessTestUiPhase.CANCELLED, coordinator.snapshot().phase)
        assertTrue(
            runtimePort.events.count { it == "reset_trainer_to_idle" } >=
                eventsBeforeCancel.count { it == "reset_trainer_to_idle" } + 1,
        )
        val result = recordedResults.single()
        assertEquals(BaselineFitnessTestStatus.CANCELLED, result.status)
    }

    @Test
    fun advisoryDisconnectDuringRampStopsWithCooldownAndProducesCompletedResult() {
        val runtimePort = FakeRuntimePort()
        val recordedResults = mutableListOf<BaselineFitnessTestResult>()
        val coordinator = BaselineFitnessTestCoordinator(
            runtimePort = runtimePort,
            onResultRecorded = recordedResults::add,
        )
        val startAt = Instant.parse("2026-03-15T10:00:00Z")

        coordinator.start(
            priorFtpWatts = 200,
            now = startAt,
            liveMetrics = liveMetrics(
                now = startAt,
                powerWatts = 130,
                cadenceRpm = 90.0,
                trainerReady = false,
                controlGranted = false,
            ),
        )
        coordinator.acceptAdvisoryFallback(startAt.plusSeconds(1))

        tickRange(
            coordinator = coordinator,
            startExclusive = startAt.plusSeconds(1),
            endInclusive = startAt.plusSeconds(301),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 130,
                    cadenceRpm = 90.0,
                    trainerReady = true,
                    controlGranted = false,
                )
            },
        )

        val rampStart = startAt.plusSeconds(301)
        tickRange(
            coordinator = coordinator,
            startExclusive = rampStart,
            endInclusive = rampStart.plusSeconds(7 * 60),
            metrics = { now ->
                liveMetrics(
                    now = now,
                    powerWatts = 240,
                    cadenceRpm = 90.0,
                    trainerReady = true,
                    controlGranted = false,
                )
            },
        )

        // Trainer disconnects during advisory ramp → auto-stop with device disconnect
        val disconnectAt = rampStart.plusSeconds(7 * 60 + 1)
        coordinator.tick(
            now = disconnectAt,
            liveMetrics = liveMetrics(
                now = disconnectAt,
                powerWatts = 240,
                cadenceRpm = 90.0,
                trainerReady = false,
                controlGranted = false,
            ),
        )

        // Should enter cooldown (advisory disconnect is non-fatal stop)
        assertEquals(BaselineFitnessTestUiPhase.COOLDOWN, coordinator.snapshot().phase)

        coordinator.skipCooldown(disconnectAt.plusSeconds(1))

        val result = recordedResults.single()
        assertEquals(BaselineFitnessTestStatus.COMPLETED, result.status)
        assertEquals(BaselineFitnessTestStopReason.DEVICE_DISCONNECT, result.stopReason)
        assertEquals(7, result.validRampMinutes)
    }

    private fun tickRange(
        coordinator: BaselineFitnessTestCoordinator,
        startExclusive: Instant,
        endInclusive: Instant,
        metrics: (Instant) -> BaselineFitnessTestLiveMetricsSnapshot,
    ) {
        var current = startExclusive.plusSeconds(1)
        while (!current.isAfter(endInclusive)) {
            coordinator.tick(
                now = current,
                liveMetrics = metrics(current),
            )
            current = current.plusSeconds(1)
        }
    }

    private fun liveMetrics(
        now: Instant,
        powerWatts: Int?,
        cadenceRpm: Double?,
        heartRateBpm: Int? = null,
        trainerReady: Boolean,
        controlGranted: Boolean,
        powerRecordedAt: Instant = now,
        heartRateRecordedAt: Instant = now,
    ): BaselineFitnessTestLiveMetricsSnapshot {
        return BaselineFitnessTestLiveMetricsSnapshot(
            powerWatts = powerWatts,
            powerRecordedAt = powerWatts?.let { powerRecordedAt },
            cadenceRpm = cadenceRpm,
            heartRateBpm = heartRateBpm,
            heartRateRecordedAt = heartRateBpm?.let { heartRateRecordedAt },
            trainerReady = trainerReady,
            controlGranted = controlGranted,
        )
    }

    private class FakeRuntimePort : BaselineFitnessTestRuntimePort {
        val events = mutableListOf<String>()
        private val queuedOutcomes = ArrayDeque<BaselineFitnessTestTrainerControlRequestOutcome>()
        var prepareTrainerResult = true
        var preparationState = BaselineFitnessTestTrainerPreparationState.IDLE

        override fun prepareTrainer(): Boolean {
            events += "prepare_trainer"
            return prepareTrainerResult
        }

        override fun releaseTrainer() {
            events += "release_trainer"
            preparationState = BaselineFitnessTestTrainerPreparationState.IDLE
        }

        override fun trainerPreparationState(): BaselineFitnessTestTrainerPreparationState {
            return preparationState
        }

        override fun requestTrainerControl(): Boolean {
            events += "request_control"
            return true
        }

        override fun setErgTarget(watts: Int) {
            events += "set_target:$watts"
        }

        override fun resetTrainerToIdle() {
            events += "reset_trainer_to_idle"
        }

        override fun consumeTrainerControlRequestOutcome(): BaselineFitnessTestTrainerControlRequestOutcome? {
            if (queuedOutcomes.isEmpty()) return null
            return queuedOutcomes.removeFirst()
        }

        fun queueControlOutcome(outcome: BaselineFitnessTestTrainerControlRequestOutcome) {
            queuedOutcomes += outcome
        }
    }
}
