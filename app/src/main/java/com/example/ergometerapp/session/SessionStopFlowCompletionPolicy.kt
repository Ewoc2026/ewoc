package com.example.ergometerapp.session

/**
 * Owns one-shot completion routing for stop-flow terminal signals.
 *
 * Why this exists:
 * - `SessionOrchestrator` can receive multiple terminal callbacks (ack/timeout/disconnect).
 * - Only the first terminal callback may transition to summary side effects.
 *
 * Invariants:
 * - Side effects run only after stop-flow state is transitioned to `IDLE`.
 * - Duplicate terminal callbacks are ignored once completion has happened.
 */
internal class SessionStopFlowCompletionPolicy(
    private val stopFlowPolicy: SessionStopFlowPolicy,
    private val onStopFlowCompleted: (reason: String) -> Unit,
) {
    /**
     * Completes stop-flow exactly once and triggers summary side effects.
     */
    fun completeToSummaryIfInProgress(reason: String): Boolean {
        if (!stopFlowPolicy.completeToIdleIfInProgress()) return false
        onStopFlowCompleted(reason)
        return true
    }
}
