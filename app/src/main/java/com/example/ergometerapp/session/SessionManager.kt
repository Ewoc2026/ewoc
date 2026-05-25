package com.example.ergometerapp.session

import android.util.Log
import com.example.ergometerapp.SessionState
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.session.export.SessionExportSnapshot
import java.util.concurrent.Executor
import java.util.concurrent.Executors

enum class SessionPhase {
    IDLE,
    RUNNING,
    STOPPED
}

/**
 * Coordinates a single workout session lifecycle.
 *
 * This class is intentionally UI-agnostic and BLE-agnostic; it only consumes
 * parsed FTMS/HR signals and produces a [SessionSummary].
 */
class SessionManager(
    private val context: android.content.Context,
    private val onStateUpdated: (SessionState) -> Unit,
    private val onTimelineSampleRecorded: (List<SessionSample>) -> Unit = {},
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val persistSummary: (android.content.Context, SessionSummary) -> Unit = { ctx, summary ->
        SessionStorage.save(ctx, summary)
    },
    private val summaryPersistenceExecutor: Executor = SUMMARY_PERSISTENCE_EXECUTOR,
    timelineRetentionSeconds: Int = DEFAULT_TIMELINE_RETENTION_SECONDS,
    private val liveTimelineWindowSeconds: Int = DEFAULT_LIVE_TIMELINE_WINDOW_SECONDS,
) {
    companion object {
        private const val DEFAULT_TIMELINE_RETENTION_SECONDS = 60 * 60
        internal const val DEFAULT_LIVE_TIMELINE_WINDOW_SECONDS = 600

        private val SUMMARY_PERSISTENCE_EXECUTOR: Executor by lazy {
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "SessionSummaryPersist").apply { isDaemon = true }
            }
        }
    }

    var lastSummary: SessionSummary? = null
        private set
    private val powerStats = TimeWeightedIntAccumulator()
    private val cadenceStats = TimeWeightedIntAccumulator()
    private val heartRateStats = TimeWeightedIntAccumulator()
    private var actualTssAccumulator: ActualTssAccumulator? = null
    private var latestBikeData: IndoorBikeData? = null
    private var latestHeartRate: Int? = null
    private var sessionStartMillis: Long? = null
    private var activeSegmentStartMillis: Long? = null
    private var accumulatedActiveMillis: Long = 0L
    private val maxTimelineSamples = timelineRetentionSeconds.coerceAtLeast(1)
    private val timelineSamples = ArrayDeque<SessionSample>()
    private var lastSampleSecond: Long? = null

    private var durationAtStopSec: Int? = null
    private var elapsedDurationAtStopSec: Int? = null

    private var lastDistanceMeters: Int? = null
    private var lastTotalEnergyKcal: Int? = null
    private var cumulativeDistanceOffsetMeters: Int = 0
    private var cumulativeEnergyOffsetKcal: Int = 0
    private var pendingBridgeDistanceMeters: Int? = null
    private var pendingBridgeTotalEnergyKcal: Int? = null
    private var continuationCarryoverSummary: SessionSummary? = null
    private var continuationCarryoverTimeline: List<SessionSample> = emptyList()
    private var suppressPersistenceForNextStop = false
    private var continuationStartPending = false

    private var sessionPhase: SessionPhase = SessionPhase.IDLE
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Memory-stable running aggregate for integer telemetry samples.
     *
     * The most recent sample is held until a newer sample timestamp arrives or
     * the session stops. This keeps averages stable across notification rates.
     */
    private class TimeWeightedIntAccumulator {
        private var weightedDurationSum: Long = 0
        private var totalDurationMillis: Long = 0
        private var max: Int? = null
        private var lastSample: Int? = null
        private var lastTimestampMillis: Long? = null

        fun add(sample: Int, timestampMillis: Long) {
            flushUntil(timestampMillis)
            lastSample = sample
            lastTimestampMillis = maxOf(lastTimestampMillis ?: timestampMillis, timestampMillis)
            val currentMax = max
            max = if (currentMax == null) sample else maxOf(currentMax, sample)
        }

        fun reset() {
            weightedDurationSum = 0
            totalDurationMillis = 0
            max = null
            lastSample = null
            lastTimestampMillis = null
        }

        fun flushUntil(timestampMillis: Long) {
            val previousTimestampMillis = lastTimestampMillis ?: return
            val previousSample = lastSample ?: return
            if (timestampMillis <= previousTimestampMillis) return

            val deltaMillis = timestampMillis - previousTimestampMillis
            weightedDurationSum += previousSample.toLong() * deltaMillis
            totalDurationMillis += deltaMillis
            lastTimestampMillis = timestampMillis
        }

        fun averageOrNull(): Int? {
            if (totalDurationMillis <= 0L) return null
            return (weightedDurationSum / totalDurationMillis).toInt()
        }

        fun maxOrNull(): Int? = max

        fun pause(timestampMillis: Long) {
            flushUntil(timestampMillis)
            lastSample = null
            lastTimestampMillis = null
        }

        fun finish(timestampMillis: Long) {
            flushUntil(timestampMillis)
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /**
     * Current session phase used by UI for state rendering and controls.
     */
    fun getPhase(): SessionPhase = sessionPhase

    fun updateBikeData(bikeData: IndoorBikeData) {
        runOnMainThread {
            latestBikeData = bikeData
            maybeApplyPendingCumulativeTrainerMetricBridge(bikeData)
            if (sessionPhase == SessionPhase.RUNNING && isSessionActivityActive()) {
                val timestampMillis = nowMillis()
                // Only valid FTMS frames with power values contribute to summary stats.
                latestBikeData
                    ?.takeIf { it.valid && it.instantaneousPowerW != null }
                    ?.instantaneousPowerW
                    ?.let { powerWatts ->
                        powerStats.add(powerWatts, timestampMillis)
                        actualTssAccumulator?.recordPower(
                            powerWatts = powerWatts,
                            timestampMillis = timestampMillis,
                        )
                    }

                // Trainer counters can restart across transport/session restarts.
                // Persisting an explicit offset keeps the logical session totals monotonic.
                latestBikeData?.totalDistanceMeters
                    ?.let { lastDistanceMeters = it + cumulativeDistanceOffsetMeters }
                latestBikeData?.totalEnergyKcal
                    ?.let { lastTotalEnergyKcal = it + cumulativeEnergyOffsetKcal }

                // Prefer external HR strap if present; fall back to bike HR otherwise.
                // This avoids mixing two sensors with different latencies.
                if (latestHeartRate == null) {
                    latestBikeData?.heartRateBpm
                        ?.takeIf { it in 30..220 }
                        ?.let { heartRateStats.add(it, timestampMillis) }
                }
                latestBikeData?.instantaneousCadenceRpm
                    ?.let { cadenceStats.add(it.toInt(), timestampMillis) }
                recordSampleIfDue(timestampMillis)
            }
            emitState()
        }
    }

    /**
     * Updates heart rate from an external sensor.
     *
     * When present, it supersedes any HR embedded in FTMS bike data to keep
     * the data source consistent for statistics.
     */
    fun updateHeartRate(hr: Int?) {
        runOnMainThread {
            latestHeartRate = hr
            if (sessionPhase == SessionPhase.RUNNING && isSessionActivityActive()) {
                val timestampMillis = nowMillis()
                hr?.takeIf { it in 30..220 }?.let { heartRateStats.add(it, timestampMillis) }
                recordSampleIfDue(timestampMillis)
            }

            emitState()
        }
    }


    private fun emitState() {

        val durationSec =
            when (sessionPhase) {
                SessionPhase.RUNNING -> activeDurationSecondsAt(nowMillis())
                SessionPhase.STOPPED -> durationAtStopSec ?: 0
                else -> 0
            }

        // Effective HR is exposed for UI even when only bike HR is available.
        val effectiveHr =
            latestHeartRate
                ?: latestBikeData?.heartRateBpm
                    ?.takeIf { it in 30..220 }

        val state = SessionState(
            bike = latestBikeData,
            heartRateBpm = latestHeartRate,
            effectiveHeartRateBpm = effectiveHr,
            logicalDistanceMeters = lastDistanceMeters,
            logicalTotalEnergyKcal = lastTotalEnergyKcal,
            timestampMillis = nowMillis(),
            durationSeconds = durationSec,
        )

        onStateUpdated(state)
    }

    /**
     * Starts a new session and clears any prior aggregates.
     */
    fun startSession(ftpWatts: Int, startActive: Boolean = true) {
        startSessionInternal(
            ftpWatts = ftpWatts,
            startActive = startActive,
            preserveContinuationCarryover = continuationStartPending,
        )
    }

    /**
     * Starts a fresh trainer/session segment while preserving the current logical ride carryover.
     *
     * This is used when the app has to restart trainer transport/session semantics to restore
     * rider-owned control, while still intending to export one combined ride summary afterwards.
     */
    fun startContinuationSegment(ftpWatts: Int, startActive: Boolean = true) {
        startSessionInternal(
            ftpWatts = ftpWatts,
            startActive = startActive,
            preserveContinuationCarryover = true,
        )
    }

    /**
     * Marks the next [startSession] call as a continuation segment instead of a fresh ride.
     */
    fun armNextStartAsContinuationSegment() {
        runOnMainThread {
            continuationStartPending = true
        }
    }

    /**
     * Arms the next stop as an intermediate continuation checkpoint instead of a final export.
     *
     * The stop still produces [lastSummary], but persistence is deferred so the eventual combined
     * summary can replace the temporary segment result.
     */
    fun armContinuationCheckpoint() {
        runOnMainThread {
            suppressPersistenceForNextStop = true
        }
    }

    /**
     * Promotes the current stopped segment into continuation carryover for the next segment.
     *
     * The next [startContinuationSegment] can then restart trainer transport while preserving
     * summary/export continuity across the hard cutover.
     */
    fun promoteStoppedSessionToContinuationCarryover() {
        runOnMainThread {
            if (sessionPhase != SessionPhase.STOPPED) return@runOnMainThread
            val stoppedSummary = lastSummary ?: return@runOnMainThread
            continuationCarryoverSummary = stoppedSummary
            continuationCarryoverTimeline = buildCombinedExportTimeline()
        }
    }

    /**
     * Bridges trainer-owned cumulative counters across a transport reset.
     *
     * Some trainers restart distance/energy counters when a fresh FTMS session is
     * opened. The caller supplies the logical totals collected so far, and the next
     * raw trainer values are added on top instead of restarting the session summary.
     */
    fun bridgeCumulativeTrainerMetrics(
        distanceMeters: Int?,
        totalEnergyKcal: Int?,
    ) {
        runOnMainThread {
            pendingBridgeDistanceMeters = distanceMeters
            pendingBridgeTotalEnergyKcal = totalEnergyKcal
        }
    }

    /**
     * Returns the latest logical cumulative trainer totals captured for this session.
     */
    fun currentCumulativeTrainerMetrics(): CumulativeTrainerMetrics {
        return CumulativeTrainerMetrics(
            distanceMeters = lastDistanceMeters,
            totalEnergyKcal = lastTotalEnergyKcal,
        )
    }

    /**
     * Clears all session/runtime aggregates without persisting a summary.
     *
     * This is reserved for debug recovery paths that need a deterministic clean
     * slate after aborted flows or manual validation dead ends.
     */
    fun resetToIdle() {
        runOnMainThread {
            powerStats.reset()
            cadenceStats.reset()
            heartRateStats.reset()
            actualTssAccumulator = null
            latestBikeData = null
            latestHeartRate = null
            sessionStartMillis = null
            activeSegmentStartMillis = null
            accumulatedActiveMillis = 0L
            durationAtStopSec = null
            elapsedDurationAtStopSec = null
            lastDistanceMeters = null
            lastTotalEnergyKcal = null
            cumulativeDistanceOffsetMeters = 0
            cumulativeEnergyOffsetKcal = 0
            pendingBridgeDistanceMeters = null
            pendingBridgeTotalEnergyKcal = null
            continuationCarryoverSummary = null
            continuationCarryoverTimeline = emptyList()
            suppressPersistenceForNextStop = false
            continuationStartPending = false
            lastSummary = null
            sessionPhase = SessionPhase.IDLE
            timelineSamples.clear()
            lastSampleSecond = null
            onTimelineSampleRecorded(emptyList())
            emitState()
        }
    }

    /**
     * Excludes subsequent wall-clock time from summary duration and aggregates
     * until [resumeSessionActivity] is called.
     */
    fun pauseSessionActivity() {
        runOnMainThread {
            if (sessionPhase != SessionPhase.RUNNING) return@runOnMainThread
            val activeStart = activeSegmentStartMillis ?: return@runOnMainThread
            val timestampMillis = nowMillis()
            powerStats.pause(timestampMillis)
            cadenceStats.pause(timestampMillis)
            heartRateStats.pause(timestampMillis)
            actualTssAccumulator?.pauseAt(timestampMillis)
            accumulatedActiveMillis += (timestampMillis - activeStart).coerceAtLeast(0L)
            activeSegmentStartMillis = null
            emitState()
        }
    }

    /**
     * Re-enables active-duration and aggregate collection after a pause or recovery gap.
     */
    fun resumeSessionActivity() {
        runOnMainThread {
            if (sessionPhase != SessionPhase.RUNNING) return@runOnMainThread
            if (activeSegmentStartMillis != null) return@runOnMainThread
            activeSegmentStartMillis = nowMillis()
            emitState()
        }
    }

    /**
     * Stops the session and finalizes summary statistics.
     *
     * Uses simple averages over collected samples; the caller is responsible for
     * choosing sampling frequency elsewhere.
     */
    fun stopSession() {
        runOnMainThread {
            if (sessionPhase != SessionPhase.RUNNING) return@runOnMainThread

            val start = sessionStartMillis ?: return@runOnMainThread
            val stopTimestampMillis = nowMillis()
            val durationSec = activeDurationSecondsAt(stopTimestampMillis)
            val elapsedDurationSec = elapsedDurationSecondsAt(stopTimestampMillis)
            powerStats.finish(stopTimestampMillis)
            cadenceStats.finish(stopTimestampMillis)
            heartRateStats.finish(stopTimestampMillis)
            recordSampleIfDue(stopTimestampMillis)
            accumulatedActiveMillis = activeDurationMillisAt(stopTimestampMillis)
            activeSegmentStartMillis = null

            durationAtStopSec = durationSec
            elapsedDurationAtStopSec = elapsedDurationSec

            val avgPower = powerStats.averageOrNull()
            val maxPower = powerStats.maxOrNull()
            val avgCadence = cadenceStats.averageOrNull()
            val maxCadence = cadenceStats.maxOrNull()
            val avgHeartRate = heartRateStats.averageOrNull()
            val maxHeartRate = heartRateStats.maxOrNull()
            val actualTss = actualTssAccumulator?.calculateTss(
                durationSeconds = durationSec,
                stopTimestampMillis = stopTimestampMillis,
            )

            val rawSummary = SessionSummary(
                startTimestampMillis = start,
                stopTimestampMillis = stopTimestampMillis,
                durationSeconds = durationSec,
                elapsedDurationSeconds = elapsedDurationSec,
                actualTss = actualTss,
                avgPower = avgPower,
                maxPower = maxPower,
                avgCadence = avgCadence,
                maxCadence = maxCadence,
                avgHeartRate = avgHeartRate,
                maxHeartRate = maxHeartRate,
                distanceMeters = lastDistanceMeters,
                totalEnergyKcal = lastTotalEnergyKcal,
            )
            val summary = continuationCarryoverSummary
                ?.let { mergeContinuationSummary(it, rawSummary) }
                ?: rawSummary

            lastSummary = summary
            sessionPhase = SessionPhase.STOPPED
            emitState()

            if (!suppressPersistenceForNextStop) {
                queueSummaryPersistence(summary)
            }
            suppressPersistenceForNextStop = false
        }
    }

    /**
     * Returns a stable snapshot of collected timeline samples for export use.
     */
    fun exportTimelineSnapshot(): List<SessionSample> {
        return timelineSamples.toList()
    }

    /**
     * Builds a single immutable export snapshot after session completion.
     */
    fun buildExportSnapshot(): SessionExportSnapshot? {
        val summary = lastSummary ?: return null
        return SessionExportSnapshot(
            summary = summary,
            timeline = buildCombinedExportTimeline(),
        )
    }

    /**
     * Persists summary off the main thread so stop-flow UI transitions remain smooth.
     *
     * Invariant: summary publication to UI ([lastSummary], phase, emitted state) is
     * completed before persistence is queued.
     */
    private fun queueSummaryPersistence(summary: SessionSummary) {
        summaryPersistenceExecutor.execute {
            try {
                persistSummary(context, summary)
            } catch (t: Throwable) {
                Log.w("SESSION", "Summary persistence failed: ${t.message}")
            }
        }
    }

    private fun startSessionInternal(
        ftpWatts: Int,
        startActive: Boolean,
        preserveContinuationCarryover: Boolean,
    ) {
        runOnMainThread {
            val preservedBridgeDistanceMeters = pendingBridgeDistanceMeters
            val preservedBridgeTotalEnergyKcal = pendingBridgeTotalEnergyKcal
            sessionPhase = SessionPhase.RUNNING
            sessionStartMillis = nowMillis()
            activeSegmentStartMillis = sessionStartMillis.takeIf { startActive }
            accumulatedActiveMillis = 0L

            powerStats.reset()
            cadenceStats.reset()
            heartRateStats.reset()
            actualTssAccumulator = ActualTssAccumulator(ftpWatts = ftpWatts)
            lastDistanceMeters = preservedBridgeDistanceMeters.takeIf { preserveContinuationCarryover }
            lastTotalEnergyKcal = preservedBridgeTotalEnergyKcal.takeIf { preserveContinuationCarryover }
            cumulativeDistanceOffsetMeters = 0
            cumulativeEnergyOffsetKcal = 0
            pendingBridgeDistanceMeters = preservedBridgeDistanceMeters.takeIf { preserveContinuationCarryover }
            pendingBridgeTotalEnergyKcal = preservedBridgeTotalEnergyKcal.takeIf { preserveContinuationCarryover }
            if (!preserveContinuationCarryover) {
                continuationCarryoverSummary = null
                continuationCarryoverTimeline = emptyList()
            }
            continuationStartPending = false
            suppressPersistenceForNextStop = false
            lastSummary = null
            latestBikeData = null
            latestHeartRate = null
            durationAtStopSec = null
            elapsedDurationAtStopSec = null
            timelineSamples.clear()
            lastSampleSecond = null
            onTimelineSampleRecorded(emptyList())
            emitState()
        }
    }

    private fun buildCombinedExportTimeline(): List<SessionSample> {
        if (continuationCarryoverTimeline.isEmpty()) {
            return exportTimelineSnapshot()
        }
        return (continuationCarryoverTimeline + exportTimelineSnapshot())
            .sortedBy { it.timestampMillis }
    }

    private fun mergeContinuationSummary(
        carryover: SessionSummary,
        resumed: SessionSummary,
    ): SessionSummary {
        return SessionSummary(
            startTimestampMillis = carryover.startTimestampMillis,
            stopTimestampMillis = resumed.stopTimestampMillis,
            durationSeconds = carryover.durationSeconds + resumed.durationSeconds,
            elapsedDurationSeconds = (
                (resumed.stopTimestampMillis - carryover.startTimestampMillis)
                    .coerceAtLeast(0L) / 1000L
                ).toInt(),
            actualTss = sumNullableDoubles(carryover.actualTss, resumed.actualTss),
            avgPower = weightedAverage(
                carryover.avgPower,
                carryover.durationSeconds,
                resumed.avgPower,
                resumed.durationSeconds,
            ),
            maxPower = maxNullable(carryover.maxPower, resumed.maxPower),
            avgCadence = weightedAverage(
                carryover.avgCadence,
                carryover.durationSeconds,
                resumed.avgCadence,
                resumed.durationSeconds,
            ),
            maxCadence = maxNullable(carryover.maxCadence, resumed.maxCadence),
            avgHeartRate = weightedAverage(
                carryover.avgHeartRate,
                carryover.durationSeconds,
                resumed.avgHeartRate,
                resumed.durationSeconds,
            ),
            maxHeartRate = maxNullable(carryover.maxHeartRate, resumed.maxHeartRate),
            distanceMeters = resumed.distanceMeters ?: carryover.distanceMeters,
            totalEnergyKcal = resumed.totalEnergyKcal ?: carryover.totalEnergyKcal,
        )
    }

    private fun weightedAverage(
        firstValue: Int?,
        firstDurationSeconds: Int,
        secondValue: Int?,
        secondDurationSeconds: Int,
    ): Int? {
        val weightedTotal = mutableListOf<Long>()
        val weights = mutableListOf<Long>()
        if (firstValue != null && firstDurationSeconds > 0) {
            weightedTotal += firstValue.toLong() * firstDurationSeconds.toLong()
            weights += firstDurationSeconds.toLong()
        }
        if (secondValue != null && secondDurationSeconds > 0) {
            weightedTotal += secondValue.toLong() * secondDurationSeconds.toLong()
            weights += secondDurationSeconds.toLong()
        }
        val totalWeight = weights.sum()
        if (totalWeight <= 0L) return null
        return (weightedTotal.sum() / totalWeight).toInt()
    }

    private fun maxNullable(first: Int?, second: Int?): Int? {
        return when {
            first == null -> second
            second == null -> first
            else -> maxOf(first, second)
        }
    }

    private fun sumNullableDoubles(first: Double?, second: Double?): Double? {
        return when {
            first == null -> second
            second == null -> first
            else -> first + second
        }
    }

    private fun isSessionActivityActive(): Boolean {
        return activeSegmentStartMillis != null
    }

    private fun activeDurationSecondsAt(timestampMillis: Long): Int {
        return (activeDurationMillisAt(timestampMillis) / 1000L).toInt()
    }

    private fun elapsedDurationSecondsAt(timestampMillis: Long): Int {
        val start = sessionStartMillis ?: return 0
        return ((timestampMillis - start).coerceAtLeast(0L) / 1000L).toInt()
    }

    private fun activeDurationMillisAt(timestampMillis: Long): Long {
        val activeStart = activeSegmentStartMillis
        val inFlightActiveMillis = if (activeStart != null) {
            (timestampMillis - activeStart).coerceAtLeast(0L)
        } else {
            0L
        }
        return (accumulatedActiveMillis + inFlightActiveMillis).coerceAtLeast(0L)
    }

    private fun recordSampleIfDue(timestampMillis: Long) {
        if (sessionPhase != SessionPhase.RUNNING || !isSessionActivityActive()) return
        val sampleSecond = timestampMillis / 1000L
        val previousSecond = lastSampleSecond
        if (previousSecond != null && sampleSecond <= previousSecond) return

        val bikeData = latestBikeData
        val resolvedHeartRate = latestHeartRate ?: bikeData?.heartRateBpm?.takeIf { it in 30..220 }
        timelineSamples.addLast(
            SessionSample(
            timestampMillis = timestampMillis,
            powerWatts = bikeData?.instantaneousPowerW,
            cadenceRpm = bikeData?.instantaneousCadenceRpm?.toInt(),
            heartRateBpm = resolvedHeartRate,
            distanceMeters = lastDistanceMeters,
            totalEnergyKcal = lastTotalEnergyKcal,
            )
        )
        while (timelineSamples.size > maxTimelineSamples) {
            timelineSamples.removeFirst()
        }
        lastSampleSecond = sampleSecond
        publishLiveTimelineWindow()
    }

    private fun maybeApplyPendingCumulativeTrainerMetricBridge(bikeData: IndoorBikeData) {
        val preservedDistanceMeters = pendingBridgeDistanceMeters
        val preservedTotalEnergyKcal = pendingBridgeTotalEnergyKcal
        if (preservedDistanceMeters == null && preservedTotalEnergyKcal == null) {
            return
        }

        val rawDistanceMeters = bikeData.totalDistanceMeters
        val rawTotalEnergyKcal = bikeData.totalEnergyKcal
        if (rawDistanceMeters == null && rawTotalEnergyKcal == null) {
            return
        }

        val shouldBridge =
            (preservedDistanceMeters != null &&
                rawDistanceMeters != null &&
                rawDistanceMeters < preservedDistanceMeters) ||
                (preservedTotalEnergyKcal != null &&
                    rawTotalEnergyKcal != null &&
                    rawTotalEnergyKcal < preservedTotalEnergyKcal)

        if (shouldBridge) {
            cumulativeDistanceOffsetMeters = preservedDistanceMeters ?: 0
            cumulativeEnergyOffsetKcal = preservedTotalEnergyKcal ?: 0
            lastDistanceMeters = preservedDistanceMeters ?: lastDistanceMeters
            lastTotalEnergyKcal = preservedTotalEnergyKcal ?: lastTotalEnergyKcal
        }

        pendingBridgeDistanceMeters = null
        pendingBridgeTotalEnergyKcal = null
    }

    private fun publishLiveTimelineWindow() {
        val windowSize = liveTimelineWindowSeconds.coerceAtLeast(1)
        val snapshot = if (timelineSamples.size <= windowSize) {
            timelineSamples.toList()
        } else {
            timelineSamples.toList().takeLast(windowSize)
        }
        onTimelineSampleRecorded(snapshot)
    }

    /**
     * Snapshot of the current logical trainer cumulative totals tracked by the session.
     */
    data class CumulativeTrainerMetrics(
        val distanceMeters: Int?,
        val totalEnergyKcal: Int?,
    )
}
