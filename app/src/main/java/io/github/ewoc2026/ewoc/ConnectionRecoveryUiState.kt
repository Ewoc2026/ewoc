package io.github.ewoc2026.ewoc

import androidx.compose.runtime.mutableStateOf

/**
 * Owns the menu-facing recovery prompt and connect-timeout state shared across session rollback,
 * summary exit cleanup, and prompt follow-up actions.
 *
 * Keeping the recovery message, suggestion flags, and timeout banner together prevents
 * `AppUiState` and `MainViewModel` from reintroducing another loose connection-failure cluster
 * while preserving the existing observable state shape already consumed by orchestration code.
 */
internal class ConnectionRecoveryUiState {
    val connectionIssueMessageState = mutableStateOf<String?>(null)
    val suggestTrainerSearchAfterConnectionIssueState = mutableStateOf(false)
    val suggestOpenSettingsAfterConnectionIssueState = mutableStateOf(false)
    val connectingTimeoutMessageState = mutableStateOf<String?>(null)

    val connectionIssuePromptStatePort = object : ConnectionIssuePromptStatePort {
        override var connectionIssueMessage: String?
            get() = connectionIssueMessageState.value
            set(value) {
                connectionIssueMessageState.value = value
            }

        override var suggestTrainerSearchAfterConnectionIssue: Boolean
            get() = suggestTrainerSearchAfterConnectionIssueState.value
            set(value) {
                suggestTrainerSearchAfterConnectionIssueState.value = value
            }

        override var suggestOpenSettingsAfterConnectionIssue: Boolean
            get() = suggestOpenSettingsAfterConnectionIssueState.value
            set(value) {
                suggestOpenSettingsAfterConnectionIssueState.value = value
            }
    }
}
