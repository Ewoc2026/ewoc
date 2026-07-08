package io.github.ewoc2026.ewoc.session

import io.github.ewoc2026.ewoc.AppScreen

/**
 * Encapsulates request-control failure recovery routing.
 *
 * Why this exists:
 * - Request-control failures can arrive from both timeout and control-point response callbacks.
 * - Start-flow failure and mid-session recovery failure must not share the same teardown path.
 *
 * Invariants:
 * - CONNECTING failure returns to MENU with connection guidance because session start never completed.
 * - SESSION failure finalizes the active session through the explicit stop/summary path.
 * - Outside active flow, failure is ignored and delegated to diagnostics/logging callback.
 * - Recovery prompt always recommends trainer search, never settings.
 */
internal class SessionRequestControlFailureAdapter(
    private val currentScreen: () -> AppScreen,
    private val onFailureIgnoredOutsideActiveFlow: (reason: String) -> Unit,
    private val rollbackToMenuWithConnectionIssue: (
        message: String,
        reason: String,
        suggestTrainerSearch: Boolean,
        suggestOpenSettings: Boolean,
    ) -> Unit,
    private val finalizeSessionToSummaryWithConnectionIssue: (
        message: String,
        reason: String,
        suggestTrainerSearch: Boolean,
        suggestOpenSettings: Boolean,
    ) -> Unit,
) {
    /**
     * Handles request-control failure from FTMS response or timeout callbacks.
     */
    fun onRequestControlFailure(message: String, reason: String) {
        return when (currentScreen()) {
            AppScreen.CONNECTING -> {
                rollbackToMenuWithConnectionIssue(
                    message,
                    reason,
                    true,
                    false,
                )
            }

            AppScreen.SESSION -> {
                finalizeSessionToSummaryWithConnectionIssue(
                    message,
                    reason,
                    true,
                    false,
                )
            }

            else -> onFailureIgnoredOutsideActiveFlow(reason)
        }
    }
}
