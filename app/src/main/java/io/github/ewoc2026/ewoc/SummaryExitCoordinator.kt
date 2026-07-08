package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.session.SessionSummary
import io.github.ewoc2026.ewoc.session.export.SessionExportSnapshot

/**
 * ViewModel-owned state bridge for leaving the summary screen.
 *
 * Invariants:
 * - Summary-only cleanup stays in the ViewModel layer so UI navigation can remain a separate concern.
 * - Leaving summary must clear staged export payloads so stale callbacks cannot complete later.
 */
internal interface SummaryExitStatePort {
    var summary: SessionSummary?
    var connectingTimeoutMessage: String?
    var fitPendingSnapshot: SessionExportSnapshot?
    var fitStatusMessage: String?
    var fitStatusIsError: Boolean
    var lastAutoExportSummaryFingerprint: String?
}

/**
 * Coordinates summary-only reset policy before navigation returns to MENU.
 *
 * Invariants:
 * - Summary exit always clears staged export state before the next summary can be shown.
 * - Summary AI messaging is cleared through the injected callback so phase-specific cleanup stays centralized.
 */
internal class SummaryExitCoordinator(
    private val clearSummaryAiMessage: () -> Unit,
) {

    fun resetForMenuReturn(statePort: SummaryExitStatePort) {
        statePort.summary = null
        statePort.connectingTimeoutMessage = null
        statePort.fitPendingSnapshot = null
        statePort.fitStatusMessage = null
        statePort.fitStatusIsError = false
        statePort.lastAutoExportSummaryFingerprint = null
        clearSummaryAiMessage()
    }
}
