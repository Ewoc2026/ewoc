package com.example.ergometerapp.ui

/**
 * Stable UI automation tags used as a fallback when debug intents are not the
 * right fit for a validation step.
 *
 * These tags are intentionally limited to the setup/navigation controls that
 * are most expensive to reach through raw coordinate taps.
 */
internal object DebugAutomationTags {
    const val MENU_ROOT = "menu.root"
    const val MENU_STEP_PROFILE = "menu.step.profile"
    const val MENU_STEP_DEVICES = "menu.step.devices"
    const val MENU_STEP_FILE_BASED = "menu.step.file_based"
    const val MENU_STEP_SUMMARY = "menu.step.summary"
    const val MENU_FILE_SELECT_WORKOUT_FILE = "menu.file.select_workout_file"
    const val MENU_FILE_IMPORT_FROM_FOLDER = "menu.file.import_from_folder"
    const val MENU_FILE_LINK_FOLDER = "menu.file.link_folder"
    const val MENU_SUMMARY_START_SESSION = "menu.summary.start_session"
    const val SESSION_DEBUG_PROBE_OVERLAY = "session.debug_probe.overlay"
    const val SESSION_DEBUG_PROBE_READY = "session.debug_probe.ready"
    const val SESSION_DEBUG_PROBE_SMOOTH = "session.debug_probe.smooth"
    const val SESSION_DEBUG_PROBE_NOTICEABLE = "session.debug_probe.noticeable"
    const val SESSION_DEBUG_PROBE_UNSAFE = "session.debug_probe.unsafe"
    const val SESSION_DEBUG_PROBE_ABORT = "session.debug_probe.abort"
}
