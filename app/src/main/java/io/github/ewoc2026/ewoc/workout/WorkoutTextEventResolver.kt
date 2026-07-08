package io.github.ewoc2026.ewoc.workout

/**
 * Fallback display duration for text events when the source file does not define one.
 */
const val DefaultWorkoutTextEventDurationSec: Int = 10

/**
 * Resolves the currently active workout text event for the given workout elapsed time.
 *
 * Resolution is based on workout-time seconds, so pause behavior is naturally handled by
 * whichever caller controls elapsed progression.
 *
 * If event durations overlap, the last matching event in input order wins.
 */
fun resolveActiveWorkoutTextEvent(
    textEvents: List<WorkoutTextEvent>,
    workoutElapsedSec: Int?,
    defaultDurationSec: Int = DefaultWorkoutTextEventDurationSec,
): WorkoutTextEvent? {
    val elapsedSec = workoutElapsedSec ?: return null
    if (textEvents.isEmpty()) return null
    val safeFallbackDuration = defaultDurationSec.coerceAtLeast(1)

    var activeEvent: WorkoutTextEvent? = null
    textEvents.forEach { event ->
        val startSec = event.timeOffsetSec.coerceAtLeast(0)
        val durationSec = event.durationSec?.takeIf { it > 0 } ?: safeFallbackDuration
        val endExclusiveSec = startSec.toLong() + durationSec.toLong()
        val active = elapsedSec >= startSec && elapsedSec.toLong() < endExclusiveSec
        if (active) {
            activeEvent = event
        }
    }
    return activeEvent
}
