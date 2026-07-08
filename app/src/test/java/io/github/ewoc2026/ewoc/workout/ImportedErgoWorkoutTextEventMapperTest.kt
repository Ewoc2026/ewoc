package io.github.ewoc2026.ewoc.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportedErgoWorkoutTextEventMapperTest {
    @Test
    fun mapsWorkoutAndStepStartMessagesToWorkoutTextEvents() {
        val workout = ImportedErgoWorkout(
            title = "Canonical builder",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.PowerSteady(
                    stepIndex = 0,
                    startOffsetSec = 30,
                    durationSec = 120,
                    watts = 210,
                    canonicalMetadata = ImportedErgoWorkoutStepCanonicalMetadata(
                        messages = listOf(
                            importedMessage(defaultText = "  Hold steady.  "),
                        ),
                        origin = ImportedErgoWorkoutStepOrigin(
                            sourceSegmentId = "steady-1",
                            sourceSegmentLabel = null,
                            sourceSegmentNote = null,
                            enclosingRepeatSegmentId = null,
                            repeatIterationIndex = null,
                        ),
                    ),
                ),
            ),
            totalDurationSec = 150,
            canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                uid = null,
                revision = null,
                control = null,
                messages = listOf(importedMessage(defaultText = "Warm into it.")),
            ),
        )

        val events = ImportedErgoWorkoutTextEventMapper.map(workout)

        assertEquals(
            listOf(
                WorkoutTextEvent(
                    timeOffsetSec = 0,
                    message = "Warm into it.",
                    durationSec = null,
                ),
                WorkoutTextEvent(
                    timeOffsetSec = 30,
                    message = "Hold steady.",
                    durationSec = null,
                ),
            ),
            events,
        )
    }

    @Test
    fun ignoresBlankCanonicalMessages() {
        val workout = ImportedErgoWorkout(
            title = "Canonical builder",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.PowerSteady(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 120,
                    watts = 210,
                    canonicalMetadata = ImportedErgoWorkoutStepCanonicalMetadata(
                        messages = listOf(importedMessage(defaultText = "   ")),
                        origin = ImportedErgoWorkoutStepOrigin(
                            sourceSegmentId = "steady-1",
                            sourceSegmentLabel = null,
                            sourceSegmentNote = null,
                            enclosingRepeatSegmentId = null,
                            repeatIterationIndex = null,
                        ),
                    ),
                ),
            ),
            totalDurationSec = 120,
            canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                uid = null,
                revision = null,
                control = null,
                messages = listOf(importedMessage(defaultText = "")),
            ),
        )

        val events = ImportedErgoWorkoutTextEventMapper.map(workout)

        assertEquals(emptyList<WorkoutTextEvent>(), events)
    }

    @Test
    fun laterStepMessageOverridesWorkoutMessageThroughExistingResolverOrdering() {
        val workout = ImportedErgoWorkout(
            title = "Canonical builder",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.PowerSteady(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 120,
                    watts = 210,
                    canonicalMetadata = ImportedErgoWorkoutStepCanonicalMetadata(
                        messages = listOf(importedMessage(defaultText = "Settle in now.")),
                        origin = ImportedErgoWorkoutStepOrigin(
                            sourceSegmentId = "steady-1",
                            sourceSegmentLabel = null,
                            sourceSegmentNote = null,
                            enclosingRepeatSegmentId = null,
                            repeatIterationIndex = null,
                        ),
                    ),
                ),
            ),
            totalDurationSec = 120,
            canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                uid = null,
                revision = null,
                control = null,
                messages = listOf(importedMessage(defaultText = "Warm into it.")),
            ),
        )

        val mappedEvents = ImportedErgoWorkoutTextEventMapper.map(workout)

        assertEquals(
            "Settle in now.",
            resolveActiveWorkoutTextEvent(
                textEvents = mappedEvents,
                workoutElapsedSec = 0,
            )?.message,
        )
        assertNull(
            resolveActiveWorkoutTextEvent(
                textEvents = mappedEvents,
                workoutElapsedSec = DefaultWorkoutTextEventDurationSec,
            ),
        )
    }

    @Test
    fun rendersBestAvailableTranslationForPreferredLocaleAtPresentationTime() {
        val workout = ImportedErgoWorkout(
            title = "Canonical builder",
            description = null,
            steps = emptyList(),
            totalDurationSec = 0,
            canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                uid = null,
                revision = null,
                control = null,
                messages = listOf(
                    ImportedErgoWorkoutMessage(
                        kind = ImportedErgoWorkoutMessageKind.INTRO,
                        timing = ImportedErgoWorkoutMessageTiming(
                            anchor = ImportedErgoWorkoutMessageAnchor.START,
                            offsetSec = 0,
                        ),
                        text = ImportedErgoWorkoutLocalizedText(
                            defaultText = "Warm into it.",
                            translations = mapOf(
                                "fi" to "Kaynnistele rauhassa.",
                                "sv" to "Kom igang lugnt.",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val events = ImportedErgoWorkoutTextEventMapper.map(
            workout = workout,
            preferredLanguageTags = listOf("fi-FI"),
        )

        assertEquals("Kaynnistele rauhassa.", events.single().message)
    }

    @Test
    fun preservesCanonicalDisplayDurationWhenPresent() {
        val workout = ImportedErgoWorkout(
            title = "Canonical builder",
            description = null,
            steps = emptyList(),
            totalDurationSec = 300,
            canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                uid = "builder-duration",
                revision = 1,
                control = null,
                messages = listOf(importedMessage(defaultText = "Longer cue", durationSec = 14)),
            ),
        )

        val events = ImportedErgoWorkoutTextEventMapper.map(workout)

        assertEquals(
            listOf(
                WorkoutTextEvent(
                    timeOffsetSec = 0,
                    message = "Longer cue",
                    durationSec = 14,
                ),
            ),
            events,
        )
    }

    @Test
    fun mapsEndAnchoredMessagesAgainstWorkoutAndStepEnd() {
        val workout = ImportedErgoWorkout(
            title = "Canonical builder",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.FreeRide(
                    stepIndex = 0,
                    startOffsetSec = 30,
                    durationSec = 120,
                    canonicalMetadata = ImportedErgoWorkoutStepCanonicalMetadata(
                        messages = listOf(
                            ImportedErgoWorkoutMessage(
                                kind = ImportedErgoWorkoutMessageKind.INSTRUCTION,
                                timing = ImportedErgoWorkoutMessageTiming(
                                    anchor = ImportedErgoWorkoutMessageAnchor.END,
                                    offsetSec = -5,
                                ),
                                text = ImportedErgoWorkoutLocalizedText(
                                    defaultText = "Prepare to stop.",
                                    translations = emptyMap(),
                                ),
                            ),
                        ),
                        origin = ImportedErgoWorkoutStepOrigin(
                            sourceSegmentId = "spin-out",
                            sourceSegmentLabel = "Spin Out",
                            sourceSegmentNote = null,
                            enclosingRepeatSegmentId = null,
                            repeatIterationIndex = null,
                        ),
                    ),
                ),
            ),
            totalDurationSec = 150,
            canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                uid = "builder-1",
                revision = 1,
                control = null,
                messages = listOf(
                    ImportedErgoWorkoutMessage(
                        kind = ImportedErgoWorkoutMessageKind.INTRO,
                        timing = ImportedErgoWorkoutMessageTiming(
                            anchor = ImportedErgoWorkoutMessageAnchor.END,
                            offsetSec = -10,
                        ),
                        text = ImportedErgoWorkoutLocalizedText(
                            defaultText = "Ride ends soon.",
                            translations = emptyMap(),
                        ),
                    ),
                ),
            ),
        )

        val events = ImportedErgoWorkoutTextEventMapper.map(workout)

        assertEquals(
            listOf(
                WorkoutTextEvent(timeOffsetSec = 140, message = "Ride ends soon.", durationSec = null),
                WorkoutTextEvent(timeOffsetSec = 145, message = "Prepare to stop.", durationSec = null),
            ),
            events,
        )
    }

    private fun importedMessage(
        defaultText: String,
        durationSec: Int? = null,
    ): ImportedErgoWorkoutMessage {
        return ImportedErgoWorkoutMessage(
            kind = ImportedErgoWorkoutMessageKind.INSTRUCTION,
            timing = ImportedErgoWorkoutMessageTiming(
                anchor = ImportedErgoWorkoutMessageAnchor.START,
                offsetSec = 0,
                durationSec = durationSec,
            ),
            text = ImportedErgoWorkoutLocalizedText(
                defaultText = defaultText,
                translations = mapOf("fi" to "ignored"),
            ),
        )
    }
}
