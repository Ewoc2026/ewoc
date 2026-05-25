package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileSettingsCoordinatorTest {
    @Test
    fun onFtpInputChanged_invalidInputSetsErrorWithoutPersistenceOrRefresh() {
        val record = ProfileSettingsEffectRecord()
        val state = FakeStatePort(record).apply {
            ftpWatts = 250
            ftpInputError = null
            record.events.clear()
        }
        val coordinator = buildCoordinator(state, record)

        coordinator.onFtpInputChanged(rawInput = "0abc")

        assertEquals("0", state.ftpInputText)
        assertEquals("ftp_invalid", state.ftpInputError)
        assertEquals(250, state.ftpWatts)
        assertEquals(emptyList<Int>(), record.savedFtpWatts)
        assertEquals(
            listOf(
                "set_ftp_input_text",
                "set_ftp_input_error",
            ),
            record.events,
        )
    }

    @Test
    fun onFtpInputChanged_validInputPersistsAndSessionSyncsBeforeRefresh() {
        val record = ProfileSettingsEffectRecord()
        val state = FakeStatePort(record).apply {
            ftpInputError = "old_error"
            record.events.clear()
        }
        val coordinator = buildCoordinator(state, record)

        coordinator.onFtpInputChanged(rawInput = "ab12345")

        assertEquals("1234", state.ftpInputText)
        assertNull(state.ftpInputError)
        assertEquals(1234, state.ftpWatts)
        assertEquals(listOf(1234), record.savedFtpWatts)
        assertEquals(
            listOf(
                "set_ftp_input_text",
                "set_ftp_input_error",
                "set_ftp_watts",
                "save_ftp_watts",
                "sync_session_ftp",
                "refresh_recommendations",
            ),
            record.events,
        )
    }

    @Test
    fun onHrProfileAgeInputChanged_invalidInputSetsErrorWithoutPersistenceOrRefresh() {
        val record = ProfileSettingsEffectRecord()
        val state = FakeStatePort(record).apply {
            hrProfileAge = 34
            hrProfileAgeError = null
            record.events.clear()
        }
        val coordinator = buildCoordinator(state, record)

        coordinator.onHrProfileAgeInputChanged(rawInput = "12")

        assertEquals("12", state.hrProfileAgeInput)
        assertEquals("age_invalid", state.hrProfileAgeError)
        assertEquals(34, state.hrProfileAge)
        assertEquals(emptyList<Int>(), record.savedHrProfileAges)
        assertEquals(
            listOf(
                "set_hr_age_input",
                "set_hr_age_error",
            ),
            record.events,
        )
    }

    @Test
    fun onHrProfileAgeInputChanged_validInputPersistsThenRefreshes() {
        val record = ProfileSettingsEffectRecord()
        val state = FakeStatePort(record).apply {
            hrProfileAgeError = "old_error"
            record.events.clear()
        }
        val coordinator = buildCoordinator(state, record)

        coordinator.onHrProfileAgeInputChanged(rawInput = "099")

        assertEquals("099", state.hrProfileAgeInput)
        assertNull(state.hrProfileAgeError)
        assertEquals(99, state.hrProfileAge)
        assertEquals(listOf(99), record.savedHrProfileAges)
        assertEquals(
            listOf(
                "set_hr_age_input",
                "set_hr_age_error",
                "set_hr_age",
                "save_hr_age",
                "refresh_recommendations",
            ),
            record.events,
        )
    }

    @Test
    fun onHrProfileSexSelected_updatesStateBeforePersistAndRefresh() {
        val record = ProfileSettingsEffectRecord()
        val state = FakeStatePort(record)
        val coordinator = buildCoordinator(state, record)

        coordinator.onHrProfileSexSelected(HrProfileSex.FEMALE)

        assertEquals(HrProfileSex.FEMALE, state.hrProfileSex)
        assertEquals(listOf(HrProfileSex.FEMALE), record.savedHrProfileSexes)
        assertEquals(
            listOf(
                "set_hr_sex",
                "save_hr_sex",
                "refresh_recommendations",
            ),
            record.events,
        )
    }

    private fun buildCoordinator(
        state: FakeStatePort,
        record: ProfileSettingsEffectRecord,
    ): ProfileSettingsCoordinator {
        return ProfileSettingsCoordinator(
            statePort = state,
            ftpInputMaxLength = 4,
            hrProfileAgeInputMaxLength = 3,
            ftpInvalidInputErrorMessage = { "ftp_invalid" },
            hrProfileAgeInvalidInputErrorMessage = { "age_invalid" },
            saveFtpWatts = { watts ->
                record.events += "save_ftp_watts"
                record.savedFtpWatts += watts
            },
            onFtpWattsSaved = {
                record.events += "sync_session_ftp"
            },
            saveHrProfileAge = { age ->
                record.events += "save_hr_age"
                record.savedHrProfileAges += age
            },
            saveHrProfileSex = { sex ->
                record.events += "save_hr_sex"
                record.savedHrProfileSexes += sex
            },
            refreshAiAssistantRecommendations = {
                record.events += "refresh_recommendations"
            },
        )
    }

    private class FakeStatePort(
        private val record: ProfileSettingsEffectRecord,
    ) : ProfileSettingsStatePort {
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
        override var ftpWatts: Int = 0
            set(value) {
                field = value
                record.events += "set_ftp_watts"
            }
        override var hrProfileAgeInput: String = ""
            set(value) {
                field = value
                record.events += "set_hr_age_input"
            }
        override var hrProfileAgeError: String? = null
            set(value) {
                field = value
                record.events += "set_hr_age_error"
            }
        override var hrProfileAge: Int? = null
            set(value) {
                field = value
                record.events += "set_hr_age"
            }
        override var hrProfileSex: HrProfileSex? = null
            set(value) {
                field = value
                record.events += "set_hr_sex"
            }
    }

    private class ProfileSettingsEffectRecord {
        val events = mutableListOf<String>()
        val savedFtpWatts = mutableListOf<Int>()
        val savedHrProfileAges = mutableListOf<Int>()
        val savedHrProfileSexes = mutableListOf<HrProfileSex>()
    }
}
