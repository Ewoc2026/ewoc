package com.example.ergometerapp.session

/**
 * One timestamped telemetry sample captured during an active session.
 *
 * Samples are intentionally sparse and normalized to at most one sample per
 * wall-clock second to keep export payloads predictable and memory usage stable.
 */
data class SessionSample(
    val timestampMillis: Long,
    val powerWatts: Int?,
    val cadenceRpm: Int?,
    val heartRateBpm: Int?,
    val distanceMeters: Int?,
    val totalEnergyKcal: Int?,
)
