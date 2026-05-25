package com.example.ergometerapp.session

/**
 * Aggregated metrics for a completed session.
 *
 * Values are nullable when the corresponding signals were absent or too sparse
 * to compute a stable statistic (e.g., no HR samples).
 */
data class SessionSummary(
    val startTimestampMillis: Long,
    val stopTimestampMillis: Long,
    val durationSeconds: Int,
    val elapsedDurationSeconds: Int = durationSeconds,
    val actualTss: Double?,
    val avgPower: Int?,
    val maxPower: Int?,
    val avgCadence: Int?,
    val maxCadence: Int?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val distanceMeters: Int?,
    val totalEnergyKcal: Int?,
)
