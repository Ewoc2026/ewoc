package io.github.ewoc2026.ewoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceSelectionPreScanCoordinatorTest {
    @Test
    fun onBeforeScanRequest_ftmsCancelsProbesWithoutHrReset() {
        val state = FakeStatePort().apply {
            hrConnected = true
            heartRate = 152
        }
        val record = PreScanEffectRecord()
        val coordinator = buildCoordinator(state, record)

        coordinator.onBeforeScanRequest(DeviceSelectionKind.FTMS)

        assertEquals(
            listOf(
                "cancel_pending_resume",
                "cancel_trainer_probe",
                "cancel_hr_probe",
            ),
            record.events,
        )
        assertEquals(true, state.hrConnected)
        assertEquals(152, state.heartRate)
        assertEquals(emptyList<Int?>(), record.sessionHeartRateUpdates)
    }

    @Test
    fun onBeforeScanRequest_hrCancelsProbesBeforeClosingAndResettingHrState() {
        val state = FakeStatePort().apply {
            hrConnected = true
            heartRate = 161
        }
        val record = PreScanEffectRecord()
        val coordinator = buildCoordinator(state, record)

        coordinator.onBeforeScanRequest(DeviceSelectionKind.HEART_RATE)

        assertEquals(
            listOf(
                "cancel_pending_resume",
                "cancel_trainer_probe",
                "cancel_hr_probe",
                "close_hr_client",
                "update_session_hr",
            ),
            record.events,
        )
        assertEquals(false, state.hrConnected)
        assertNull(state.heartRate)
        assertEquals(listOf(null), record.sessionHeartRateUpdates)
    }

    @Test
    fun onBeforeScanStart_cancelsTrainerProbeBeforeHrProbe() {
        val state = FakeStatePort()
        val record = PreScanEffectRecord()
        val coordinator = buildCoordinator(state, record)

        coordinator.onBeforeScanStart()

        assertEquals(
            listOf(
                "cancel_trainer_probe",
                "cancel_hr_probe",
            ),
            record.events,
        )
    }

    private fun buildCoordinator(
        state: FakeStatePort,
        record: PreScanEffectRecord,
    ): DeviceSelectionPreScanCoordinator {
        return DeviceSelectionPreScanCoordinator(
            statePort = state,
            cancelPendingStatusProbeResume = { record.events += "cancel_pending_resume" },
            cancelTrainerStatusProbeScan = { record.events += "cancel_trainer_probe" },
            cancelHrStatusProbeScan = { record.events += "cancel_hr_probe" },
            closeHeartRateClient = { record.events += "close_hr_client" },
            updateSessionHeartRate = { bpm ->
                record.events += "update_session_hr"
                record.sessionHeartRateUpdates += bpm
            },
        )
    }

    private class FakeStatePort : DeviceSelectionPreScanStatePort {
        override var hrConnected: Boolean = false
        override var heartRate: Int? = null
    }

    private class PreScanEffectRecord {
        val events = mutableListOf<String>()
        val sessionHeartRateUpdates = mutableListOf<Int?>()
    }
}
