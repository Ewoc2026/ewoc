package com.example.ergometerapp.session.export

import com.example.ergometerapp.session.SessionSample
import com.example.ergometerapp.session.SessionSummary

/**
 * Immutable export payload that combines summary-level metrics with timeline samples.
 */
data class SessionExportSnapshot(
    val summary: SessionSummary,
    val timeline: List<SessionSample>,
)
