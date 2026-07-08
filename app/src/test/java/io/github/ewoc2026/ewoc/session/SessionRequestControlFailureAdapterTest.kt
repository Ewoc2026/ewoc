package io.github.ewoc2026.ewoc.session

import io.github.ewoc2026.ewoc.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRequestControlFailureAdapterTest {

    @Test
    fun connectingScreenRoutesFailureToRollbackWithRecoveryPrompt() {
        val ignoredReasons = mutableListOf<String>()
        val rollbackCalls = mutableListOf<RollbackCall>()
        val finalizeCalls = mutableListOf<RollbackCall>()
        val adapter = SessionRequestControlFailureAdapter(
            currentScreen = { AppScreen.CONNECTING },
            onFailureIgnoredOutsideActiveFlow = { reason -> ignoredReasons += reason },
            rollbackToMenuWithConnectionIssue = { message, reason, suggestTrainerSearch, suggestOpenSettings ->
                rollbackCalls += RollbackCall(
                    message = message,
                    reason = reason,
                    suggestTrainerSearch = suggestTrainerSearch,
                    suggestOpenSettings = suggestOpenSettings,
                )
            },
            finalizeSessionToSummaryWithConnectionIssue = { message, reason, suggestTrainerSearch, suggestOpenSettings ->
                finalizeCalls += RollbackCall(
                    message = message,
                    reason = reason,
                    suggestTrainerSearch = suggestTrainerSearch,
                    suggestOpenSettings = suggestOpenSettings,
                )
            },
        )

        adapter.onRequestControlFailure(
            message = "request-control rejected",
            reason = "session.request_control_rejected",
        )

        assertTrue(ignoredReasons.isEmpty())
        assertTrue(finalizeCalls.isEmpty())
        assertEquals(
            listOf(
                RollbackCall(
                    message = "request-control rejected",
                    reason = "session.request_control_rejected",
                    suggestTrainerSearch = true,
                    suggestOpenSettings = false,
                )
            ),
            rollbackCalls,
        )
    }

    @Test
    fun sessionScreenRoutesFailureToSummaryFinalizationWithRecoveryPrompt() {
        val ignoredReasons = mutableListOf<String>()
        val rollbackCalls = mutableListOf<RollbackCall>()
        val finalizeCalls = mutableListOf<RollbackCall>()
        val adapter = SessionRequestControlFailureAdapter(
            currentScreen = { AppScreen.SESSION },
            onFailureIgnoredOutsideActiveFlow = { reason -> ignoredReasons += reason },
            rollbackToMenuWithConnectionIssue = { message, reason, suggestTrainerSearch, suggestOpenSettings ->
                rollbackCalls += RollbackCall(
                    message = message,
                    reason = reason,
                    suggestTrainerSearch = suggestTrainerSearch,
                    suggestOpenSettings = suggestOpenSettings,
                )
            },
            finalizeSessionToSummaryWithConnectionIssue = { message, reason, suggestTrainerSearch, suggestOpenSettings ->
                finalizeCalls += RollbackCall(
                    message = message,
                    reason = reason,
                    suggestTrainerSearch = suggestTrainerSearch,
                    suggestOpenSettings = suggestOpenSettings,
                )
            },
        )

        adapter.onRequestControlFailure(
            message = "request-control timeout",
            reason = "session.request_control_timeout",
        )

        assertTrue(ignoredReasons.isEmpty())
        assertTrue(rollbackCalls.isEmpty())
        assertEquals(
            listOf(
                RollbackCall(
                    message = "request-control timeout",
                    reason = "session.request_control_timeout",
                    suggestTrainerSearch = true,
                    suggestOpenSettings = false,
                )
            ),
            finalizeCalls,
        )
    }

    @Test
    fun menuScreenIgnoresFailureAndSkipsRollback() {
        val ignoredReasons = mutableListOf<String>()
        var rollbackCalls = 0
        var finalizeCalls = 0
        val adapter = SessionRequestControlFailureAdapter(
            currentScreen = { AppScreen.MENU },
            onFailureIgnoredOutsideActiveFlow = { reason -> ignoredReasons += reason },
            rollbackToMenuWithConnectionIssue = { _, _, _, _ -> rollbackCalls += 1 },
            finalizeSessionToSummaryWithConnectionIssue = { _, _, _, _ -> finalizeCalls += 1 },
        )

        adapter.onRequestControlFailure(
            message = "ignored",
            reason = "session.request_control_timeout",
        )

        assertEquals(listOf("session.request_control_timeout"), ignoredReasons)
        assertEquals(0, rollbackCalls)
        assertEquals(0, finalizeCalls)
    }

    private data class RollbackCall(
        val message: String,
        val reason: String,
        val suggestTrainerSearch: Boolean,
        val suggestOpenSettings: Boolean,
    )
}
