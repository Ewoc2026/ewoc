package io.github.ewoc2026.ewoc.ai

/**
 * Wearable data usage context for freshness policy evaluation.
 */
enum class AiWearableUseCase {
    PRE_RIDE,
    POST_RIDE,
}

/**
 * Normalized wearable availability and freshness state.
 */
enum class AiWearableDataState {
    FRESH,
    STALE,
    MISSING,
}

/**
 * Wearable freshness policy thresholds.
 */
data class AiWearableFreshnessConfig(
    val preRideMaxAgeMillis: Long = 24 * 60 * 60 * 1000L,
    val postRideMaxAgeMillis: Long = 12 * 60 * 60 * 1000L,
) {
    init {
        require(preRideMaxAgeMillis > 0) { "preRideMaxAgeMillis must be > 0" }
        require(postRideMaxAgeMillis > 0) { "postRideMaxAgeMillis must be > 0" }
    }
}

/**
 * Result object used by rule engine and policy layers.
 *
 * [confidenceOverride] is intentionally conservative for stale/missing data.
 */
data class AiWearableNormalizationResult(
    val snapshot: AiWearableSnapshot?,
    val state: AiWearableDataState,
    val confidenceOverride: AiQualityClass,
    val reasonCode: String,
)

/**
 * Applies freshness rules to wearable snapshots before rule consumption.
 */
class AiWearableNormalizer(
    private val config: AiWearableFreshnessConfig = AiWearableFreshnessConfig(),
) {

    /**
     * Evaluates wearable snapshot freshness for a specific use case.
     */
    fun normalize(
        snapshot: AiWearableSnapshot?,
        nowMillis: Long,
        useCase: AiWearableUseCase,
    ): AiWearableNormalizationResult {
        if (snapshot == null) {
            return AiWearableNormalizationResult(
                snapshot = null,
                state = AiWearableDataState.MISSING,
                confidenceOverride = AiQualityClass.LOW,
                reasonCode = "wearable.missing_snapshot",
            )
        }

        val maxAge = when (useCase) {
            AiWearableUseCase.PRE_RIDE -> config.preRideMaxAgeMillis
            AiWearableUseCase.POST_RIDE -> config.postRideMaxAgeMillis
        }
        val ageMillis = nowMillis - snapshot.syncedAtMillis
        if (ageMillis > maxAge) {
            return AiWearableNormalizationResult(
                snapshot = snapshot,
                state = AiWearableDataState.STALE,
                confidenceOverride = AiQualityClass.LOW,
                reasonCode = "wearable.stale_snapshot",
            )
        }

        return AiWearableNormalizationResult(
            snapshot = snapshot,
            state = AiWearableDataState.FRESH,
            confidenceOverride = AiQualityClass.HIGH,
            reasonCode = "wearable.fresh_snapshot",
        )
    }
}
