package com.example.ergometerapp.baseline

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineFitnessTestSettingsStorageTest {
    @Test
    fun encodeLatestResultIncludesCompletedFieldsAndSensorProfile() {
        val payload = BaselineFitnessTestSettingsStorage.encodeLatestResult(sampleResult())

        assertTrue(payload.contains("\"testVersion\":\"baseline_fitness_test_v1\""))
        assertTrue(payload.contains("\"status\":\"completed\""))
        assertTrue(payload.contains("\"controlMode\":\"ERG\""))
        assertTrue(payload.contains("\"ftpEstimateWatts\":195"))
        assertTrue(payload.contains("\"sensorProfile\":{\"power\":true,\"heartRate\":true,\"cadence\":true}"))
    }

    @Test
    fun decodeLatestResultRoundTripsCompletedSummary() {
        val restored = BaselineFitnessTestSettingsStorage.decodeLatestResult(
            BaselineFitnessTestSettingsStorage.encodeLatestResult(sampleResult()),
        )

        assertNotNull(restored)
        assertEquals(sampleResult(), restored)
    }

    @Test
    fun decodeLatestResultReturnsNullForMalformedPayload() {
        val restored = BaselineFitnessTestSettingsStorage.decodeLatestResult("{bad-json")

        assertNull(restored)
    }

    @Test
    fun decodeLatestResultHandlesNullOptionalFieldsForCancelledAttempt() {
        val restored = BaselineFitnessTestSettingsStorage.decodeLatestResult(
            """
                {
                  "testVersion":"baseline_fitness_test_v1",
                  "status":"cancelled",
                  "stopReason":"control_lost_mid_test",
                  "controlMode":"ERG",
                  "startedAt":"2026-03-15T10:00:00Z",
                  "completedAt":"2026-03-15T10:07:30Z",
                  "startWatts":100,
                  "validRampMinutes":2,
                  "lastFullStepWatts":null,
                  "ftpEstimateWatts":null,
                  "peak1mPowerWatts":null,
                  "thresholdHrEstimateBpm":null,
                  "confidence":null,
                  "maxPowerGapSec":0.0,
                  "hrCoverageRatio":0.4,
                  "sensorProfile":{"power":true,"heartRate":false,"cadence":true}
                }
            """.trimIndent(),
        )

        assertNotNull(restored)
        assertEquals(BaselineFitnessTestStatus.CANCELLED, restored?.status)
        assertNull(restored?.ftpEstimateWatts)
        assertNull(restored?.confidence)
        assertEquals(false, restored?.sensorProfile?.heartRate)
    }

    private fun sampleResult(): BaselineFitnessTestResult {
        return BaselineFitnessTestResult(
            testVersion = BaselineFitnessTestProtocol.TEST_VERSION,
            status = BaselineFitnessTestStatus.COMPLETED,
            stopReason = BaselineFitnessTestStopReason.MANUAL_STOP,
            controlMode = BaselineFitnessTestControlMode.ERG,
            startedAt = Instant.parse("2026-03-15T10:00:00Z"),
            completedAt = Instant.parse("2026-03-15T10:16:24Z"),
            startWatts = 100,
            validRampMinutes = 9,
            lastFullStepWatts = 260,
            ftpEstimateWatts = 195,
            peak1mPowerWatts = 260,
            thresholdHrEstimateBpm = 171,
            confidence = BaselineFitnessTestConfidence.MEDIUM,
            maxPowerGapSec = 1.3,
            hrCoverageRatio = 0.91,
            sensorProfile = BaselineFitnessTestSensorProfile(
                power = true,
                heartRate = true,
                cadence = true,
            ),
        )
    }
}
