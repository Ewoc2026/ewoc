package com.example.ergometerapp

/**
 * State bridge consumed by [ConnectionIssuePromptCoordinator].
 *
 * Keeping prompt fields behind a dedicated port keeps ordering-sensitive
 * reset behavior unit-testable without ViewModel/Compose runtime coupling.
 */
internal interface ConnectionIssuePromptStatePort {
    var connectionIssueMessage: String?
    var suggestTrainerSearchAfterConnectionIssue: Boolean
    var suggestOpenSettingsAfterConnectionIssue: Boolean
}

/**
 * Coordinates connection-issue prompt clear and follow-up picker search flows.
 *
 * Invariants:
 * - Prompt clear always resets message and both suggestion flags first.
 * - Recommendation refresh always runs after prompt-state reset.
 * - Search-from-prompt always clears prompt state before FTMS picker request.
 */
internal class ConnectionIssuePromptCoordinator(
    private val statePort: ConnectionIssuePromptStatePort,
    private val refreshAiAssistantRecommendations: () -> Unit,
    private val onSearchFtmsDevicesRequested: () -> Unit,
) {
    fun clearPrompt() {
        statePort.connectionIssueMessage = null
        statePort.suggestTrainerSearchAfterConnectionIssue = false
        statePort.suggestOpenSettingsAfterConnectionIssue = false
        refreshAiAssistantRecommendations()
    }

    fun onSearchFtmsDevicesFromPrompt() {
        clearPrompt()
        onSearchFtmsDevicesRequested()
    }
}
