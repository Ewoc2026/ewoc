package io.github.ewoc2026.ewoc.session

import android.content.ContextWrapper
import io.github.ewoc2026.ewoc.SessionState
import io.github.ewoc2026.ewoc.ftms.IndoorBikeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class SessionManagerEdgeCaseTest {

    @Test
    fun shortSessionWithNoDataProducesNullTelemetryMetrics() {
        val clock = MutableClock(startMillis = 1_000L)
        val manager = createManager(clock)

        manager.startSession(ftpWatts = 250)
        clock.advanceBy(500L)
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(0, summary!!.durationSeconds)
        assertNull(summary.actualTss)
        assertNull(summary.avgPower)
        assertNull(summary.maxPower)
        assertNull(summary.avgCadence)
        assertNull(summary.maxCadence)
        assertNull(summary.avgHeartRate)
        assertNull(summary.maxHeartRate)
    }

    @Test
    fun noTelemetryForLongerSessionKeepsActualTssNull() {
        val clock = MutableClock(startMillis = 10_000L)
        val manager = createManager(clock)

        manager.startSession(ftpWatts = 250)
        clock.advanceBy(10_000L)
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(10, summary!!.durationSeconds)
        assertNull(summary.actualTss)
        assertNull(summary.avgPower)
    }

    @Test
    fun sparseTelemetryGapsReduceActualTssComparedToDenseSampling() {
        val denseSummary = runConstantPowerSession(sampleIntervalSec = 1, durationSec = 120)
        val sparseSummary = runConstantPowerSession(sampleIntervalSec = 5, durationSec = 120)
        val denseTss = requireNotNull(denseSummary.actualTss)
        val sparseTss = requireNotNull(sparseSummary.actualTss)

        assertTrue(sparseTss < denseTss)
        assertTrue(sparseTss > 0.0)
        assertEquals(250, denseSummary.avgPower)
        assertEquals(250, sparseSummary.avgPower)
    }

    @Test
    fun stopSessionQueuesPersistenceWithoutBlockingSummaryPublication() {
        val clock = MutableClock(startMillis = 1_000L)
        val queuedTasks = mutableListOf<Runnable>()
        val persistedSummaries = mutableListOf<SessionSummary>()
        val manager = SessionManager(
            context = ContextWrapper(null),
            onStateUpdated = { _: SessionState -> },
            nowMillis = { clock.currentMillis },
            persistSummary = { _, summary -> persistedSummaries += summary },
            summaryPersistenceExecutor = Executor { task -> queuedTasks += task },
        )

        manager.startSession(ftpWatts = 250)
        clock.advanceBy(5_000L)
        manager.stopSession()

        val publishedSummary = requireNotNull(manager.lastSummary)
        assertEquals(5, publishedSummary.durationSeconds)
        assertTrue(persistedSummaries.isEmpty())
        assertEquals(1, queuedTasks.size)

        queuedTasks.single().run()
        assertEquals(listOf(publishedSummary), persistedSummaries)
    }

    @Test
    fun pausedIntervalsDoNotInflateSummaryDuration() {
        val clock = MutableClock(startMillis = 1_000L)
        val manager = createManager(clock)

        manager.startSession(ftpWatts = 250)
        clock.advanceBy(5_000L)
        manager.pauseSessionActivity()
        clock.advanceBy(10_000L)
        manager.resumeSessionActivity()
        clock.advanceBy(5_000L)
        manager.stopSession()

        val summary = requireNotNull(manager.lastSummary)
        assertEquals(10, summary.durationSeconds)
        assertEquals(20, summary.elapsedDurationSeconds)
    }

    @Test
    fun pausedIntervalsDoNotRecordTelemetryIntoSummaryAverages() {
        val clock = MutableClock(startMillis = 1_000L)
        val manager = createManager(clock)

        manager.startSession(ftpWatts = 250)
        manager.updateBikeData(bikeData(instantaneousPowerWatts = 200))
        clock.advanceBy(1_000L)
        manager.pauseSessionActivity()
        manager.updateBikeData(bikeData(instantaneousPowerWatts = 400))
        clock.advanceBy(5_000L)
        manager.resumeSessionActivity()
        manager.updateBikeData(bikeData(instantaneousPowerWatts = 100))
        clock.advanceBy(1_000L)
        manager.stopSession()

        val summary = requireNotNull(manager.lastSummary)
        assertEquals(150, summary.avgPower)
        assertEquals(200, summary.maxPower)
        assertEquals(2, summary.durationSeconds)
        assertEquals(7, summary.elapsedDurationSeconds)
    }

    @Test
    fun telemetryAveragesAreTimeWeightedInsteadOfSampleCountWeighted() {
        val clock = MutableClock(startMillis = 1_000L)
        val manager = createManager(clock)

        manager.startSession(ftpWatts = 250)
        manager.updateHeartRate(120)
        manager.updateBikeData(
            bikeData(
                instantaneousPowerWatts = 100,
                cadenceRpm = 80.0,
            ),
        )
        clock.advanceBy(9_000L)
        manager.updateBikeData(
            bikeData(
                instantaneousPowerWatts = 300,
                cadenceRpm = 100.0,
            ),
        )
        manager.updateHeartRate(180)
        clock.advanceBy(1_000L)
        manager.stopSession()

        val summary = requireNotNull(manager.lastSummary)
        assertEquals(120, summary.avgPower)
        assertEquals(82, summary.avgCadence)
        assertEquals(126, summary.avgHeartRate)
    }

    @Test
    fun timelineCapture_isLimitedToOneSamplePerSecond() {
        val clock = MutableClock(startMillis = 1_000L)
        val manager = createManager(clock)

        manager.startSession(ftpWatts = 250)
        clock.currentMillis = 1_100L
        manager.updateBikeData(
            bikeData(
                instantaneousPowerWatts = 100,
                cadenceRpm = 80.0,
                distanceMeters = 10,
            ),
        )
        clock.currentMillis = 1_500L
        manager.updateHeartRate(130)
        clock.currentMillis = 1_900L
        manager.updateBikeData(
            bikeData(
                instantaneousPowerWatts = 120,
                cadenceRpm = 82.0,
                distanceMeters = 12,
            ),
        )
        clock.currentMillis = 2_100L
        manager.updateHeartRate(131)
        clock.currentMillis = 2_800L
        manager.updateBikeData(
            bikeData(
                instantaneousPowerWatts = 130,
                cadenceRpm = 83.0,
                distanceMeters = 15,
            ),
        )
        clock.currentMillis = 3_000L
        manager.stopSession()

        val timeline = manager.exportTimelineSnapshot()
        assertEquals(3, timeline.size)
        assertEquals(1_100L, timeline[0].timestampMillis)
        assertEquals(100, timeline[0].powerWatts)
        assertEquals(80, timeline[0].cadenceRpm)
        assertNull(timeline[0].heartRateBpm)
        assertEquals(10, timeline[0].distanceMeters)

        assertEquals(2_100L, timeline[1].timestampMillis)
        assertEquals(120, timeline[1].powerWatts)
        assertEquals(82, timeline[1].cadenceRpm)
        assertEquals(131, timeline[1].heartRateBpm)
        assertEquals(12, timeline[1].distanceMeters)

        assertEquals(3_000L, timeline[2].timestampMillis)
        assertEquals(130, timeline[2].powerWatts)
        assertEquals(83, timeline[2].cadenceRpm)
        assertEquals(131, timeline[2].heartRateBpm)
        assertEquals(15, timeline[2].distanceMeters)
    }

    private fun runConstantPowerSession(sampleIntervalSec: Int, durationSec: Int): SessionSummary {
        val clock = MutableClock(startMillis = 100_000L)
        val manager = createManager(clock)
        manager.startSession(ftpWatts = 250)

        var second = 0
        while (second <= durationSec) {
            clock.currentMillis = 100_000L + second * 1000L
            manager.updateBikeData(
                bikeData(
                    instantaneousPowerWatts = 250,
                )
            )
            second += sampleIntervalSec
        }

        clock.currentMillis = 100_000L + durationSec * 1000L
        manager.stopSession()
        return requireNotNull(manager.lastSummary)
    }

    private fun createManager(clock: MutableClock): SessionManager {
        return SessionManager(
            context = ContextWrapper(null),
            onStateUpdated = { _: SessionState -> },
            nowMillis = { clock.currentMillis },
            persistSummary = { _, _ -> },
        )
    }

    private fun bikeData(
        instantaneousPowerWatts: Int,
        cadenceRpm: Double? = null,
        distanceMeters: Int? = null,
    ): IndoorBikeData {
        return IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = cadenceRpm,
            averageCadenceRpm = null,
            totalDistanceMeters = distanceMeters,
            resistanceLevel = null,
            instantaneousPowerW = instantaneousPowerWatts,
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

    private class MutableClock(
        startMillis: Long,
    ) {
        var currentMillis: Long = startMillis

        fun advanceBy(deltaMillis: Long) {
            currentMillis += deltaMillis
        }
    }
}
