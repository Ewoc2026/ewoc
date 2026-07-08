package io.github.ewoc2026.ewoc.session

import android.content.ContextWrapper
import io.github.ewoc2026.ewoc.ftms.IndoorBikeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    @Test
    fun startSessionSetsPhaseToRunning() {
        val manager = buildManager()

        manager.startSession(ftpWatts = 200)

        assertEquals(SessionPhase.RUNNING, manager.getPhase())
    }

    @Test
    fun stopSessionWithoutDataProducesZeroDurationSummary() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        clock.nowMs += 5_000L
        manager.stopSession()

        assertEquals(SessionPhase.STOPPED, manager.getPhase())
        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(5, summary!!.durationSeconds)
        assertNull(summary.avgPower)
        assertNull(summary.avgCadence)
        assertNull(summary.avgHeartRate)
    }

    @Test
    fun powerSamplesAggregateToTimeWeightedAverage() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(bikeDataWithPower(200))
        clock.nowMs += 3_000L
        manager.updateBikeData(bikeDataWithPower(100))
        clock.nowMs += 3_000L
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(150, summary!!.avgPower)
        assertEquals(200, summary.maxPower)
    }

    @Test
    fun cadenceSamplesAggregateToTimeWeightedAverage() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(bikeDataWithCadence(90.0))
        clock.nowMs += 2_000L
        manager.updateBikeData(bikeDataWithCadence(80.0))
        clock.nowMs += 2_000L
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(85, summary!!.avgCadence)
        assertEquals(90, summary.maxCadence)
    }

    @Test
    fun externalHeartRateSupersedesBikeHeartRate() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateHeartRate(140)
        manager.updateBikeData(bikeDataWithPower(200, heartRateBpm = 100))
        clock.nowMs += 4_000L
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(140, summary!!.avgHeartRate)
        assertEquals(140, summary.maxHeartRate)
    }

    @Test
    fun bikeHeartRateUsedWhenNoExternalStrap() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(bikeDataWithPower(200, heartRateBpm = 155))
        clock.nowMs += 4_000L
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(155, summary!!.avgHeartRate)
    }

    @Test
    fun heartRateOutOfRangeIgnored() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(bikeDataWithPower(200, heartRateBpm = 10))
        clock.nowMs += 4_000L
        manager.stopSession()

        val summary = manager.lastSummary
        assertNull(summary!!.avgHeartRate)
    }

    @Test
    fun pauseExcludesTimeFromDuration() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(bikeDataWithPower(200))
        clock.nowMs += 3_000L
        manager.pauseSessionActivity()
        clock.nowMs += 10_000L
        manager.resumeSessionActivity()
        manager.updateBikeData(bikeDataWithPower(200))
        clock.nowMs += 2_000L
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(5, summary!!.durationSeconds)
        assertEquals(15, summary.elapsedDurationSeconds)
    }

    @Test
    fun pauseStopsMetricAggregation() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(bikeDataWithPower(200))
        clock.nowMs += 2_000L
        manager.pauseSessionActivity()
        clock.nowMs += 1_000L
        manager.updateBikeData(bikeDataWithPower(50))
        clock.nowMs += 1_000L
        manager.updateBikeData(bikeDataWithPower(50))
        clock.nowMs += 8_000L
        manager.resumeSessionActivity()
        manager.updateBikeData(bikeDataWithPower(200))
        clock.nowMs += 2_000L
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(200, summary!!.avgPower)
    }

    @Test
    fun distanceAndEnergyFromLatestBikeData() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(
            bikeData(
                powerW = 200,
                distanceMeters = 500,
                totalEnergyKcal = 20,
            ),
        )
        clock.nowMs += 2_000L
        manager.updateBikeData(
            bikeData(
                powerW = 200,
                distanceMeters = 1500,
                totalEnergyKcal = 60,
            ),
        )
        clock.nowMs += 2_000L
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(1500, summary!!.distanceMeters)
        assertEquals(60, summary.totalEnergyKcal)
    }

    @Test
    fun bridgeCumulativeTrainerMetricsKeepsTotalsMonotonicAcrossTrainerReset() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(
            bikeData(
                powerW = 180,
                distanceMeters = 149,
                totalEnergyKcal = 4,
            ),
        )
        manager.bridgeCumulativeTrainerMetrics(
            distanceMeters = 149,
            totalEnergyKcal = 4,
        )
        clock.nowMs += 1_000L
        manager.updateBikeData(
            bikeData(
                powerW = 160,
                distanceMeters = 0,
                totalEnergyKcal = 0,
            ),
        )
        clock.nowMs += 1_000L
        manager.updateBikeData(
            bikeData(
                powerW = 170,
                distanceMeters = 31,
                totalEnergyKcal = 2,
            ),
        )
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(180, summary!!.distanceMeters)
        assertEquals(6, summary.totalEnergyKcal)

        val timeline = manager.exportTimelineSnapshot()
        assertEquals(149, timeline[0].distanceMeters)
        assertEquals(4, timeline[0].totalEnergyKcal)
        assertEquals(149, timeline[1].distanceMeters)
        assertEquals(4, timeline[1].totalEnergyKcal)
        assertEquals(180, timeline[2].distanceMeters)
        assertEquals(6, timeline[2].totalEnergyKcal)
    }

    @Test
    fun bridgeCumulativeTrainerMetricsDoesNotDoubleCountWhenTrainerCountersContinue() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(
            bikeData(
                powerW = 180,
                distanceMeters = 149,
                totalEnergyKcal = 4,
            ),
        )
        manager.bridgeCumulativeTrainerMetrics(
            distanceMeters = 149,
            totalEnergyKcal = 4,
        )
        clock.nowMs += 1_000L
        manager.updateBikeData(
            bikeData(
                powerW = 160,
                distanceMeters = 160,
                totalEnergyKcal = 5,
            ),
        )
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(160, summary!!.distanceMeters)
        assertEquals(5, summary.totalEnergyKcal)
    }

    @Test
    fun timelineSamplesRecordedPerSecond() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(bikeDataWithPower(200))
        clock.nowMs += 1_000L
        manager.updateBikeData(bikeDataWithPower(210))
        clock.nowMs += 1_000L
        manager.updateBikeData(bikeDataWithPower(220))
        clock.nowMs += 1_000L
        manager.stopSession()

        val timeline = manager.exportTimelineSnapshot()
        assertTrue(timeline.size >= 3)
        assertEquals(200, timeline.first().powerWatts)
    }

    @Test
    fun exportSnapshotReturnsNullBeforeStop() {
        val manager = buildManager()

        manager.startSession(ftpWatts = 200)

        assertNull(manager.buildExportSnapshot())
    }

    @Test
    fun exportSnapshotReturnsDataAfterStop() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(bikeDataWithPower(200))
        clock.nowMs += 2_000L
        manager.stopSession()

        val snapshot = manager.buildExportSnapshot()
        assertNotNull(snapshot)
        assertNotNull(snapshot!!.summary)
    }

    @Test
    fun continuationCarryoverMergesSummaryAcrossHardCutoverSegments() {
        val clock = FakeClock(1_000_000L)
        val persisted = mutableListOf<SessionSummary>()
        val manager = buildManager(
            clock = clock,
            persistSummary = { _, summary -> persisted += summary },
        )

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(
            bikeData(
                powerW = 200,
                cadenceRpm = 90.0,
                heartRateBpm = 140,
                distanceMeters = 149,
                totalEnergyKcal = 4,
            ),
        )
        clock.nowMs += 10_000L
        manager.armContinuationCheckpoint()
        manager.stopSession()
        manager.promoteStoppedSessionToContinuationCarryover()

        manager.startContinuationSegment(ftpWatts = 200)
        manager.bridgeCumulativeTrainerMetrics(
            distanceMeters = 149,
            totalEnergyKcal = 4,
        )
        manager.updateBikeData(
            bikeData(
                powerW = 100,
                cadenceRpm = 80.0,
                heartRateBpm = 130,
                distanceMeters = 0,
                totalEnergyKcal = 0,
            ),
        )
        clock.nowMs += 5_000L
        manager.updateBikeData(
            bikeData(
                powerW = 100,
                cadenceRpm = 80.0,
                heartRateBpm = 130,
                distanceMeters = 31,
                totalEnergyKcal = 2,
            ),
        )
        manager.stopSession()

        val summary = manager.lastSummary
        requireNotNull(summary)
        assertEquals(15, summary.durationSeconds)
        assertEquals(15, summary.elapsedDurationSeconds)
        assertEquals(166, summary.avgPower)
        assertEquals(200, summary.maxPower)
        assertEquals(86, summary.avgCadence)
        assertEquals(90, summary.maxCadence)
        assertEquals(136, summary.avgHeartRate)
        assertEquals(140, summary.maxHeartRate)
        assertEquals(180, summary.distanceMeters)
        assertEquals(6, summary.totalEnergyKcal)
        assertEquals(0.2, summary.actualTss!!, 0.01)
        assertEquals(1, persisted.size)
    }

    @Test
    fun armedBridgeSurvivesContinuationStartAndIsVisibleBeforeFirstResetPacket() {
        val clock = FakeClock(1_000_000L)
        val emittedStates = mutableListOf<io.github.ewoc2026.ewoc.SessionState>()
        val manager = buildManager(
            clock = clock,
            onStateUpdated = { state -> emittedStates += state },
        )

        manager.armNextStartAsContinuationSegment()
        manager.bridgeCumulativeTrainerMetrics(
            distanceMeters = 149,
            totalEnergyKcal = 4,
        )

        manager.startSession(ftpWatts = 200, startActive = false)

        val startState = emittedStates.last()
        assertEquals(149, startState.logicalDistanceMeters)
        assertEquals(4, startState.logicalTotalEnergyKcal)

        manager.updateBikeData(
            bikeData(
                powerW = 150,
                distanceMeters = 0,
                totalEnergyKcal = 0,
            ),
        )

        val bridgedState = emittedStates.last()
        assertEquals(149, bridgedState.logicalDistanceMeters)
        assertEquals(4, bridgedState.logicalTotalEnergyKcal)
    }

    @Test
    fun continuationCarryoverMergesTimelineForExport() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(
            bikeData(
                powerW = 200,
                distanceMeters = 100,
                totalEnergyKcal = 3,
            ),
        )
        clock.nowMs += 1_000L
        manager.armContinuationCheckpoint()
        manager.stopSession()
        manager.promoteStoppedSessionToContinuationCarryover()

        manager.startContinuationSegment(ftpWatts = 200)
        manager.bridgeCumulativeTrainerMetrics(
            distanceMeters = 100,
            totalEnergyKcal = 3,
        )
        manager.updateBikeData(
            bikeData(
                powerW = 120,
                distanceMeters = 0,
                totalEnergyKcal = 0,
            ),
        )
        clock.nowMs += 1_000L
        manager.updateBikeData(
            bikeData(
                powerW = 120,
                distanceMeters = 20,
                totalEnergyKcal = 1,
            ),
        )
        manager.stopSession()

        val snapshot = manager.buildExportSnapshot()
        requireNotNull(snapshot)
        assertTrue(snapshot.timeline.size >= 4)
        assertEquals(100, snapshot.timeline.first().distanceMeters)
        assertEquals(120, snapshot.timeline.last().distanceMeters)
        assertEquals(4, snapshot.summary.totalEnergyKcal)
    }

    @Test
    fun summaryPersistedOnStop() {
        val clock = FakeClock(1_000_000L)
        val persisted = mutableListOf<SessionSummary>()
        val manager = buildManager(
            clock = clock,
            persistSummary = { _, summary -> persisted += summary },
        )

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(bikeDataWithPower(200))
        clock.nowMs += 2_000L
        manager.stopSession()

        assertEquals(1, persisted.size)
        assertEquals(2, persisted.first().durationSeconds)
    }

    @Test
    fun startSessionAfterStopClearsPriorState() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(bikeDataWithPower(300))
        clock.nowMs += 2_000L
        manager.stopSession()
        val firstSummary = manager.lastSummary

        manager.startSession(ftpWatts = 200)
        assertNull(manager.lastSummary)
        assertEquals(SessionPhase.RUNNING, manager.getPhase())

        manager.updateBikeData(bikeDataWithPower(100))
        clock.nowMs += 2_000L
        manager.stopSession()

        val secondSummary = manager.lastSummary
        assertNotNull(secondSummary)
        assertEquals(100, secondSummary!!.avgPower)
        assertEquals(300, firstSummary!!.avgPower)
    }

    @Test
    fun timelineCallbackFiresOnEachNewSample() {
        val clock = FakeClock(1_000_000L)
        val snapshots = mutableListOf<List<SessionSample>>()
        val manager = buildManager(clock = clock, onTimelineSampleRecorded = { snapshots.add(it) })

        manager.startSession(ftpWatts = 200)
        // First sample at t=0
        manager.updateBikeData(bikeDataWithPower(150))
        // Same second — should not fire again
        manager.updateBikeData(bikeDataWithPower(160))
        // Advance 1 second — new sample
        clock.nowMs += 1_000L
        manager.updateBikeData(bikeDataWithPower(170))

        // startSession clears with emptyList, then 2 sample recordings
        assertEquals(3, snapshots.size)
        assertTrue(snapshots[0].isEmpty()) // clear on start
        assertEquals(1, snapshots[1].size)
        assertEquals(150, snapshots[1][0].powerWatts)
        assertEquals(2, snapshots[2].size)
        assertEquals(170, snapshots[2][1].powerWatts)
    }

    @Test
    fun timelineCallbackRespectsWindowSize() {
        val clock = FakeClock(1_000_000L)
        val snapshots = mutableListOf<List<SessionSample>>()
        val manager = buildManager(
            clock = clock,
            onTimelineSampleRecorded = { snapshots.add(it) },
            liveTimelineWindowSeconds = 3,
        )

        manager.startSession(ftpWatts = 200)
        // Record 5 samples (5 seconds)
        for (i in 0 until 5) {
            manager.updateBikeData(bikeDataWithPower(100 + i * 10))
            clock.nowMs += 1_000L
        }

        val lastSnapshot = snapshots.last()
        // Window is 3 seconds, so at most 3 samples in the live window
        assertTrue(lastSnapshot.size <= 3)
        // Most recent sample should be the last recorded
        assertEquals(140, lastSnapshot.last().powerWatts)
    }

    @Test
    fun stopWhileIdleIsNoOp() {
        val manager = buildManager()

        manager.stopSession()

        assertEquals(SessionPhase.IDLE, manager.getPhase())
        assertNull(manager.lastSummary)
    }

    @Test
    fun resetToIdleClearsSessionRuntimeWithoutPersistingSummary() {
        val clock = FakeClock(1_000_000L)
        val persisted = mutableListOf<SessionSummary>()
        val manager = buildManager(
            clock = clock,
            persistSummary = { _, summary -> persisted += summary },
        )

        manager.startSession(ftpWatts = 200)
        manager.updateBikeData(
            bikeData(
                powerW = 200,
                distanceMeters = 300,
                totalEnergyKcal = 9,
            ),
        )
        clock.nowMs += 2_000L
        manager.resetToIdle()

        assertEquals(SessionPhase.IDLE, manager.getPhase())
        assertNull(manager.lastSummary)
        assertTrue(manager.exportTimelineSnapshot().isEmpty())
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun startInactiveDoesNotAccumulateDurationUntilResume() {
        val clock = FakeClock(1_000_000L)
        val manager = buildManager(clock = clock)

        manager.startSession(ftpWatts = 200, startActive = false)
        clock.nowMs += 5_000L
        manager.resumeSessionActivity()
        manager.updateBikeData(bikeDataWithPower(200))
        clock.nowMs += 3_000L
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(3, summary!!.durationSeconds)
    }

    private data class FakeClock(var nowMs: Long = 0L)

    private fun buildManager(
        clock: FakeClock = FakeClock(1_000_000L),
        persistSummary: (android.content.Context, SessionSummary) -> Unit = { _, _ -> },
        onTimelineSampleRecorded: (List<SessionSample>) -> Unit = {},
        onStateUpdated: (io.github.ewoc2026.ewoc.SessionState) -> Unit = {},
        liveTimelineWindowSeconds: Int = SessionManager.DEFAULT_LIVE_TIMELINE_WINDOW_SECONDS,
    ): SessionManager {
        return SessionManager(
            context = ContextWrapper(null),
            onStateUpdated = onStateUpdated,
            onTimelineSampleRecorded = onTimelineSampleRecorded,
            nowMillis = { clock.nowMs },
            persistSummary = persistSummary,
            summaryPersistenceExecutor = { it.run() },
            liveTimelineWindowSeconds = liveTimelineWindowSeconds,
        )
    }

    private fun bikeDataWithPower(
        powerW: Int,
        heartRateBpm: Int? = null,
    ): IndoorBikeData {
        return IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = null,
            averageCadenceRpm = null,
            totalDistanceMeters = null,
            resistanceLevel = null,
            instantaneousPowerW = powerW,
            averagePowerW = null,
            totalEnergyKcal = null,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = heartRateBpm,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null,
        )
    }

    private fun bikeDataWithCadence(cadenceRpm: Double): IndoorBikeData {
        return IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = cadenceRpm,
            averageCadenceRpm = null,
            totalDistanceMeters = null,
            resistanceLevel = null,
            instantaneousPowerW = null,
            averagePowerW = null,
            totalEnergyKcal = null,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = null,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null,
        )
    }

    private fun bikeData(
        powerW: Int,
        cadenceRpm: Double? = null,
        heartRateBpm: Int? = null,
        distanceMeters: Int? = null,
        totalEnergyKcal: Int? = null,
    ): IndoorBikeData {
        return IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = cadenceRpm,
            averageCadenceRpm = null,
            totalDistanceMeters = distanceMeters,
            resistanceLevel = null,
            instantaneousPowerW = powerW,
            averagePowerW = null,
            totalEnergyKcal = totalEnergyKcal,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = heartRateBpm,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null,
        )
    }
}
