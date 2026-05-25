package com.example.ergometerapp.baseline

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaselineFitnessTestResultPromotionCoordinatorTest {
    @Test
    fun recordResult_completedPromotionUpdatesStateBeforePersistenceAndRefresh() {
        val record = PromotionEffectRecord()
        val statePort = FakeActiveFtpStatePort(record).apply {
            ftpWatts = 180
            ftpInputText = "180"
            ftpInputError = "old_error"
            record.events.clear()
        }
        val coordinator = buildCoordinator(statePort, record)

        coordinator.recordResult(completedResult())

        assertEquals(195, statePort.ftpWatts)
        assertEquals("195", statePort.ftpInputText)
        assertNull(statePort.ftpInputError)
        assertEquals(listOf(195), record.savedFtpWatts)
        assertEquals(1, record.savedResults.size)
        assertEquals(
            listOf(
                "set_ftp_watts",
                "set_ftp_input_text",
                "set_ftp_input_error",
                "save_ftp_watts",
                "save_latest_result",
                "notify_ftp_promoted",
                "refresh_after_promotion",
            ),
            record.events,
        )
    }

    @Test
    fun recordResult_completedWithOutOfBoundsFtpSavesResultWithoutPromoting() {
        val record = PromotionEffectRecord()
        val statePort = FakeActiveFtpStatePort(record).apply {
            ftpWatts = 180
            ftpInputText = "180"
            record.events.clear()
        }
        val coordinator = buildCoordinator(statePort, record)

        val tooLow = completedResult().copy(ftpEstimateWatts = 10)
        coordinator.recordResult(tooLow)
        assertEquals(180, statePort.ftpWatts)
        assertEquals("180", statePort.ftpInputText)
        assertEquals(listOf("save_latest_result"), record.events)

        record.events.clear()
        record.savedResults.clear()
        val tooHigh = completedResult().copy(ftpEstimateWatts = 1500)
        coordinator.recordResult(tooHigh)
        assertEquals(180, statePort.ftpWatts)
        assertEquals(listOf("save_latest_result"), record.events)
    }

    @Test
    fun recordResult_invalidAttemptOnlyPersistsLatestMetadata() {
        val record = PromotionEffectRecord()
        val statePort = FakeActiveFtpStatePort(record)
        val coordinator = buildCoordinator(statePort, record)
        val invalidResult = completedResult().copy(
            status = BaselineFitnessTestStatus.INVALID,
            ftpEstimateWatts = null,
            peak1mPowerWatts = null,
            confidence = null,
        )

        coordinator.recordResult(invalidResult)

        assertEquals(emptyList<Int>(), record.savedFtpWatts)
        assertEquals(listOf(invalidResult), record.savedResults)
        assertEquals(listOf("save_latest_result"), record.events)
    }

    private fun buildCoordinator(
        statePort: FakeActiveFtpStatePort,
        record: PromotionEffectRecord,
    ): BaselineFitnessTestResultPromotionCoordinator {
        return BaselineFitnessTestResultPromotionCoordinator(
            statePort = statePort,
            saveFtpWatts = { watts ->
                record.events += "save_ftp_watts"
                record.savedFtpWatts += watts
            },
            saveLatestResult = { result ->
                record.events += "save_latest_result"
                record.savedResults += result
            },
            onFtpWattsPromoted = {
                record.events += "notify_ftp_promoted"
            },
            refreshAfterPromotion = {
                record.events += "refresh_after_promotion"
            },
        )
    }

    private fun completedResult(): BaselineFitnessTestResult {
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

    private class FakeActiveFtpStatePort(
        private val record: PromotionEffectRecord,
    ) : BaselineFitnessTestActiveFtpStatePort {
        override var ftpWatts: Int = 0
            set(value) {
                field = value
                record.events += "set_ftp_watts"
            }

        override var ftpInputText: String = ""
            set(value) {
                field = value
                record.events += "set_ftp_input_text"
            }

        override var ftpInputError: String? = null
            set(value) {
                field = value
                record.events += "set_ftp_input_error"
            }
    }

    private class PromotionEffectRecord {
        val events = mutableListOf<String>()
        val savedFtpWatts = mutableListOf<Int>()
        val savedResults = mutableListOf<BaselineFitnessTestResult>()
    }
}
