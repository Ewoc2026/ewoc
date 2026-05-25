package com.example.ergometerapp

import com.example.ergometerapp.session.SessionSummary

/**
 * ViewModel-owned state bridge for summary FIT auto-export gating.
 *
 * Invariants:
 * - The summary and selected preference stay in the ViewModel layer so Compose-owned state remains local.
 * - The last exported fingerprint stays in the ViewModel layer because it is session UI state, not persisted policy.
 */
internal interface SummaryFitAutoExportStatePort {
    val preference: FitExportPreference?
    val summary: SessionSummary?
    var lastExportedSummaryFingerprint: String?
}

/**
 * Coordinates one-shot summary FIT auto-export decisions.
 *
 * Invariants:
 * - Auto-export runs only when the selected preference explicitly enables it.
 * - One summary fingerprint is attempted at most once, even if downstream export preparation later fails.
 * - Automatic exports always keep SAF picker fallback disabled because no user interaction is in progress.
 */
internal class SummaryFitAutoExportCoordinator(
    private val createFingerprint: (SessionSummary) -> String,
    private val prepareExport: () -> String?,
    private val tryExportPendingToDocumentsFolder: (suggestedFileName: String, allowPickerFallback: Boolean) -> Unit,
) {

    fun tryAutoExportIfNeeded(statePort: SummaryFitAutoExportStatePort) {
        if (statePort.preference != FitExportPreference.AUTO_SAVE) return

        val summary = statePort.summary ?: return
        val fingerprint = createFingerprint(summary)
        if (fingerprint == statePort.lastExportedSummaryFingerprint) return

        statePort.lastExportedSummaryFingerprint = fingerprint
        val suggestedFileName = prepareExport() ?: return
        tryExportPendingToDocumentsFolder(
            suggestedFileName,
            false,
        )
    }
}
