package com.example.ergometerapp.logging

import android.util.Log
import com.example.ergometerapp.BuildConfig

/**
 * Centralized application logging policy.
 *
 * Telemetry and diagnostics logs are intentionally gated by
 * [BuildConfig.VERBOSE_TELEMETRY_LOGGING] so release builds do not emit
 * high-cardinality runtime payloads into shared logs.
 */
internal object AppLog {

    /**
     * Emits an info-level telemetry log only when verbose telemetry logging is enabled.
     */
    fun telemetryInfo(tag: String, message: () -> String) {
        if (!BuildConfig.VERBOSE_TELEMETRY_LOGGING) return
        Log.i(tag, message())
    }

    /**
     * Emits a debug-level telemetry log only when verbose telemetry logging is enabled.
     */
    fun telemetryDebug(tag: String, message: () -> String) {
        if (!BuildConfig.VERBOSE_TELEMETRY_LOGGING) return
        Log.d(tag, message())
    }

    /**
     * Emits stable user-visible validation markers so log review can anchor on
     * operator actions before diving into lower-level BLE telemetry.
     */
    fun testMarker(event: String, context: Map<String, String> = emptyMap()) {
        telemetryInfo("TEST_MARKER") {
            val suffix = if (context.isEmpty()) {
                ""
            } else {
                context.entries.joinToString(
                    separator = " ",
                    prefix = " ",
                ) { (key, value) -> "$key=$value" }
            }
            "event=$event$suffix"
        }
    }
}
