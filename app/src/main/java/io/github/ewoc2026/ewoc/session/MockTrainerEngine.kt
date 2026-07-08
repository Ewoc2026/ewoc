package io.github.ewoc2026.ewoc.session

import android.os.Handler
import android.os.Looper
import io.github.ewoc2026.ewoc.ftms.IndoorBikeData
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Emits deterministic synthetic FTMS telemetry for debug-only trainerless sessions.
 *
 * The engine intentionally keeps outputs stable and bounded so workout flow tests
 * remain predictable while still reflecting target-power changes from the runner.
 */
class MockTrainerEngine(
    private val tickIntervalMs: Long = 1_000L,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val nowElapsedMs: () -> Long = { System.currentTimeMillis() },
) {
    private val baselinePowerWatts = 120
    private val minTargetPowerWatts = 60
    private val maxTargetPowerWatts = 450
    private val minCadenceRpm = 55.0
    private val maxCadenceRpm = 110.0
    private val minSpeedKmh = 12.0
    private val maxSpeedKmh = 55.0
    private val joulesPerKcal = 4184.0

    private var running = false
    private var startedAtElapsedMs: Long = 0L
    private var lastTickElapsedMs: Long = 0L
    private var targetPowerWatts: Int? = null
    private var currentPowerWatts: Int = baselinePowerWatts
    private var totalDistanceMeters = 0.0
    private var totalEnergyKcal = 0.0
    private var telemetryCallback: ((IndoorBikeData) -> Unit)? = null
    private var debugScenario: MockTrainerDebugScenario? = null

    // These windows are tuned for human-paced screenshots while keeping mock validation quick.
    private val waitingStartHoldMs = 8_000L
    private val autoPauseWindowStartMs = 16_000L
    private val autoPauseWindowEndMs = 21_000L

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            emitTelemetry()
            handler.postDelayed(this, tickIntervalMs)
        }
    }

    fun start(onTelemetry: (IndoorBikeData) -> Unit) {
        stop()
        running = true
        telemetryCallback = onTelemetry
        startedAtElapsedMs = nowElapsedMs()
        lastTickElapsedMs = startedAtElapsedMs
        targetPowerWatts = null
        currentPowerWatts = baselinePowerWatts
        totalDistanceMeters = 0.0
        totalEnergyKcal = 0.0
        handler.post(tickRunnable)
    }

    fun stop() {
        running = false
        telemetryCallback = null
        handler.removeCallbacks(tickRunnable)
    }

    fun setTargetPowerWatts(targetWatts: Int?) {
        targetPowerWatts = targetWatts?.coerceIn(minTargetPowerWatts, maxTargetPowerWatts)
    }

    internal fun armDebugScenario(scenario: MockTrainerDebugScenario?) {
        debugScenario = scenario
    }

    private fun emitTelemetry() {
        val callback = telemetryCallback ?: return
        val now = nowElapsedMs()
        val elapsedMs = (now - startedAtElapsedMs).coerceAtLeast(0L)
        val deltaMs = (now - lastTickElapsedMs).coerceAtLeast(0L)
        val deltaSeconds = deltaMs / 1000.0
        lastTickElapsedMs = now

        val elapsedSec = (elapsedMs / 1000L).toInt()
        val effectiveTargetPower = targetPowerWatts ?: baselinePowerWatts
        val gap = effectiveTargetPower - currentPowerWatts
        val adaptiveStep = when {
            gap == 0 -> 0
            gap.absoluteValue <= 3 -> gap
            else -> (gap * 0.35).roundToInt().coerceAtLeast(1) * gap.sign()
        }
        currentPowerWatts = (currentPowerWatts + adaptiveStep).coerceIn(
            minTargetPowerWatts,
            maxTargetPowerWatts,
        )

        val cadenceWave = sin(elapsedSec / 8.0) * 2.5
        val naturalCadenceRpm = (55.0 + (currentPowerWatts * 0.17) + cadenceWave)
            .coerceIn(minCadenceRpm, maxCadenceRpm)
        val naturalSpeedKmh = (10.0 + (naturalCadenceRpm * 0.26) + (currentPowerWatts * 0.018))
            .coerceIn(minSpeedKmh, maxSpeedKmh)
        val override = debugOverrideFor(elapsedMs)
        val cadenceRpm = override?.cadenceRpm ?: naturalCadenceRpm
        val speedKmh = override?.speedKmh ?: naturalSpeedKmh
        val powerWatts = override?.powerWatts ?: currentPowerWatts
        totalDistanceMeters += speedKmh * 1000.0 * (deltaSeconds / 3600.0)
        totalEnergyKcal += (powerWatts * deltaSeconds) / joulesPerKcal

        callback(
            IndoorBikeData(
                valid = true,
                instantaneousSpeedKmh = speedKmh,
                averageSpeedKmh = null,
                instantaneousCadenceRpm = cadenceRpm,
                averageCadenceRpm = null,
                totalDistanceMeters = totalDistanceMeters.roundToInt(),
                resistanceLevel = null,
                instantaneousPowerW = powerWatts,
                averagePowerW = null,
                totalEnergyKcal = totalEnergyKcal.roundToInt(),
                energyPerHourKcal = null,
                energyPerMinuteKcal = null,
                heartRateBpm = null,
                metabolicEquivalent = null,
                elapsedTimeSeconds = elapsedSec,
                remainingTimeSeconds = null,
            ),
        )
    }

    private fun debugOverrideFor(elapsedMs: Long): TelemetryOverride? {
        return when (debugScenario) {
            MockTrainerDebugScenario.WAITING_START_AND_PAUSE_CAPTURE -> {
                when (elapsedMs) {
                    in 0L until waitingStartHoldMs -> TelemetryOverride(
                        cadenceRpm = 0.0,
                        speedKmh = 0.0,
                        powerWatts = 0,
                    )

                    in autoPauseWindowStartMs until autoPauseWindowEndMs -> TelemetryOverride(
                        cadenceRpm = 0.0,
                        speedKmh = 0.0,
                        powerWatts = 0,
                    )

                    else -> null
                }
            }

            null -> null
        }
    }

    private fun Int.sign(): Int {
        return when {
            this > 0 -> 1
            this < 0 -> -1
            else -> 0
        }
    }

    private data class TelemetryOverride(
        val cadenceRpm: Double,
        val speedKmh: Double,
        val powerWatts: Int,
    )
}
