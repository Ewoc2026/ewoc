package io.github.ewoc2026.ewoc

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCadenceRetentionDecisionTest {
    @Test
    fun resolveSessionCadenceRetention_clearsInsideRecoveryBand() {
        val decision = resolveSessionCadenceRetention(
            targetRpm = 90,
            recoveryToleranceRpm = 2,
            liveCadenceRpm = 92,
            displayedCadenceRpm = 85,
            displayedTargetRpm = 90,
        )

        assertEquals(SessionCadenceRetentionDecision.CLEAR, decision)
    }

    @Test
    fun resolveSessionCadenceRetention_refreshesWhenCadenceStillOutsideBandAndChanged() {
        val decision = resolveSessionCadenceRetention(
            targetRpm = 90,
            recoveryToleranceRpm = 2,
            liveCadenceRpm = 84,
            displayedCadenceRpm = 85,
            displayedTargetRpm = 90,
        )

        assertEquals(SessionCadenceRetentionDecision.REFRESH, decision)
    }

    @Test
    fun resolveSessionCadenceRetention_keepsWhenCadenceAndTargetStaySame() {
        val decision = resolveSessionCadenceRetention(
            targetRpm = 90,
            recoveryToleranceRpm = 2,
            liveCadenceRpm = 84,
            displayedCadenceRpm = 84,
            displayedTargetRpm = 90,
        )

        assertEquals(SessionCadenceRetentionDecision.KEEP, decision)
    }
}
