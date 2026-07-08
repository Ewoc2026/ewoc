package io.github.ewoc2026.ewoc.session

/**
 * Latest terminal outcome for one externally initiated FTMS Request Control attempt.
 *
 * This stays separate from session start/stop flow state so non-workout features can reuse the
 * FTMS control stack without inheriting the session screen's recovery behavior.
 */
internal enum class ExternalTrainerControlRequestOutcome {
    GRANTED,
    FAILED,
}
