package com.example.ergometerapp.session

import android.content.ContextWrapper
import com.example.ergometerapp.SessionState
import com.example.ergometerapp.ftms.IndoorBikeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionManagerRetentionTest {

    @Test
    fun timelineRetentionCapsExportAndKeepsNewestSamples() {
        val clock = MutableClock(startMillis = 0L)
        val manager = createManager(clock = clock, timelineRetentionSeconds = 5)

        manager.startSession(ftpWatts = 250)
        for (second in 0..10) {
            clock.currentMillis = second * 1_000L
            manager.updateBikeData(
                bikeData(
                    instantaneousPowerWatts = 100 + second,
                    cadenceRpm = (80 + second).toDouble(),
                    distanceMeters = second,
                )
            )
        }
        manager.stopSession()

        val timeline = manager.exportTimelineSnapshot()
        assertEquals(5, timeline.size)
        assertEquals(6_000L, timeline.first().timestampMillis)
        assertEquals(10_000L, timeline.last().timestampMillis)
        assertEquals(106, timeline.first().powerWatts)
        assertEquals(110, timeline.last().powerWatts)
    }

    @Test
    fun summaryMetricsStayStableWithDifferentTimelineRetentionWindows() {
        val shortRetentionSummary = runSessionAndReturnSummary(timelineRetentionSeconds = 5)
        val longRetentionSummary = runSessionAndReturnSummary(timelineRetentionSeconds = 10_000)

        assertNotNull(shortRetentionSummary.actualTss)
        assertNotNull(longRetentionSummary.actualTss)
        assertEquals(longRetentionSummary.durationSeconds, shortRetentionSummary.durationSeconds)
        assertEquals(longRetentionSummary.avgPower, shortRetentionSummary.avgPower)
        assertEquals(longRetentionSummary.maxPower, shortRetentionSummary.maxPower)
        assertEquals(longRetentionSummary.avgCadence, shortRetentionSummary.avgCadence)
        assertEquals(longRetentionSummary.maxCadence, shortRetentionSummary.maxCadence)
        assertEquals(longRetentionSummary.avgHeartRate, shortRetentionSummary.avgHeartRate)
        assertEquals(longRetentionSummary.maxHeartRate, shortRetentionSummary.maxHeartRate)
        assertEquals(longRetentionSummary.actualTss, shortRetentionSummary.actualTss)
        assertEquals(longRetentionSummary.distanceMeters, shortRetentionSummary.distanceMeters)
        assertEquals(longRetentionSummary.totalEnergyKcal, shortRetentionSummary.totalEnergyKcal)
    }

    @Test
    fun retentionWindowIsClampedToAtLeastOneSample() {
        val clock = MutableClock(startMillis = 0L)
        val manager = createManager(clock = clock, timelineRetentionSeconds = 0)

        manager.startSession(ftpWatts = 250)
        for (second in 0..4) {
            clock.currentMillis = second * 1_000L
            manager.updateBikeData(
                bikeData(
                    instantaneousPowerWatts = 200 + second,
                )
            )
        }
        manager.stopSession()

        val timeline = manager.exportTimelineSnapshot()
        assertEquals(1, timeline.size)
        assertEquals(4_000L, timeline.single().timestampMillis)
        assertEquals(204, timeline.single().powerWatts)
    }

    private fun runSessionAndReturnSummary(timelineRetentionSeconds: Int): SessionSummary {
        val clock = MutableClock(startMillis = 10_000L)
        val manager = createManager(clock = clock, timelineRetentionSeconds = timelineRetentionSeconds)
        manager.startSession(ftpWatts = 250)
        for (second in 0..120) {
            clock.currentMillis = 10_000L + second * 1_000L
            manager.updateBikeData(
                bikeData(
                    instantaneousPowerWatts = 200 + (second % 50),
                    cadenceRpm = (85 + (second % 10)).toDouble(),
                    distanceMeters = second * 5,
                )
            )
            manager.updateHeartRate(130 + (second % 5))
        }
        manager.stopSession()
        return requireNotNull(manager.lastSummary)
    }

    private fun createManager(
        clock: MutableClock,
        timelineRetentionSeconds: Int,
    ): SessionManager {
        return SessionManager(
            context = ContextWrapper(null),
            onStateUpdated = { _: SessionState -> },
            nowMillis = { clock.currentMillis },
            persistSummary = { _, _ -> },
            timelineRetentionSeconds = timelineRetentionSeconds,
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
    }
}
