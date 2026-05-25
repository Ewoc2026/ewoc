package com.example.ergometerapp.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ActualTssAccumulatorTest {

    @Test
    fun constantPowerAtFtpForOneHourProducesHundredTss() {
        val accumulator = ActualTssAccumulator(ftpWatts = 200)

        for (second in 0L..3600L) {
            accumulator.recordPower(
                powerWatts = 200,
                timestampMillis = second * 1000L,
            )
        }

        val tss = accumulator.calculateTss(
            durationSeconds = 3600,
            stopTimestampMillis = 3600_000L,
        )

        assertNotNull(tss)
        assertEquals(100.0, tss!!, 0.0)
    }

    @Test
    fun halfFtpForOneHourProducesTwentyFiveTss() {
        val accumulator = ActualTssAccumulator(ftpWatts = 240)

        for (second in 0L..3600L) {
            accumulator.recordPower(
                powerWatts = 120,
                timestampMillis = second * 1000L,
            )
        }

        val tss = accumulator.calculateTss(
            durationSeconds = 3600,
            stopTimestampMillis = 3600_000L,
        )

        assertNotNull(tss)
        assertEquals(25.0, tss!!, 0.0)
    }

    @Test
    fun sparseSamplesAreIntegratedAsPiecewiseConstantPower() {
        val accumulator = ActualTssAccumulator(
            ftpWatts = 250,
            maxHoldSeconds = 5,
        )

        for (second in 0L..3600L step 5L) {
            accumulator.recordPower(
                powerWatts = 250,
                timestampMillis = second * 1000L,
            )
        }

        val tss = accumulator.calculateTss(
            durationSeconds = 3600,
            stopTimestampMillis = 3600_000L,
        )

        assertNotNull(tss)
        assertEquals(100.0, tss!!, 0.0)
    }

    @Test
    fun longGapsPastHoldLimitAreCountedAsZeroPowerConservatively() {
        val accumulator = ActualTssAccumulator(
            ftpWatts = 250,
            rollingWindowSeconds = 1,
            maxHoldSeconds = 3,
        )

        for (second in 0L..3600L step 5L) {
            accumulator.recordPower(
                powerWatts = 250,
                timestampMillis = second * 1000L,
            )
        }

        val tss = accumulator.calculateTss(
            durationSeconds = 3600,
            stopTimestampMillis = 3600_000L,
        )

        assertNotNull(tss)
        assertEquals(77.5, tss!!, 0.0)
    }

    @Test
    fun returnsNullWhenNoPowerSamplesWereRecorded() {
        val accumulator = ActualTssAccumulator(ftpWatts = 220)

        val tss = accumulator.calculateTss(
            durationSeconds = 1200,
            stopTimestampMillis = 1_200_000L,
        )

        assertNull(tss)
    }
}
