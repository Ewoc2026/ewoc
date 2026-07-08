package io.github.ewoc2026.ewoc.workout

/**
 * Maps preserved canonical `.ewo` messages onto the existing text-event seam.
 *
 * The current session rail already understands [WorkoutTextEvent], so this
 * mapper exposes canonical authored messages without widening the runtime
 * runner contract or changing any UI layout. Translation selection now stays in
 * the presentation layer so imported metadata remains stable across locale
 * changes while the session rail still renders one concrete text event stream.
 */
object ImportedErgoWorkoutTextEventMapper {
    /**
     * Flattens workout- and step-level canonical start messages into
     * workout-time text events.
     */
    fun map(
        workout: ImportedErgoWorkout,
        preferredLanguageTags: List<String> = emptyList(),
    ): List<WorkoutTextEvent> {
        val events = mutableListOf<WorkoutTextEvent>()

        workout.canonicalMetadata
            ?.messages
            .orEmpty()
            .forEach { message ->
                mapMessage(
                    message = message,
                    anchorStartOffsetSec = 0,
                    anchorEndOffsetSec = workout.totalDurationSec,
                    preferredLanguageTags = preferredLanguageTags,
                )?.let(events::add)
            }

        workout.steps.forEach { step ->
            step.canonicalMetadata
                ?.messages
                .orEmpty()
                .forEach { message ->
                    mapMessage(
                        message = message,
                        anchorStartOffsetSec = step.startOffsetSec,
                        anchorEndOffsetSec = step.startOffsetSec + step.durationSec,
                        preferredLanguageTags = preferredLanguageTags,
                    )?.let(events::add)
                }
        }

        return events.toList()
    }

    private fun mapMessage(
        message: ImportedErgoWorkoutMessage,
        anchorStartOffsetSec: Int,
        anchorEndOffsetSec: Int,
        preferredLanguageTags: List<String>,
    ): WorkoutTextEvent? {
        val renderedText = ImportedErgoWorkoutLocalizedTextResolver.resolve(
            text = message.text,
            preferredLanguageTags = preferredLanguageTags,
        ).trim()
        if (renderedText.isEmpty()) return null

        val baseOffsetSec = when (message.timing.anchor) {
            ImportedErgoWorkoutMessageAnchor.START -> anchorStartOffsetSec
            ImportedErgoWorkoutMessageAnchor.END -> anchorEndOffsetSec
        }

        return WorkoutTextEvent(
            timeOffsetSec = (baseOffsetSec + message.timing.offsetSec).coerceAtLeast(0),
            message = renderedText,
            durationSec = message.timing.durationSec?.takeIf { it > 0 },
        )
    }
}
