package io.github.ewoc2026.ewoc.session.release

/**
 * Concrete safety-ramp instructions for one disconnect-sensitive release path.
 */
internal data class ReleaseRampPlan(
    val startTargetPowerW: Int,
    val endTargetPowerW: Int,
    val durationMs: Long,
    val floorHoldMs: Long = 0L,
    val tickMs: Long,
    val stepRoundToWatts: Int = 5,
)
