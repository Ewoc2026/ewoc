package com.example.ergometerapp.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportedErgoWorkoutLocalizedTextResolverTest {
    @Test
    fun prefersExactLocaleMatchBeforeLanguageFallback() {
        val text = ImportedErgoWorkoutLocalizedText(
            defaultText = "Settle in.",
            translations = linkedMapOf(
                "fi" to "Asetu rytmiin.",
                "fi-FI" to "Asetu suomalaiseen rytmiin.",
            ),
        )

        val resolved = ImportedErgoWorkoutLocalizedTextResolver.resolve(
            text = text,
            preferredLanguageTags = listOf("fi-FI"),
        )

        assertEquals("Asetu suomalaiseen rytmiin.", resolved)
    }

    @Test
    fun fallsBackToLanguageMatchWhenRegionSpecificTranslationIsMissing() {
        val text = ImportedErgoWorkoutLocalizedText(
            defaultText = "Settle in.",
            translations = mapOf(
                "fi" to "Asetu rytmiin.",
            ),
        )

        val resolved = ImportedErgoWorkoutLocalizedTextResolver.resolve(
            text = text,
            preferredLanguageTags = listOf("fi-FI"),
        )

        assertEquals("Asetu rytmiin.", resolved)
    }

    @Test
    fun fallsBackToCanonicalDefaultWhenNoPreferredTranslationExists() {
        val text = ImportedErgoWorkoutLocalizedText(
            defaultText = "Settle in.",
            translations = mapOf(
                "fi" to "Asetu rytmiin.",
            ),
        )

        val resolved = ImportedErgoWorkoutLocalizedTextResolver.resolve(
            text = text,
            preferredLanguageTags = listOf("sv-SE"),
        )

        assertEquals("Settle in.", resolved)
    }

    @Test
    fun prefersCanonicalDefaultOverSecondaryLocaleFallbackWhenPrimaryLocaleMisses() {
        val text = ImportedErgoWorkoutLocalizedText(
            defaultText = "Settle in.",
            translations = mapOf(
                "fi" to "Asetu rytmiin.",
            ),
        )

        val resolved = ImportedErgoWorkoutLocalizedTextResolver.resolve(
            text = text,
            preferredLanguageTags = listOf("en-US", "fi-FI"),
        )

        assertEquals("Settle in.", resolved)
    }
}
