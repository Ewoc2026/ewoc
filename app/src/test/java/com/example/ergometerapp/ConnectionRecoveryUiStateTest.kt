package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionRecoveryUiStateTest {

    @Test
    fun appUiStateForwardersAndPromptPortShareRecoveryBackingState() {
        val appUiState = AppUiState()
        val recoveryUiState = appUiState.connectionRecoveryUiState

        recoveryUiState.connectingTimeoutMessageState.value = "Still connecting"
        recoveryUiState.connectionIssuePromptStatePort.connectionIssueMessage = "Trainer disconnected"
        recoveryUiState.connectionIssuePromptStatePort.suggestTrainerSearchAfterConnectionIssue = true
        recoveryUiState.connectionIssuePromptStatePort.suggestOpenSettingsAfterConnectionIssue = true

        assertEquals("Still connecting", appUiState.connectingTimeoutMessage.value)
        assertEquals("Trainer disconnected", appUiState.connectionIssueMessage.value)
        assertTrue(appUiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertTrue(appUiState.suggestOpenSettingsAfterConnectionIssue.value)

        appUiState.connectionIssueMessage.value = null
        appUiState.suggestTrainerSearchAfterConnectionIssue.value = false
        appUiState.suggestOpenSettingsAfterConnectionIssue.value = false

        assertEquals(null, recoveryUiState.connectionIssuePromptStatePort.connectionIssueMessage)
        assertFalse(
            recoveryUiState.connectionIssuePromptStatePort.suggestTrainerSearchAfterConnectionIssue,
        )
        assertFalse(
            recoveryUiState.connectionIssuePromptStatePort.suggestOpenSettingsAfterConnectionIssue,
        )
    }
}
