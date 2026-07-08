package io.github.ewoc2026.ewoc

/**
 * State bridge consumed by [ProfileSettingsCoordinator].
 *
 * Keeping profile input/output fields behind a dedicated port lets us lock
 * validation and side-effect ordering in isolated tests without Compose runtime
 * or MainViewModel coupling.
 */
internal interface ProfileSettingsStatePort {
    var ftpInputText: String
    var ftpInputError: String?
    var ftpWatts: Int
    var hrProfileAgeInput: String
    var hrProfileAgeError: String?
    var hrProfileAge: Int?
    var hrProfileSex: HrProfileSex?
}

/**
 * Coordinates profile setting updates from menu input callbacks.
 *
 * Invariants:
 * - Invalid FTP/age input updates inline error state and skips persistence.
 * - Valid FTP path persists first, then notifies session sync, then refreshes AI.
 * - Valid age/sex paths persist profile values before recommendation refresh.
 */
internal class ProfileSettingsCoordinator(
    private val statePort: ProfileSettingsStatePort,
    private val ftpInputMaxLength: Int,
    private val hrProfileAgeInputMaxLength: Int,
    private val ftpInvalidInputErrorMessage: () -> String,
    private val hrProfileAgeInvalidInputErrorMessage: () -> String,
    private val saveFtpWatts: (Int) -> Unit,
    private val onFtpWattsSaved: () -> Unit,
    private val saveHrProfileAge: (Int) -> Unit,
    private val saveHrProfileSex: (HrProfileSex) -> Unit,
    private val refreshAiAssistantRecommendations: () -> Unit,
) {
    fun onFtpInputChanged(rawInput: String) {
        val sanitized = rawInput.filter { it.isDigit() }.take(ftpInputMaxLength)
        statePort.ftpInputText = sanitized
        val parsed = sanitized.toIntOrNull()
        if (parsed == null || parsed <= 0) {
            statePort.ftpInputError = ftpInvalidInputErrorMessage()
            return
        }
        statePort.ftpInputError = null
        statePort.ftpWatts = parsed
        saveFtpWatts(parsed)
        onFtpWattsSaved()
        refreshAiAssistantRecommendations()
    }

    fun onHrProfileAgeInputChanged(rawInput: String) {
        val sanitized = rawInput.filter { it.isDigit() }.take(hrProfileAgeInputMaxLength)
        statePort.hrProfileAgeInput = sanitized
        val parsed = sanitized.toIntOrNull()
        if (parsed == null || parsed !in 13..100) {
            statePort.hrProfileAgeError = hrProfileAgeInvalidInputErrorMessage()
            return
        }
        statePort.hrProfileAgeError = null
        statePort.hrProfileAge = parsed
        saveHrProfileAge(parsed)
        refreshAiAssistantRecommendations()
    }

    fun onHrProfileSexSelected(sex: HrProfileSex) {
        statePort.hrProfileSex = sex
        saveHrProfileSex(sex)
        refreshAiAssistantRecommendations()
    }
}
