package com.ewo.core

/**
 * Shared canonical tag policy for workout-root metadata tags.
 *
 * Tags stay intentionally machine-friendly so editors, filters, and future
 * interoperability tooling can treat them as stable classification slugs
 * rather than rider-facing free text.
 */
internal object EwoWorkoutTagPolicy {
    const val pattern = "^[a-z0-9_-]{1,32}$"
    const val maxCount = 10

    private val tagRegex = Regex(pattern)

    fun isValid(tag: String): Boolean = tagRegex.matches(tag)
}
