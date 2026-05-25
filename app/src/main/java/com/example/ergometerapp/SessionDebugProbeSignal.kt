package com.example.ergometerapp

/**
 * User-to-debugger signal emitted from the session debug probe overlay.
 *
 * The signal names are intentionally generic so the same overlay can support
 * future live investigations without being hard-coded to one trainer safety
 * experiment.
 */
internal enum class SessionDebugProbeSignal(val wireName: String) {
    READY("ready"),
    SMOOTH("smooth"),
    NOTICEABLE("noticeable"),
    UNSAFE("unsafe"),
    ABORT("abort"),
}
