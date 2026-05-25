package com.example.ergometerapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostWorkoutCompletionChoicePolicyTest {
    @Test
    fun continueRideRequiresPreparedExitWindow() {
        assertTrue(requiresPreparedPostWorkoutExitWindow(PostWorkoutCompletionChoice.CONTINUE))
    }

    @Test
    fun summaryUsesOrdinaryFinishPath() {
        assertFalse(requiresPreparedPostWorkoutExitWindow(PostWorkoutCompletionChoice.SUMMARY))
    }
}
