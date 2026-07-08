package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.ftms.IndoorBikeData

data class SessionState(
    val bike: IndoorBikeData?,
    val heartRateBpm: Int?,            // chest strap
    val effectiveHeartRateBpm: Int?,   // merged
    val logicalDistanceMeters: Int?,
    val logicalTotalEnergyKcal: Int?,
    val timestampMillis: Long,
    val durationSeconds: Int,
)
