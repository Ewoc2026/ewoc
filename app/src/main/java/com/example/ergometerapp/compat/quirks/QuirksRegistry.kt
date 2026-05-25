package com.example.ergometerapp.compat.quirks

/**
 * Predicate rule that maps a [TrainerFingerprint] to a quirks profile.
 */
interface QuirksRule {
    val quirks: CompatibilityQuirks
    fun matches(fingerprint: TrainerFingerprint): Boolean
}

/**
 * Central quirks resolver for Compatibility Mode.
 *
 * V1 intentionally ships with a default profile and no device-specific rules.
 * New rules should be added only with reproducible diagnostics evidence.
 */
object QuirksRegistry {
    private const val DEFAULT_QUIRKS_ID = "default"
    private const val DEFAULT_QUIRKS_NOTES = "Default FTMS compatibility profile."

    private val rules: List<QuirksRule> = emptyList()

    fun resolve(fingerprint: TrainerFingerprint): CompatibilityQuirks {
        return rules.firstOrNull { rule -> rule.matches(fingerprint) }?.quirks
            ?: CompatibilityQuirks(
                id = DEFAULT_QUIRKS_ID,
                matchConfidence = MatchConfidence.HIGH,
                notes = DEFAULT_QUIRKS_NOTES,
            )
    }
}

