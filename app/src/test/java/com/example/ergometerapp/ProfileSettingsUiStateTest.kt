package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileSettingsUiStateTest {

    @Test
    fun statePortAndRestoreStoredSettingsShareProfileBackingState() {
        val uiState = ProfileSettingsUiState(defaultFtpWatts = 210)

        uiState.restoreStoredSettings(
            ftpWatts = 245,
            hrProfileAge = 38,
            hrProfileSex = HrProfileSex.FEMALE,
        )
        uiState.statePort.ftpInputText = "252"
        uiState.statePort.ftpInputError = "ftp-error"
        uiState.statePort.ftpWatts = 252
        uiState.statePort.hrProfileAgeInput = "39"
        uiState.statePort.hrProfileAgeError = "age-error"
        uiState.statePort.hrProfileAge = 39
        uiState.statePort.hrProfileSex = HrProfileSex.MALE

        assertEquals(252, uiState.ftpWattsState.intValue)
        assertEquals("252", uiState.ftpInputTextState.value)
        assertEquals("ftp-error", uiState.ftpInputErrorState.value)
        assertEquals(39, uiState.hrProfileAgeState.value)
        assertEquals("39", uiState.hrProfileAgeInputState.value)
        assertEquals("age-error", uiState.hrProfileAgeErrorState.value)
        assertEquals(HrProfileSex.MALE, uiState.hrProfileSexState.value)
    }

    @Test
    fun restoreStoredSettingsClearsTransientErrors() {
        val uiState = ProfileSettingsUiState(defaultFtpWatts = 210)

        uiState.statePort.ftpInputError = "stale-ftp"
        uiState.statePort.hrProfileAgeError = "stale-age"

        uiState.restoreStoredSettings(
            ftpWatts = 260,
            hrProfileAge = null,
            hrProfileSex = null,
        )

        assertEquals(260, uiState.ftpWattsState.intValue)
        assertEquals("260", uiState.ftpInputTextState.value)
        assertNull(uiState.ftpInputErrorState.value)
        assertEquals("", uiState.hrProfileAgeInputState.value)
        assertNull(uiState.hrProfileAgeState.value)
        assertNull(uiState.hrProfileAgeErrorState.value)
        assertNull(uiState.hrProfileSexState.value)
    }
}
