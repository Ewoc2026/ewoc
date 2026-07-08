package io.github.ewoc2026.ewoc

/**
 * ViewModel-owned state bridge for summary FIT export preference changes.
 *
 * Invariants:
 * - The selected preference stays in the ViewModel layer so Compose can react immediately.
 * - Preference acknowledgements always reuse the shared FIT export status channel.
 */
internal interface SummaryFitExportPreferenceStatePort {
    var preference: FitExportPreference?
    var statusMessage: String?
    var statusIsError: Boolean
}

/**
 * Coordinates summary FIT export preference persistence and acknowledgement status messaging.
 *
 * Invariants:
 * - Persisted preference and in-memory preference are updated in the same call.
 * - Preference changes always publish a non-error acknowledgement message.
 */
internal class SummaryFitExportPreferenceCoordinator(
    private val statePort: SummaryFitExportPreferenceStatePort,
    private val savePreference: (FitExportPreference) -> Unit,
    private val autoSaveEnabledMessage: () -> String,
    private val askEveryTimeEnabledMessage: () -> String,
    private val doNotSaveEnabledMessage: () -> String,
) {

    fun onPreferenceSelected(preference: FitExportPreference) {
        statePort.preference = preference
        savePreference(preference)
        statePort.statusMessage = when (preference) {
            FitExportPreference.AUTO_SAVE -> autoSaveEnabledMessage()
            FitExportPreference.ASK_EVERY_TIME -> askEveryTimeEnabledMessage()
            FitExportPreference.DO_NOT_SAVE -> doNotSaveEnabledMessage()
        }
        statePort.statusIsError = false
    }
}
