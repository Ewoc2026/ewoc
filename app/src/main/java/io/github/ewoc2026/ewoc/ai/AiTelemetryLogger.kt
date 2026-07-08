package io.github.ewoc2026.ewoc.ai

/**
 * Normalized quality flags included in every telemetry event payload.
 */
data class AiTelemetryQualityFlags(
    val trainerReachability: AiReachability,
    val hrReachability: AiReachability,
    val trainerSignalStale: Boolean,
    val hrSignalStale: Boolean,
    val hasWearableSnapshot: Boolean,
    val wearableSnapshotStale: Boolean,
)

/**
 * Structured AI telemetry events for emitted and suppressed recommendations.
 */
sealed interface AiTelemetryEvent {
    data class RecommendationEmitted(
        val timestampMillis: Long,
        val phase: AiPhase,
        val type: AiRecommendationType,
        val confidence: AiQualityClass,
        val templateKey: String,
        val qualityFlags: AiTelemetryQualityFlags,
    ) : AiTelemetryEvent

    data class RecommendationSuppressed(
        val timestampMillis: Long,
        val phase: AiPhase,
        val type: AiRecommendationType,
        val confidence: AiQualityClass,
        val suppressionStage: String,
        val suppressionReasonCode: String,
        val templateKey: String,
        val qualityFlags: AiTelemetryQualityFlags,
    ) : AiTelemetryEvent
}

/**
 * In-memory ring buffer for AI telemetry diagnostics.
 */
object AiTelemetryBuffer {
    private const val capacity = 500
    private val events = ArrayDeque<AiTelemetryEvent>(capacity)

    @Synchronized
    fun record(event: AiTelemetryEvent) {
        if (events.size >= capacity) {
            events.removeFirst()
        }
        events.addLast(event)
    }

    @Synchronized
    fun snapshot(): List<AiTelemetryEvent> {
        return events.toList()
    }

    @Synchronized
    fun clear() {
        events.clear()
    }
}

/**
 * Telemetry configuration for staleness interpretation.
 */
data class AiTelemetryConfig(
    val wearableFreshnessMaxAgeMillis: Long = 18 * 60 * 60 * 1000L,
) {
    init {
        require(wearableFreshnessMaxAgeMillis > 0) {
            "wearableFreshnessMaxAgeMillis must be > 0"
        }
    }
}

/**
 * Logs AI lifecycle outcomes with one structured event per emitted/suppressed item.
 */
class AiTelemetryLogger(
    private val config: AiTelemetryConfig = AiTelemetryConfig(),
    private val recordEvent: (AiTelemetryEvent) -> Unit = { event ->
        AiTelemetryBuffer.record(event)
    },
) {

    /**
     * Logs emitted and suppressed recommendation outcomes for one evaluation cycle.
     */
    fun logCycle(
        snapshot: AiInputSnapshot,
        decision: AiPresentationDecision,
        timestampMillis: Long,
    ) {
        val qualityFlags = qualityFlags(snapshot, timestampMillis)

        decision.messages.forEach { message ->
            recordEvent(
                AiTelemetryEvent.RecommendationEmitted(
                    timestampMillis = timestampMillis,
                    phase = message.phase,
                    type = message.type,
                    confidence = message.confidence,
                    templateKey = message.templateKey,
                    qualityFlags = qualityFlags,
                ),
            )
        }

        decision.policySuppressed.forEach { suppressed ->
            recordEvent(
                AiTelemetryEvent.RecommendationSuppressed(
                    timestampMillis = timestampMillis,
                    phase = suppressed.candidate.phase,
                    type = suppressed.candidate.type,
                    confidence = suppressed.candidate.confidence,
                    suppressionStage = "policy",
                    suppressionReasonCode = suppressed.reason.name,
                    templateKey = suppressed.candidate.payload.templateKey,
                    qualityFlags = qualityFlags,
                ),
            )
        }

        decision.adapterSuppressed.forEach { suppressed ->
            recordEvent(
                AiTelemetryEvent.RecommendationSuppressed(
                    timestampMillis = timestampMillis,
                    phase = suppressed.candidate.phase,
                    type = suppressed.candidate.type,
                    confidence = suppressed.candidate.confidence,
                    suppressionStage = "presentation",
                    suppressionReasonCode = suppressed.reason.name,
                    templateKey = suppressed.candidate.payload.templateKey,
                    qualityFlags = qualityFlags,
                ),
            )
        }
    }

    private fun qualityFlags(
        snapshot: AiInputSnapshot,
        nowMillis: Long,
    ): AiTelemetryQualityFlags {
        val wearable = snapshot.wearableSnapshot
        val wearableStale = wearable != null &&
            nowMillis - wearable.syncedAtMillis > config.wearableFreshnessMaxAgeMillis
        return AiTelemetryQualityFlags(
            trainerReachability = snapshot.connectivityQuality.trainerReachability,
            hrReachability = snapshot.connectivityQuality.hrReachability,
            trainerSignalStale = snapshot.connectivityQuality.trainerSignalStale,
            hrSignalStale = snapshot.connectivityQuality.hrSignalStale,
            hasWearableSnapshot = wearable != null,
            wearableSnapshotStale = wearableStale,
        )
    }
}
