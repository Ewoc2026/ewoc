package io.github.ewoc2026.ewoc

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Owns menu-facing FTP and HR profile state used by profile settings workflows.
 *
 * Keeping the persisted values plus their editable input/error mirrors together prevents
 * `MainViewModel` from spreading one profile-setting workflow across unrelated backing vars.
 * The state port intentionally reuses the same owner so coordinator validation and restore
 * paths cannot drift onto separate fields.
 */
internal class ProfileSettingsUiState(
    defaultFtpWatts: Int,
) {
    val ftpWattsState = mutableIntStateOf(defaultFtpWatts)
    val ftpInputTextState = mutableStateOf(defaultFtpWatts.toString())
    val ftpInputErrorState = mutableStateOf<String?>(null)
    val hrProfileAgeState = mutableStateOf<Int?>(null)
    val hrProfileAgeInputState = mutableStateOf("")
    val hrProfileAgeErrorState = mutableStateOf<String?>(null)
    val hrProfileSexState = mutableStateOf<HrProfileSex?>(null)

    val statePort = object : ProfileSettingsStatePort {
        override var ftpInputText: String
            get() = ftpInputTextState.value
            set(value) {
                ftpInputTextState.value = value
            }

        override var ftpInputError: String?
            get() = ftpInputErrorState.value
            set(value) {
                ftpInputErrorState.value = value
            }

        override var ftpWatts: Int
            get() = ftpWattsState.intValue
            set(value) {
                ftpWattsState.intValue = value
            }

        override var hrProfileAgeInput: String
            get() = hrProfileAgeInputState.value
            set(value) {
                hrProfileAgeInputState.value = value
            }

        override var hrProfileAgeError: String?
            get() = hrProfileAgeErrorState.value
            set(value) {
                hrProfileAgeErrorState.value = value
            }

        override var hrProfileAge: Int?
            get() = hrProfileAgeState.value
            set(value) {
                hrProfileAgeState.value = value
            }

        override var hrProfileSex: HrProfileSex?
            get() = hrProfileSexState.value
            set(value) {
                hrProfileSexState.value = value
            }
    }

    fun restoreStoredSettings(
        ftpWatts: Int,
        hrProfileAge: Int?,
        hrProfileSex: HrProfileSex?,
    ) {
        ftpWattsState.intValue = ftpWatts
        ftpInputTextState.value = ftpWatts.toString()
        ftpInputErrorState.value = null
        hrProfileAgeState.value = hrProfileAge
        hrProfileAgeInputState.value = hrProfileAge?.toString().orEmpty()
        hrProfileAgeErrorState.value = null
        hrProfileSexState.value = hrProfileSex
    }
}
