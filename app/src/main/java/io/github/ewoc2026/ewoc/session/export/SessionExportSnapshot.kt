package io.github.ewoc2026.ewoc.session.export

import io.github.ewoc2026.ewoc.session.SessionSample
import io.github.ewoc2026.ewoc.session.SessionSummary

/**
 * Immutable export payload that combines summary-level metrics with timeline samples.
 */
data class SessionExportSnapshot(
    val summary: SessionSummary,
    val timeline: List<SessionSample>,
)
