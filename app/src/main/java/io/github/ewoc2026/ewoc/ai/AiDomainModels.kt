package io.github.ewoc2026.ewoc.ai

/**
 * App phase where AI recommendations are evaluated and rendered.
 */
enum class AiPhase {
    MENU,
    SESSION,
    SUMMARY,
}

/**
 * Recommendation category used for prioritization and cooldown control.
 */
enum class AiRecommendationType {
    PACING,
    CADENCE,
    READINESS,
    RECOVERY,
    SAFETY,
    CONNECTIVITY,
}

/**
 * Shared confidence scale for quality and recommendation confidence.
 *
 * Using one scale avoids mismatched interpretation between rule engine and UI.
 */
enum class AiQualityClass {
    HIGH,
    MEDIUM,
    LOW,
}

/**
 * Reachability state used for trainer and optional HR source visibility.
 */
enum class AiReachability {
    REACHABLE,
    UNREACHABLE,
    UNKNOWN,
}

/**
 * Direction marker used for trend-only wearable signals.
 */
enum class AiTrendMarker {
    UP,
    DOWN,
    STABLE,
    UNKNOWN,
}

/**
 * Priority class used when multiple recommendations compete in one phase.
 */
enum class AiRecommendationPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
}

/**
 * Metadata for signal capture age and freshness checks.
 *
 * [captureTimestampMillis] marks when the source snapshot was taken and
 * [maxExpectedAgeMillis] defines the age boundary where freshness should be
 * considered degraded.
 */
data class AiSignalMeta(
    val captureTimestampMillis: Long,
    val maxExpectedAgeMillis: Long,
)

/**
 * Live ride metrics consumed by rule evaluation.
 *
 * Fields are nullable because the app may run without HR and because telemetry
 * can be temporarily missing during scan or reconnect transitions.
 */
data class AiLiveMetrics(
    val actualPowerWatts: Int? = null,
    val targetPowerWatts: Int? = null,
    val targetCadenceRpm: Int? = null,
    val cadenceRpm: Int? = null,
    val speedKmh: Double? = null,
    val heartRateBpm: Int? = null,
    val workoutElapsedSec: Int? = null,
    val workoutPaused: Boolean = false,
)

/**
 * Connectivity quality flags that influence confidence and suppression logic.
 */
data class AiConnectivityQuality(
    val trainerReachability: AiReachability = AiReachability.UNKNOWN,
    val hrReachability: AiReachability = AiReachability.UNKNOWN,
    val trainerSignalStale: Boolean = false,
    val hrSignalStale: Boolean = false,
)

/**
 * Optional wearable snapshot used for pre/post readiness and recovery context.
 *
 * The snapshot is intentionally normalized so the rule engine does not depend
 * on provider-specific SDK model types.
 */
data class AiWearableSnapshot(
    val sourceId: String,
    val syncedAtMillis: Long,
    val sleepDurationMinutes: Int? = null,
    val sleepQualityScore: Double? = null,
    val readinessScore: Double? = null,
    val recoveryScore: Double? = null,
    val hrvTrend: AiTrendMarker = AiTrendMarker.UNKNOWN,
    val restingHrTrend: AiTrendMarker = AiTrendMarker.UNKNOWN,
    val strainScore: Double? = null,
)

/**
 * Workout identifiers and load metadata needed by pre/post recommendation rules.
 */
data class AiWorkoutContext(
    val workoutId: String? = null,
    val workoutName: String? = null,
    val plannedTss: Double? = null,
)

/**
 * Aggregated historical load features used to evaluate readiness trends.
 */
data class AiHistoryAggregates(
    val recentActualTss7d: Double? = null,
    val recentActualTss28d: Double? = null,
    val recentSessionCount7d: Int? = null,
)

/**
 * Context fields that stay stable longer than per-second live telemetry.
 *
 * Session completion and last-session TSS are included here because post-ride
 * rules consume them as already-aggregated values instead of raw timeline data.
 */
data class AiContext(
    val configuredFtpWatts: Int? = null,
    val workout: AiWorkoutContext = AiWorkoutContext(),
    val history: AiHistoryAggregates = AiHistoryAggregates(),
    val lastSessionActualTss: Double? = null,
    val lastSessionCompletionRatio: Double? = null,
    val mockTrainerModeActive: Boolean = false,
)

/**
 * Complete AI input snapshot for one evaluation cycle.
 *
 * Defaults are intentionally conservative so missing optional fields never
 * block evaluation or force non-null placeholders.
 */
data class AiInputSnapshot(
    val phase: AiPhase,
    val signalMeta: AiSignalMeta,
    val liveMetrics: AiLiveMetrics = AiLiveMetrics(),
    val connectivityQuality: AiConnectivityQuality = AiConnectivityQuality(),
    val context: AiContext = AiContext(),
    val wearableSnapshot: AiWearableSnapshot? = null,
)

/**
 * Template payload rendered by UI or post-processed by optional formatter.
 *
 * Arguments are stringly-typed on purpose to keep payload transport and debug
 * output stable without binding to Android-specific resource objects.
 */
data class AiRecommendationPayload(
    val templateKey: String,
    val templateArgs: Map<String, String> = emptyMap(),
)

/**
 * Rule-engine output candidate before final policy and rate-limit decisions.
 */
data class AiRecommendationCandidate(
    val phase: AiPhase,
    val type: AiRecommendationType,
    val priority: AiRecommendationPriority,
    val confidence: AiQualityClass,
    val rationaleKeys: List<String> = emptyList(),
    val payload: AiRecommendationPayload,
)
