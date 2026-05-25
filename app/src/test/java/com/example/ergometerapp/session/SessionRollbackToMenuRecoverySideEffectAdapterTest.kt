package com.example.ergometerapp.session

import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRollbackToMenuRecoverySideEffectAdapterTest {

    @Test
    fun rollbackToMenuRunsCleanupBeforePostRollbackCallback() {
        val uiState = AppUiState().apply {
            screen.value = AppScreen.CONNECTING
            pendingSessionStartAfterPermission = true
            pendingCadenceStartAfterControlGranted = true
            autoPausedByZeroCadence = true
            connectingTimeoutMessage.value = "waiting"
        }
        val operations = mutableListOf<String>()
        val callbackReasons = mutableListOf<String>()

        val adapter = SessionRollbackToMenuRecoverySideEffectAdapter(
            uiState = uiState,
            cancelConnectFlowTimeout = { operations += "cancelConnectFlowTimeout" },
            cancelMockConnectTransition = { operations += "cancelMockConnectTransition" },
            stopMockTrainerEngine = { operations += "stopMockTrainerEngine" },
            stopWorkout = { operations += "stopWorkout" },
            clearWorkoutRunner = { operations += "clearWorkoutRunner" },
            stopSession = { operations += "stopSession" },
            resetStopFlowPolicy = { operations += "resetStopFlowPolicy" },
            resetFtmsUiState = { operations += "resetFtmsUiState" },
            allowScreenOff = { operations += "allowScreenOff" },
            closeBleTransport = { operations += "closeBleTransport" },
            onAfterRollbackApplied = { reason ->
                operations += "onAfterRollbackApplied"
                callbackReasons += reason
                assertEquals(AppScreen.MENU, uiState.screen.value)
            },
        )

        adapter.rollbackToMenu(
            message = "connection issue",
            reason = "session.request_control_timeout",
            suggestTrainerSearch = true,
            suggestOpenSettings = false,
        )

        assertEquals(
            listOf(
                "cancelConnectFlowTimeout",
                "cancelMockConnectTransition",
                "stopMockTrainerEngine",
                "stopWorkout",
                "clearWorkoutRunner",
                "stopSession",
                "resetStopFlowPolicy",
                "resetFtmsUiState",
                "allowScreenOff",
                "closeBleTransport",
                "onAfterRollbackApplied",
            ),
            operations,
        )
        assertEquals(listOf("session.request_control_timeout"), callbackReasons)
        assertEquals(AppScreen.MENU, uiState.screen.value)
        assertFalse(uiState.pendingSessionStartAfterPermission)
        assertFalse(uiState.pendingCadenceStartAfterControlGranted)
        assertFalse(uiState.autoPausedByZeroCadence)
        assertNull(uiState.connectingTimeoutMessage.value)
    }

    @Test
    fun rollbackToMenuPublishesConfiguredRecoveryPromptFlags() {
        val uiState = AppUiState().apply {
            screen.value = AppScreen.SESSION
        }

        val adapter = SessionRollbackToMenuRecoverySideEffectAdapter(
            uiState = uiState,
            cancelConnectFlowTimeout = {},
            cancelMockConnectTransition = {},
            stopMockTrainerEngine = {},
            stopWorkout = {},
            clearWorkoutRunner = {},
            stopSession = {},
            resetStopFlowPolicy = {},
            resetFtmsUiState = {},
            allowScreenOff = {},
            closeBleTransport = {},
            onAfterRollbackApplied = {},
        )

        adapter.rollbackToMenu(
            message = "permission denied",
            reason = "connect_timeout_back_to_menu",
            suggestTrainerSearch = false,
            suggestOpenSettings = true,
        )

        assertEquals("permission denied", uiState.connectionIssueMessage.value)
        assertFalse(uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertTrue(uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertEquals(AppScreen.MENU, uiState.screen.value)
    }
}
