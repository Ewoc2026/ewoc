package com.example.ergometerapp

/**
 * Shared source of truth for the currently selected session setup mode.
 *
 * This lives outside the UI package so setup rendering, session-start gating,
 * orchestration, and summary/session fallbacks can all read one stable mode
 * without duplicating enum definitions or parallel boolean flags.
 */
enum class SessionSetupMode {
    FILE,
    EDITOR,
    TELEMETRY_ONLY,
}
