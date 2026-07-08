package io.github.ewoc2026.ewoc.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutTextEventResolverTest {
    @Test
    fun usesEventDurationWhenPresent() {
        val events = listOf(
            WorkoutTextEvent(
                timeOffsetSec = 10,
                message = "Hold cadence",
                durationSec = 4,
            ),
        )

        assertNull(resolveActiveWorkoutTextEvent(events, workoutElapsedSec = 9))
        assertEquals("Hold cadence", resolveActiveWorkoutTextEvent(events, workoutElapsedSec = 10)?.message)
        assertEquals("Hold cadence", resolveActiveWorkoutTextEvent(events, workoutElapsedSec = 13)?.message)
        assertNull(resolveActiveWorkoutTextEvent(events, workoutElapsedSec = 14))
    }

    @Test
    fun fallsBackToDefaultDurationWhenDurationMissing() {
        val events = listOf(
            WorkoutTextEvent(
                timeOffsetSec = 20,
                message = "Drink",
                durationSec = null,
            ),
        )

        assertEquals("Drink", resolveActiveWorkoutTextEvent(events, workoutElapsedSec = 20)?.message)
        assertEquals("Drink", resolveActiveWorkoutTextEvent(events, workoutElapsedSec = 29)?.message)
        assertNull(resolveActiveWorkoutTextEvent(events, workoutElapsedSec = 30))
    }

    @Test
    fun activeEventStaysSameWhenElapsedTimeDoesNotAdvance() {
        val events = listOf(
            WorkoutTextEvent(
                timeOffsetSec = 40,
                message = "Relax grip",
                durationSec = 6,
            ),
        )

        val beforePause = resolveActiveWorkoutTextEvent(events, workoutElapsedSec = 42)?.message
        val duringPauseSameElapsed = resolveActiveWorkoutTextEvent(events, workoutElapsedSec = 42)?.message
        val afterResumeSameElapsed = resolveActiveWorkoutTextEvent(events, workoutElapsedSec = 42)?.message

        assertEquals("Relax grip", beforePause)
        assertEquals(beforePause, duringPauseSameElapsed)
        assertEquals(beforePause, afterResumeSameElapsed)
    }

    @Test
    fun latestMatchingEventWinsWhenDurationsOverlap() {
        val events = listOf(
            WorkoutTextEvent(timeOffsetSec = 50, message = "Base message", durationSec = 8),
            WorkoutTextEvent(timeOffsetSec = 54, message = "Override message", durationSec = 6),
        )

        assertEquals("Base message", resolveActiveWorkoutTextEvent(events, workoutElapsedSec = 52)?.message)
        assertEquals("Override message", resolveActiveWorkoutTextEvent(events, workoutElapsedSec = 55)?.message)
    }
}
