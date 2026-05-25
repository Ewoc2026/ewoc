package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SummaryFitExportPreferenceCoordinatorTest {

    @Test
    fun onPreferenceSelected_updatesStatePersistsPreferenceAndPublishesAutoSaveMessage() {
        val record = PreferenceEffectRecord()
        val statePort = FakeSummaryFitExportPreferenceStatePort(record).apply {
            preference = FitExportPreference.DO_NOT_SAVE
            statusMessage = "stale"
            statusIsError = true
            record.events.clear()
        }
        val coordinator = coordinator(
            statePort = statePort,
            record = record,
        )

        coordinator.onPreferenceSelected(FitExportPreference.AUTO_SAVE)

        assertEquals(FitExportPreference.AUTO_SAVE, statePort.preference)
        assertEquals(listOf(FitExportPreference.AUTO_SAVE), record.savedPreferences)
        assertEquals("Auto-save enabled.", statePort.statusMessage)
        assertFalse(statePort.statusIsError)
        assertEquals(
            listOf(
                "set_preference",
                "save_preference",
                "set_status_message",
                "set_status_is_error",
            ),
            record.events,
        )
    }

    @Test
    fun onPreferenceSelected_mapsEachPreferenceToExpectedAcknowledgementMessage() {
        val cases = listOf(
            FitExportPreference.AUTO_SAVE to "Auto-save enabled.",
            FitExportPreference.ASK_EVERY_TIME to "Ask every time enabled.",
            FitExportPreference.DO_NOT_SAVE to "Do not save enabled.",
        )

        cases.forEach { (preference, expectedMessage) ->
            val record = PreferenceEffectRecord()
            val statePort = FakeSummaryFitExportPreferenceStatePort(record).apply {
                statusIsError = true
                record.events.clear()
            }
            val coordinator = coordinator(
                statePort = statePort,
                record = record,
            )

            coordinator.onPreferenceSelected(preference)

            assertEquals(preference, statePort.preference)
            assertEquals(expectedMessage, statePort.statusMessage)
            assertFalse(
                "Expected non-error acknowledgement for preference=$preference",
                statePort.statusIsError,
            )
        }
    }

    private fun coordinator(
        statePort: FakeSummaryFitExportPreferenceStatePort,
        record: PreferenceEffectRecord,
    ): SummaryFitExportPreferenceCoordinator {
        return SummaryFitExportPreferenceCoordinator(
            statePort = statePort,
            savePreference = { preference ->
                record.events += "save_preference"
                record.savedPreferences += preference
            },
            autoSaveEnabledMessage = { "Auto-save enabled." },
            askEveryTimeEnabledMessage = { "Ask every time enabled." },
            doNotSaveEnabledMessage = { "Do not save enabled." },
        )
    }

    private class FakeSummaryFitExportPreferenceStatePort(
        private val record: PreferenceEffectRecord,
    ) : SummaryFitExportPreferenceStatePort {
        override var preference: FitExportPreference? = null
            set(value) {
                field = value
                record.events += "set_preference"
            }

        override var statusMessage: String? = null
            set(value) {
                field = value
                record.events += "set_status_message"
            }

        override var statusIsError: Boolean = false
            set(value) {
                field = value
                record.events += "set_status_is_error"
            }
    }

    private class PreferenceEffectRecord {
        val events = mutableListOf<String>()
        val savedPreferences = mutableListOf<FitExportPreference>()
    }
}
