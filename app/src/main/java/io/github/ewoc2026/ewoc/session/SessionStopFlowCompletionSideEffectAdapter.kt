package io.github.ewoc2026.ewoc.session

import io.github.ewoc2026.ewoc.AppScreen
import io.github.ewoc2026.ewoc.AppUiState

/**
 * Applies stop-flow completion side effects in deterministic UI order.
 *
 * Why this exists:
 * - `SessionStopFlowCompletionPolicy` decides *when* completion is allowed.
 * - This adapter owns *how* summary UI side effects are applied once completion wins.
 *
 * Invariants:
 * - Session summary finalization runs exactly at stop-flow terminal completion,
 *   not at stop-button press, so stop timestamp aligns with ACK/disconnect/timeout.
 * - Summary payload is published before switching to `SUMMARY`.
 * - Screen wake-lock release happens before post-transition callbacks, so callers can
 *   treat callback execution as "summary transition already applied".
 */
internal class SessionStopFlowCompletionSideEffectAdapter(
    private val uiState: AppUiState,
    private val finalizeSessionSummary: () -> Unit,
    private val summaryProvider: () -> SessionSummary?,
    private val allowScreenOff: () -> Unit,
    private val onAfterSummaryTransition: (reason: String) -> Unit,
) {
    /**
     * Runs summary side effects after stop-flow completion has been accepted.
     */
    fun completeToSummary(reason: String) {
        finalizeSessionSummary()
        uiState.summary.value = summaryProvider()
        allowScreenOff()
        uiState.screen.value = AppScreen.SUMMARY
        onAfterSummaryTransition(reason)
    }
}
