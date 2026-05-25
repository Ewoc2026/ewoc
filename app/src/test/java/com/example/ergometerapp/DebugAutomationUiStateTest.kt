package com.example.ergometerapp

import com.example.ergometerapp.session.MockTrainerDebugScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugAutomationUiStateTest {

    @Test
    fun mockTrainerScenario_isConsumedOnceWithoutAffectingDocumentsOverride() {
        val uiState = DebugAutomationUiState()

        uiState.armMockTrainerScenario(MockTrainerDebugScenario.WAITING_START_AND_PAUSE_CAPTURE)
        uiState.armDocumentsFolderWriteFailure()

        assertEquals(
            MockTrainerDebugScenario.WAITING_START_AND_PAUSE_CAPTURE,
            uiState.consumeMockTrainerScenario(),
        )
        assertNull(uiState.consumeMockTrainerScenario())
        assertTrue(uiState.forceDocumentsFolderWriteFailure)
    }

    @Test
    fun documentsFolderWriteFailure_isConsumedOnceWithoutAffectingMockTrainerScenario() {
        val uiState = DebugAutomationUiState()
        uiState.armMockTrainerScenario(MockTrainerDebugScenario.WAITING_START_AND_PAUSE_CAPTURE)
        uiState.armDocumentsFolderWriteFailure()

        assertTrue(uiState.consumeDocumentsFolderWriteFailure())
        assertFalse(uiState.consumeDocumentsFolderWriteFailure())
        assertEquals(
            MockTrainerDebugScenario.WAITING_START_AND_PAUSE_CAPTURE,
            uiState.pendingMockTrainerScenario,
        )
    }
}
