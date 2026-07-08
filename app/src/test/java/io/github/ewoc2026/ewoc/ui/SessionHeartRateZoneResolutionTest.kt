package io.github.ewoc2026.ewoc.ui

import io.github.ewoc2026.ewoc.HrProfileSex
import io.github.ewoc2026.ewoc.estimatedMaxHeartRate
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionHeartRateZoneResolutionTest {

    @Test
    fun resolvesZoneOneWhenHeartRateIsBelowFirstZoneLowerBound() {
        val zone = resolveHeartRateZone(
            heartRateBpm = 95,
            maxHeartRate = 200,
        )

        assertEquals(1, zone)
    }

    @Test
    fun resolvesZoneFiveWhenHeartRateIsAboveLastZoneUpperBound() {
        val zone = resolveHeartRateZone(
            heartRateBpm = 205,
            maxHeartRate = 200,
        )

        assertEquals(5, zone)
    }

    @Test
    fun resolvesExpectedZoneInsideCalculatedRanges() {
        val zone = resolveHeartRateZone(
            heartRateBpm = 150,
            maxHeartRate = 200,
        )

        assertEquals(3, zone)
    }

    @Test
    fun clampsUnrealisticMaxHeartRateInputBeforeResolvingZone() {
        val zone = resolveHeartRateZone(
            heartRateBpm = 55,
            maxHeartRate = 60,
        )

        assertEquals(1, zone)
    }

    @Test
    fun estimatesMaxHeartRateWithNeutralFormulaWhenSexIsMissing() {
        val estimated = estimatedMaxHeartRate(
            age = 40,
            sex = null,
        )

        assertEquals(175, estimated)
    }

    @Test
    fun keepsLegacyMaleAndFemaleFormulasForConfiguredProfiles() {
        val maleEstimated = estimatedMaxHeartRate(
            age = 40,
            sex = HrProfileSex.MALE,
        )
        val femaleEstimated = estimatedMaxHeartRate(
            age = 40,
            sex = HrProfileSex.FEMALE,
        )

        assertEquals(180, maleEstimated)
        assertEquals(171, femaleEstimated)
    }
}
