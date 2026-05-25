package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStartEligibilityCoordinatorTest {
    @Test
    fun onMockTrainerModeChanged_releaseBuildIsNoOp() {
        val record = SessionStartEligibilityEffectRecord()
        val state = FakeStatePort(record).apply {
            mockTrainerModeEnabled = false
            record.events.clear()
        }
        val coordinator = buildCoordinator(
            state = state,
            record = record,
            isDebugBuild = false,
        )

        coordinator.onMockTrainerModeChanged(enabled = true)

        assertFalse(state.mockTrainerModeEnabled)
        assertEquals(emptyList<Boolean>(), record.savedMockTrainerModeValues)
        assertEquals(emptyList<String>(), record.events)
    }

    @Test
    fun onMockTrainerModeChanged_debugBuildPersistsAndClearsPromptAfterStateUpdate() {
        val record = SessionStartEligibilityEffectRecord()
        val state = FakeStatePort(record).apply {
            mockTrainerModeEnabled = false
            record.events.clear()
        }
        val coordinator = buildCoordinator(
            state = state,
            record = record,
            isDebugBuild = true,
        )

        coordinator.onMockTrainerModeChanged(enabled = true)

        assertTrue(state.mockTrainerModeEnabled)
        assertEquals(listOf(true), record.savedMockTrainerModeValues)
        assertEquals(
            listOf(
                "set_mock_trainer_mode",
                "save_mock_trainer_mode",
                "clear_connection_issue_prompt",
            ),
            record.events,
        )
    }

    @Test
    fun canStartSession_followsWorkoutReadinessTrainerSelectionAndMockModeTruthTable() {
        data class Case(
            val workoutReady: Boolean,
            val trainerSelected: Boolean,
            val mockModeActive: Boolean,
            val expected: Boolean,
        )

        val cases = listOf(
            Case(workoutReady = false, trainerSelected = false, mockModeActive = false, expected = false),
            Case(workoutReady = false, trainerSelected = false, mockModeActive = true, expected = false),
            Case(workoutReady = false, trainerSelected = true, mockModeActive = false, expected = false),
            Case(workoutReady = false, trainerSelected = true, mockModeActive = true, expected = false),
            Case(workoutReady = true, trainerSelected = false, mockModeActive = false, expected = false),
            Case(workoutReady = true, trainerSelected = false, mockModeActive = true, expected = true),
            Case(workoutReady = true, trainerSelected = true, mockModeActive = false, expected = true),
            Case(workoutReady = true, trainerSelected = true, mockModeActive = true, expected = true),
        )

        cases.forEach { case ->
            val record = SessionStartEligibilityEffectRecord()
            val state = FakeStatePort(record).apply {
                workoutReady = case.workoutReady
                mockTrainerModeEnabled = case.mockModeActive
                selectedFtmsDeviceMac = if (case.trainerSelected) {
                    "aa:bb:cc:dd:ee:ff"
                } else {
                    null
                }
                record.events.clear()
            }
            val coordinator = buildCoordinator(
                state = state,
                record = record,
                isDebugBuild = true,
            )

            assertEquals(
                "Mismatch for case=$case",
                case.expected,
                coordinator.canStartSession(),
            )
        }
    }

    // region Telemetry-only (3C) truth table extension

    @Test
    fun canStartSession_telemetryOnlyBypassesWorkoutReadinessRequirement() {
        data class TelemetryCase(
            val setupMode: SessionSetupMode,
            val workoutReady: Boolean,
            val trainerSelected: Boolean,
            val mockModeActive: Boolean,
            val expected: Boolean,
        )

        val cases = listOf(
            // telemetry-only + trainer → eligible even without workout
            TelemetryCase(setupMode = SessionSetupMode.TELEMETRY_ONLY, workoutReady = false, trainerSelected = true, mockModeActive = false, expected = true),
            TelemetryCase(setupMode = SessionSetupMode.TELEMETRY_ONLY, workoutReady = false, trainerSelected = false, mockModeActive = true, expected = true),
            // telemetry-only without trainer → still ineligible
            TelemetryCase(setupMode = SessionSetupMode.TELEMETRY_ONLY, workoutReady = false, trainerSelected = false, mockModeActive = false, expected = false),
            // telemetry-only + workout → eligible (superset)
            TelemetryCase(setupMode = SessionSetupMode.TELEMETRY_ONLY, workoutReady = true, trainerSelected = true, mockModeActive = false, expected = true),
            // non-telemetry-only without workout → still ineligible (existing behavior preserved)
            TelemetryCase(setupMode = SessionSetupMode.FILE, workoutReady = false, trainerSelected = true, mockModeActive = false, expected = false),
        )

        cases.forEach { case ->
            val record = SessionStartEligibilityEffectRecord()
            val state = FakeStatePort(record).apply {
                selectedSessionSetupMode = case.setupMode
                workoutReady = case.workoutReady
                mockTrainerModeEnabled = case.mockModeActive
                selectedFtmsDeviceMac = if (case.trainerSelected) "aa:bb:cc:dd:ee:ff" else null
                record.events.clear()
            }
            val coordinator = buildCoordinator(
                state = state,
                record = record,
                isDebugBuild = true,
            )

            assertEquals(
                "Mismatch for telemetry-only case=$case",
                case.expected,
                coordinator.canStartSession(),
            )
        }
    }

    // endregion

    @Test
    fun selectedDeviceHelpersRequireCanonicalMacFormat() {
        val record = SessionStartEligibilityEffectRecord()
        val state = FakeStatePort(record).apply {
            selectedFtmsDeviceMac = "invalid"
            selectedHrDeviceMac = "aa:bb:cc:dd:ee:ff"
        }
        val coordinator = buildCoordinator(
            state = state,
            record = record,
            isDebugBuild = true,
        )

        assertFalse(coordinator.hasSelectedFtmsDevice())
        assertTrue(coordinator.hasSelectedHrDevice())
        assertNull(coordinator.currentFtmsDeviceMac())
        assertEquals("AA:BB:CC:DD:EE:FF", coordinator.currentHrDeviceMac())
    }

    private fun buildCoordinator(
        state: FakeStatePort,
        record: SessionStartEligibilityEffectRecord,
        isDebugBuild: Boolean,
    ): SessionStartEligibilityCoordinator {
        return SessionStartEligibilityCoordinator(
            statePort = state,
            isDebugBuild = { isDebugBuild },
            normalizeBluetoothMac = BluetoothMacAddress::normalizeOrNull,
            saveMockTrainerModeEnabled = { enabled ->
                record.events += "save_mock_trainer_mode"
                record.savedMockTrainerModeValues += enabled
            },
            clearConnectionIssuePrompt = {
                record.events += "clear_connection_issue_prompt"
            },
        )
    }

    private class FakeStatePort(
        private val record: SessionStartEligibilityEffectRecord,
    ) : SessionStartEligibilityStatePort {
        override var workoutReady: Boolean = false
        override var selectedSessionSetupMode: SessionSetupMode = SessionSetupMode.FILE

        override var mockTrainerModeEnabled: Boolean = false
            set(value) {
                field = value
                record.events += "set_mock_trainer_mode"
            }

        override var selectedFtmsDeviceMac: String? = null

        override var selectedHrDeviceMac: String? = null
    }

    private class SessionStartEligibilityEffectRecord {
        val events = mutableListOf<String>()
        val savedMockTrainerModeValues = mutableListOf<Boolean>()
    }
}
