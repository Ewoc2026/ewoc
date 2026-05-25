package com.example.ergometerapp.ftms

/**
 * Parsed view of an FTMS Indoor Bike Data packet (UUID 0x2AD2).
 *
 * The FTMS spec marks many fields as optional via flags. Optional values are
 * represented as null when absent to preserve the original semantics rather
 * than substituting sentinel values.
 */
data class IndoorBikeData(
    val valid: Boolean,
    val instantaneousSpeedKmh: Double?,
    val averageSpeedKmh: Double?,
    val instantaneousCadenceRpm: Double?,
    val averageCadenceRpm: Double?,
    val totalDistanceMeters: Int?,
    val resistanceLevel: Int?,
    val instantaneousPowerW: Int?,
    val averagePowerW: Int?,
    val totalEnergyKcal: Int?,
    val energyPerHourKcal: Int?,
    val energyPerMinuteKcal: Int?,
    val heartRateBpm: Int?,
    val metabolicEquivalent: Double?,
    val elapsedTimeSeconds: Int?,
    val remainingTimeSeconds: Int?
)
