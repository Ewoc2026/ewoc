package com.example.ergometerapp.workout

import java.util.Locale

/**
 * Resolves preserved canonical message text against the current presentation locale.
 *
 * Canonical imports keep every authored translation so locale selection can stay
 * in the presentation layer. That keeps import output stable while still letting
 * the active device locale pick the best available translation at render time.
 */
object ImportedErgoWorkoutLocalizedTextResolver {
    /**
     * Returns the best available localized text for the current presentation context.
     *
     * Matching prefers exact BCP 47 tags first, then falls back to language-only
     * matches before finally returning the canonical default text.
     */
    fun resolve(
        text: ImportedErgoWorkoutLocalizedText,
        preferredLanguageTags: List<String>,
    ): String {
        if (text.translations.isEmpty()) return text.defaultText

        val normalizedTranslations = text.translations.entries.associate { entry ->
            normalizeLanguageTag(entry.key) to entry.value
        }
        val primaryPreferredTag = preferredLanguageTags
            .asSequence()
            .map(::normalizeLanguageTag)
            .firstOrNull { it.isNotEmpty() }
            ?: return text.defaultText

        normalizedTranslations[primaryPreferredTag]?.let { return it }

        val primaryLanguage = baseLanguage(primaryPreferredTag)
        normalizedTranslations[primaryLanguage]?.let { return it }

        normalizedTranslations.entries.firstOrNull { (translationTag, _) ->
            baseLanguage(translationTag) == primaryLanguage
        }?.value?.let { return it }

        return text.defaultText
    }

    private fun normalizeLanguageTag(languageTag: String): String {
        return languageTag
            .trim()
            .replace('_', '-')
            .lowercase(Locale.ROOT)
    }

    private fun baseLanguage(languageTag: String): String {
        return languageTag.substringBefore('-')
    }
}
