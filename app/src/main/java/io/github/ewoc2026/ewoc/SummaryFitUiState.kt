package io.github.ewoc2026.ewoc

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.ewoc2026.ewoc.session.SessionSummary

/**
 * Owns summary FIT preference state plus the transient auto-export fingerprint gate.
 *
 * Keeping the selected preference and the one-shot auto-export marker together prevents summary
 * FIT coordinators from reintroducing separate backing vars in `MainViewModel` while leaving
 * shared SAF export status in `DocumentsFolderUiState`, where the export routing already lives.
 */
internal class SummaryFitUiState {
    val preferenceState = mutableStateOf<FitExportPreference?>(null)

    var lastAutoExportSummaryFingerprint: String? = null

    fun exportPreferenceStatePort(
        statusMessageState: MutableState<String?>,
        statusIsErrorState: MutableState<Boolean>,
    ) = object : SummaryFitExportPreferenceStatePort {
        override var preference: FitExportPreference?
            get() = preferenceState.value
            set(value) {
                preferenceState.value = value
            }

        override var statusMessage: String?
            get() = statusMessageState.value
            set(value) {
                statusMessageState.value = value
            }

        override var statusIsError: Boolean
            get() = statusIsErrorState.value
            set(value) {
                statusIsErrorState.value = value
            }
    }

    fun autoExportStatePort(summaryProvider: () -> SessionSummary?) = object : SummaryFitAutoExportStatePort {
        override val preference: FitExportPreference?
            get() = preferenceState.value

        override val summary: SessionSummary?
            get() = summaryProvider()

        override var lastExportedSummaryFingerprint: String?
            get() = lastAutoExportSummaryFingerprint
            set(value) {
                lastAutoExportSummaryFingerprint = value
            }
    }
}
