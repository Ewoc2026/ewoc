package com.example.ergometerapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostWorkoutContinuationHandoffTest {
    @Test
    fun telemetryOnlyContinuationBecomesReadyEvenWhenRunnerStillLooksDone() {
        assertTrue(
            isPostWorkoutContinuationHandoffReady(
                screen = AppScreen.SESSION,
                selectedSessionSetupMode = SessionSetupMode.TELEMETRY_ONLY,
                ftmsReady = true,
                ftmsControlGranted = true,
                runnerDone = true,
            ),
        )
    }

    @Test
    fun telemetryOnlyContinuationWaitsUntilFtmsIsReady() {
        assertFalse(
            isPostWorkoutContinuationHandoffReady(
                screen = AppScreen.SESSION,
                selectedSessionSetupMode = SessionSetupMode.TELEMETRY_ONLY,
                ftmsReady = false,
                ftmsControlGranted = false,
                runnerDone = true,
            ),
        )
    }
}
