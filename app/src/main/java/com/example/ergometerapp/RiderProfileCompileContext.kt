package com.example.ergometerapp

import com.ewo.core.EwoCompileContext
import kotlin.math.roundToInt

/**
 * Builds canonical workout compile context from the persisted rider profile.
 *
 * Canonical `.ewo` files intentionally stay free of rider-specific FTP and HR profile values, so
 * import and preview must resolve those from app settings when available.
 */
internal fun riderProfileCompileContext(
    ftpWatts: Int?,
    hrProfileAge: Int?,
    hrProfileSex: HrProfileSex?,
): EwoCompileContext = EwoCompileContext(
    ftpWatts = ftpWatts,
    hrMaxBpm = derivedHrMaxBpm(hrProfileAge, hrProfileSex),
)

/**
 * Returns the app's derived HR max when the rider profile contains enough information.
 */
internal fun derivedHrMaxBpm(
    hrProfileAge: Int?,
    hrProfileSex: HrProfileSex?,
): Int? = hrProfileAge?.let { estimatedMaxHeartRate(age = it, sex = hrProfileSex) }

/**
 * Uses the same estimated max-HR model for session zones and HR-relative workout resolution.
 */
internal fun estimatedMaxHeartRate(age: Int, sex: HrProfileSex?): Int {
    val clampedAge = age.coerceIn(13, 100)
    val max = when (sex) {
        HrProfileSex.MALE -> 208.0 - (0.7 * clampedAge)
        HrProfileSex.FEMALE -> 206.0 - (0.88 * clampedAge)
        // When sex is not provided, use a neutral midpoint estimate and flag in UI.
        null -> 207.0 - (0.79 * clampedAge)
    }
    return max.roundToInt().coerceIn(120, 220)
}
