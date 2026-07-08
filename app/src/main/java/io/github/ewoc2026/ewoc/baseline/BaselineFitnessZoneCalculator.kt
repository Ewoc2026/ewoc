package io.github.ewoc2026.ewoc.baseline

import kotlin.math.roundToInt

/**
 * Derives display-only power zones from the active FTP.
 *
 * V1 does not persist zone tables. These ranges use the common Coggan-style FTP bands so the result
 * screen can render a stable summary before the project grows a dedicated training-profile model.
 */
internal object BaselineFitnessZoneCalculator {
    private val zoneDefinitions = listOf(
        ZoneDefinition("Z1", "Recovery", 0.00, 0.55),
        ZoneDefinition("Z2", "Endurance", 0.56, 0.75),
        ZoneDefinition("Z3", "Tempo", 0.76, 0.90),
        ZoneDefinition("Z4", "Threshold", 0.91, 1.05),
        ZoneDefinition("Z5", "VO2 Max", 1.06, 1.20),
        ZoneDefinition("Z6", "Anaerobic", 1.21, 1.50),
        ZoneDefinition("Z7", "Neuromuscular", 1.51, null),
    )

    fun calculate(ftpWatts: Int): List<BaselineFitnessPowerZone> {
        val safeFtpWatts = ftpWatts.coerceAtLeast(1)
        return zoneDefinitions.map { definition ->
            BaselineFitnessPowerZone(
                code = definition.code,
                label = definition.label,
                minFractionFtpInclusive = definition.minFractionFtpInclusive,
                maxFractionFtpInclusive = definition.maxFractionFtpInclusive,
                minWatts = (safeFtpWatts * definition.minFractionFtpInclusive).roundToInt(),
                maxWattsInclusive = definition.maxFractionFtpInclusive?.let { fraction ->
                    (safeFtpWatts * fraction).roundToInt()
                },
            )
        }
    }

    private data class ZoneDefinition(
        val code: String,
        val label: String,
        val minFractionFtpInclusive: Double,
        val maxFractionFtpInclusive: Double?,
    )
}
