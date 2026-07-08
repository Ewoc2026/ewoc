package io.github.ewoc2026.ewoc.compat.quirks

import io.github.ewoc2026.ewoc.compat.CompatibilityV1Constants

/**
 * Indicates confidence of a quirks-rule match for diagnostics visibility.
 */
enum class MatchConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * Cleanup strategy used by Compatibility Mode after active test steps.
 */
sealed class StopStrategy {
    data object StopOpcodeThenDisconnect : StopStrategy()
    data object SetWattsThenDisconnect : StopStrategy()
    data object DisconnectOnly : StopStrategy()
}

/**
 * Resolved per-trainer policy knobs for Compatibility Mode v1.
 *
 * The default profile keeps behavior deterministic and low-risk; device-
 * specific overrides can be added later through registry rules.
 */
data class CompatibilityQuirks(
    val id: String,
    val matchConfidence: MatchConfidence,
    val notes: String,
    val stopStrategy: StopStrategy = StopStrategy.StopOpcodeThenDisconnect,
    val fallbackPowerWatts: Int? = CompatibilityV1Constants.STOP_FALLBACK_TARGET_WATTS,
    val clampWattsMin: Int = CompatibilityV1Constants.STEP_MIN_WATTS,
    val clampWattsMax: Int = CompatibilityV1Constants.STEP_MAX_WATTS,
    val cpAckTimeoutMs: Long = CompatibilityV1Constants.CP_ACK_TIMEOUT_MS,
    val requestControlTimeoutMs: Long = CompatibilityV1Constants.REQUEST_CONTROL_TIMEOUT_MS,
    val connectTimeoutMs: Long = CompatibilityV1Constants.CONNECT_TIMEOUT_MS,
    val precheckTelemetryMinPackets: Int = CompatibilityV1Constants.PRECHECK_TELEMETRY_MIN_PACKETS,
    val precheckTelemetryWindowMs: Long = CompatibilityV1Constants.PRECHECK_TELEMETRY_WINDOW_MS,
    val telemetryStallWindowMs: Long = CompatibilityV1Constants.TELEMETRY_STALL_WINDOW_MS,
    val enableResetOptional: Boolean = true,
    val maxReconnectRetries: Int = CompatibilityV1Constants.CONNECT_RETRY_COUNT,
)

