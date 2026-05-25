package com.example.ergometerapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionStatusMessageResolutionTest {

    @Test
    fun resolvePrimarySessionStatusMessageReturnsTrimmedOverrideMessage() {
        val message = resolvePrimarySessionStatusMessage("  Trainer disconnected  ")

        assertEquals("Trainer disconnected", message)
    }

    @Test
    fun resolvePrimarySessionStatusMessageReturnsNullWhenOverrideIsBlank() {
        val message = resolvePrimarySessionStatusMessage("   ")

        assertNull(message)
    }

    @Test
    fun resolvePrimarySessionStatusMessageReturnsNullWithoutOverride() {
        val message = resolvePrimarySessionStatusMessage(null)

        assertNull(message)
    }

    @Test
    fun shouldAnimateSessionStatusDotsReturnsFalseWhenOverrideIsVisible() {
        val animateDots = shouldAnimateSessionStatusDots(
            overrideMessage = "Trainer disconnected",
            waitingForUserAction = true,
        )

        assertFalse(animateDots)
    }

    @Test
    fun resolveSessionChartHeaderIntervalCueKeepsIntervalStateOutsideMessages() {
        val headerCue = resolveSessionChartHeaderIntervalCue(
            intervalPhase = "ON",
            intervalRemaining = "30 sec",
        )

        assertEquals("ON 30 sec", headerCue)
    }
}
