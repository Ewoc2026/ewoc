package io.github.ewoc2026.ewoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionIssuePromptCoordinatorTest {
    @Test
    fun clearPrompt_resetsMessageAndSuggestionFlagsBeforeRefreshingRecommendations() {
        val record = ConnectionIssueEffectRecord()
        val state = FakeStatePort(record).apply {
            connectionIssueMessage = "Connection failed"
            suggestTrainerSearchAfterConnectionIssue = true
            suggestOpenSettingsAfterConnectionIssue = true
            record.events.clear()
        }
        val coordinator = buildCoordinator(state, record)

        coordinator.clearPrompt()

        assertNull(state.connectionIssueMessage)
        assertFalse(state.suggestTrainerSearchAfterConnectionIssue)
        assertFalse(state.suggestOpenSettingsAfterConnectionIssue)
        assertEquals(
            listOf(
                "set_message",
                "set_suggest_trainer_search",
                "set_suggest_open_settings",
                "refresh_recommendations",
            ),
            record.events,
        )
    }

    @Test
    fun onSearchFtmsDevicesFromPrompt_clearsPromptBeforeSearchRequest() {
        val record = ConnectionIssueEffectRecord()
        val state = FakeStatePort(record).apply {
            connectionIssueMessage = "Timeout"
            suggestTrainerSearchAfterConnectionIssue = true
            suggestOpenSettingsAfterConnectionIssue = true
            record.events.clear()
        }
        val coordinator = buildCoordinator(state, record)

        coordinator.onSearchFtmsDevicesFromPrompt()

        assertNull(state.connectionIssueMessage)
        assertFalse(state.suggestTrainerSearchAfterConnectionIssue)
        assertFalse(state.suggestOpenSettingsAfterConnectionIssue)
        assertEquals(
            listOf(
                "set_message",
                "set_suggest_trainer_search",
                "set_suggest_open_settings",
                "refresh_recommendations",
                "request_ftms_scan",
            ),
            record.events,
        )
    }

    private fun buildCoordinator(
        state: FakeStatePort,
        record: ConnectionIssueEffectRecord,
    ): ConnectionIssuePromptCoordinator {
        return ConnectionIssuePromptCoordinator(
            statePort = state,
            refreshAiAssistantRecommendations = { record.events += "refresh_recommendations" },
            onSearchFtmsDevicesRequested = { record.events += "request_ftms_scan" },
        )
    }

    private class FakeStatePort(
        private val record: ConnectionIssueEffectRecord,
    ) : ConnectionIssuePromptStatePort {
        override var connectionIssueMessage: String? = null
            set(value) {
                field = value
                record.events += "set_message"
            }
        override var suggestTrainerSearchAfterConnectionIssue: Boolean = false
            set(value) {
                field = value
                record.events += "set_suggest_trainer_search"
            }
        override var suggestOpenSettingsAfterConnectionIssue: Boolean = false
            set(value) {
                field = value
                record.events += "set_suggest_open_settings"
            }
    }

    private class ConnectionIssueEffectRecord {
        val events = mutableListOf<String>()
    }
}
