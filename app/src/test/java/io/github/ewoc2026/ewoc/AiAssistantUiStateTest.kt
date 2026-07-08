package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.ai.AiPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantUiStateTest {

    @Test
    fun appUiStateForwardersAndAssistantOwnerShareBackingState() {
        val appUiState = AppUiState()
        val assistantUiState = appUiState.aiAssistantUiState

        assistantUiState.setPhaseMessage(
            phase = AiPhase.MENU,
            message = "Menu guidance",
            isError = true,
            menuTemplateKey = "menu.template",
        )
        assistantUiState.setPhaseMessage(
            phase = AiPhase.SESSION,
            message = "Session cue",
            isError = false,
        )
        assistantUiState.setPhaseMessage(
            phase = AiPhase.SUMMARY,
            message = "Summary cue",
            isError = true,
        )

        assertEquals("Menu guidance", appUiState.aiMenuAssistantMessage.value)
        assertTrue(appUiState.aiMenuAssistantIsError.value)
        assertEquals("menu.template", appUiState.aiMenuAssistantTemplateKey.value)
        assertEquals("Session cue", appUiState.aiSessionAssistantMessage.value)
        assertFalse(appUiState.aiSessionAssistantIsError.value)
        assertEquals("Summary cue", appUiState.aiSummaryAssistantMessage.value)
        assertTrue(appUiState.aiSummaryAssistantIsError.value)

        appUiState.aiMenuAssistantMessage.value = null
        appUiState.aiMenuAssistantIsError.value = false
        appUiState.aiMenuAssistantTemplateKey.value = null
        appUiState.aiSessionAssistantMessage.value = null
        appUiState.aiSessionAssistantIsError.value = true
        appUiState.aiSummaryAssistantMessage.value = null
        appUiState.aiSummaryAssistantIsError.value = false

        assertEquals(null, assistantUiState.menuMessageState.value)
        assertFalse(assistantUiState.menuIsErrorState.value)
        assertEquals(null, assistantUiState.menuTemplateKeyState.value)
        assertEquals(null, assistantUiState.sessionMessageState.value)
        assertTrue(assistantUiState.sessionIsErrorState.value)
        assertEquals(null, assistantUiState.summaryMessageState.value)
        assertFalse(assistantUiState.summaryIsErrorState.value)
    }

    @Test
    fun clearPhaseMessageOnlyResetsRequestedPhase() {
        val uiState = AiAssistantUiState()

        uiState.setPhaseMessage(
            phase = AiPhase.MENU,
            message = "Menu guidance",
            isError = true,
            menuTemplateKey = "menu.template",
        )
        uiState.setPhaseMessage(
            phase = AiPhase.SESSION,
            message = "Session cue",
            isError = true,
        )
        uiState.setPhaseMessage(
            phase = AiPhase.SUMMARY,
            message = "Summary cue",
            isError = false,
        )

        uiState.clearPhaseMessage(AiPhase.SESSION)

        assertEquals("Menu guidance", uiState.menuMessageState.value)
        assertTrue(uiState.menuIsErrorState.value)
        assertEquals("menu.template", uiState.menuTemplateKeyState.value)
        assertEquals(null, uiState.sessionMessageState.value)
        assertFalse(uiState.sessionIsErrorState.value)
        assertEquals("Summary cue", uiState.summaryMessageState.value)
        assertFalse(uiState.summaryIsErrorState.value)
    }
}
