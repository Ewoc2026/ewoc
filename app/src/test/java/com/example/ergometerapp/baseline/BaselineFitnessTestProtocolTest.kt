package com.example.ergometerapp.baseline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaselineFitnessTestProtocolTest {
    @Test
    fun computeStartWattsFallsBackToDefaultWithoutPriorFtp() {
        assertEquals(100, BaselineFitnessTestProtocol.computeStartWatts(priorFtpWatts = null))
    }

    @Test
    fun computeStartWattsAnchorsLowPriorFtpAtMinimumBeforeRounding() {
        assertEquals(75, BaselineFitnessTestProtocol.computeStartWatts(priorFtpWatts = 120))
    }

    @Test
    fun computeStartWattsRoundsPriorFtpAnchorToNearestFive() {
        assertEquals(115, BaselineFitnessTestProtocol.computeStartWatts(priorFtpWatts = 251))
    }

    @Test
    fun targetWattsForRampMinuteAddsTwentyWattsPerMinute() {
        assertEquals(100, BaselineFitnessTestProtocol.targetWattsForRampMinute(100, 0))
        assertEquals(160, BaselineFitnessTestProtocol.targetWattsForRampMinute(100, 3))
    }

    @Test
    fun zoneCalculatorBuildsOpenEndedFinalZone() {
        val zones = BaselineFitnessZoneCalculator.calculate(ftpWatts = 200)

        assertEquals(7, zones.size)
        assertEquals("Z1", zones.first().code)
        assertEquals(110, zones.first().maxWattsInclusive)
        assertEquals("Z7", zones.last().code)
        assertNull(zones.last().maxWattsInclusive)
        assertEquals(302, zones.last().minWatts)
    }
}
