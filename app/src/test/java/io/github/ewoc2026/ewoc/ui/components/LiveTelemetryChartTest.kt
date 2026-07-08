package io.github.ewoc2026.ewoc.ui.components

import io.github.ewoc2026.ewoc.session.SessionSample
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTelemetryChartTest {

    @Test
    fun resolveVisibleTelemetryLanesHidesStaleHeartRateLaneOutsideWindow() {
        val samples = listOf(
            SessionSample(
                timestampMillis = 1_000L,
                powerWatts = 180,
                cadenceRpm = 88,
                heartRateBpm = 145,
                distanceMeters = null,
                totalEnergyKcal = null,
            ),
            SessionSample(
                timestampMillis = 10_000L,
                powerWatts = 190,
                cadenceRpm = 90,
                heartRateBpm = null,
                distanceMeters = null,
                totalEnergyKcal = null,
            ),
        )

        val visible = resolveVisibleTelemetryLanes(
            samples = samples,
            oldestVisible = 9_000L,
        )

        assertTrue(visible.hasPower)
        assertTrue(visible.hasCadence)
        assertFalse(visible.hasHeartRate)
    }

    @Test
    fun resolveVisibleTelemetryLanesKeepsLaneWhenSignalExistsInVisibleWindow() {
        val samples = listOf(
            SessionSample(
                timestampMillis = 10_000L,
                powerWatts = 190,
                cadenceRpm = 90,
                heartRateBpm = 152,
                distanceMeters = null,
                totalEnergyKcal = null,
            ),
        )

        val visible = resolveVisibleTelemetryLanes(
            samples = samples,
            oldestVisible = 9_000L,
        )

        assertTrue(visible.hasPower)
        assertTrue(visible.hasCadence)
        assertTrue(visible.hasHeartRate)
    }
}
