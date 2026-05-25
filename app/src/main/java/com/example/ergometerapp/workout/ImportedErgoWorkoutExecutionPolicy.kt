package com.example.ergometerapp.workout

import kotlin.math.roundToInt

/**
 * Temporary execution contract for imported `.ewo` handoff models.
 *
 * Canonical control metadata is preserved at import time so future runtime
 * slices can consume it without widening the parser boundary again. The current
 * runner must still treat that control block as import-only context until heart
 * rate supervision semantics are implemented explicitly.
 */
object ImportedErgoWorkoutExecutionPolicy {
    /**
     * Reports how workout-level canonical control metadata participates in the
     * current execution slice.
     */
    fun canonicalControlUsage(workout: ImportedErgoWorkout): ImportedErgoWorkoutCanonicalControlUsage {
        return if (workout.canonicalMetadata?.control != null) {
            ImportedErgoWorkoutCanonicalControlUsage.PRESERVED_IMPORT_ONLY
        } else {
            ImportedErgoWorkoutCanonicalControlUsage.NONE
        }
    }

    /**
     * Returns a stable mapper failure when the imported step requires runtime
     * control semantics that the current runner intentionally does not provide.
     */
    fun unsupportedStepError(
        workout: ImportedErgoWorkout,
        step: ImportedErgoWorkoutStep,
    ): MappingError? {
        return when (step) {
            is ImportedErgoWorkoutStep.PowerSteady -> null
            is ImportedErgoWorkoutStep.PowerRamp -> null
            is ImportedErgoWorkoutStep.FreeRide -> null
            is ImportedErgoWorkoutStep.HeartRateSteady -> {
                val policyResolution = resolveHeartRateExecutionPolicy(workout, step)
                MappingError(
                    code = MappingErrorCode.UNSUPPORTED_HEART_RATE_TARGET,
                    message = heartRateUnsupportedMessage(workout, policyResolution),
                    stepIndex = step.stepIndex,
                    stepType = "HeartRateSteady",
                )
            }
        }
    }

    /**
     * Resolves the explicit v1 runtime policy contract for a preserved imported
     * HR step without enabling runner support yet.
     *
     * The current imported-step handoff keeps `hrUpperCapBpm` only at the
     * workout level, so canonical workout metadata is still required before the
     * runner can receive a complete policy object.
     */
    fun resolveHeartRateExecutionPolicy(
        workout: ImportedErgoWorkout,
        step: ImportedErgoWorkoutStep.HeartRateSteady,
    ): ImportedHrExecutionPolicyResolution {
        val hrUpperCapBpm = workout.canonicalMetadata?.control?.hrUpperCapBpm
            ?: return ImportedHrExecutionPolicyResolution.MissingCanonicalControl

        return ImportedHrExecutionPolicyResolution.Available(
            ImportedHrExecutionPolicyV1(
                targetLowBpm = step.lowBpm,
                targetHighBpm = step.highBpm,
                initialPowerWatts = step.initialPowerWatts,
                minPowerWatts = step.minPowerWatts,
                maxPowerWatts = step.maxPowerWatts,
                signalLossPowerWatts = step.signalLossPowerWatts,
                hrUpperCapBpm = hrUpperCapBpm,
                requiredCapabilities = setOf(
                    ImportedHrExecutionCapability.HEART_RATE_SIGNAL,
                    ImportedHrExecutionCapability.TRAINER_CONTROL,
                ),
                unavailableAtStartBehavior = HrUnavailableAtStartBehavior.FAIL_START,
                signalLossBehavior = HrSignalLossBehavior.FALLBACK_THEN_STOP,
                capBehavior = HrCapBehavior.THROTTLE_THEN_STOP,
                unreachableTargetBehavior = HrUnreachableTargetBehavior.HOLD_AT_BOUND_WITH_STATUS,
            ),
        )
    }

    /**
     * Evaluates whether the current runtime snapshot satisfies the v1 start
     * requirements of an imported HR step.
     *
     * This keeps capability checks aligned with the explicit policy object even
     * while the runner still blocks HR execution entirely.
     */
    fun evaluateHeartRateStartCapabilities(
        workout: ImportedErgoWorkout,
        step: ImportedErgoWorkoutStep.HeartRateSteady,
        snapshot: ImportedHrExecutionCapabilitySnapshot,
    ): ImportedHrExecutionStartCapabilityEvaluation {
        return when (val resolution = resolveHeartRateExecutionPolicy(workout, step)) {
            ImportedHrExecutionPolicyResolution.MissingCanonicalControl -> {
                ImportedHrExecutionStartCapabilityEvaluation.MissingCanonicalControl
            }

            is ImportedHrExecutionPolicyResolution.Available -> {
                val missingCapabilities = resolution.policy.requiredCapabilities.filterTo(
                    linkedSetOf(),
                ) { capability ->
                    !snapshot.has(capability)
                }
                ImportedHrExecutionStartCapabilityEvaluation.PolicyAvailable(
                    policy = resolution.policy,
                    missingCapabilities = missingCapabilities,
                )
            }
        }
    }

    private fun heartRateUnsupportedMessage(
        workout: ImportedErgoWorkout,
        policyResolution: ImportedHrExecutionPolicyResolution,
    ): String {
        return when (policyResolution) {
            ImportedHrExecutionPolicyResolution.MissingCanonicalControl -> when (canonicalControlUsage(workout)) {
                ImportedErgoWorkoutCanonicalControlUsage.NONE ->
                "Heart-rate-controlled imported steps are not executable yet."

                ImportedErgoWorkoutCanonicalControlUsage.PRESERVED_IMPORT_ONLY ->
                    "Heart-rate-controlled imported steps are preserved at import time, but canonical HR control remains import-only until a dedicated runtime policy ships."
            }

            is ImportedHrExecutionPolicyResolution.Available ->
                "Heart-rate-controlled imported steps already resolve to a v1 runtime policy object, but runner support is not enabled yet."
        }
    }
}

internal const val IMPORTED_HR_SAFETY_MAX_POWER_WATTS = 100

private const val IMPORTED_HR_SIGNAL_LOSS_FALLBACK_FRACTION = 0.50
private const val IMPORTED_HR_CAP_THROTTLE_FRACTION = 0.40

/**
 * Current execution usage of workout-level canonical control metadata.
 */
enum class ImportedErgoWorkoutCanonicalControlUsage {
    NONE,
    PRESERVED_IMPORT_ONLY,
}

/**
 * Explicit runner-facing v1 policy derived from imported canonical HR metadata.
 *
 * The mapper can expose this contract now so future runner and editor work can
 * share the same semantics without reinterpreting raw import metadata.
 */
data class ImportedHrExecutionPolicyV1(
    val targetLowBpm: Int,
    val targetHighBpm: Int,
    val initialPowerWatts: Int,
    val minPowerWatts: Int,
    val maxPowerWatts: Int,
    /**
     * Preserved import-time absolute fallback target.
     *
     * The runtime currently keeps this for compatibility with existing workout
     * metadata, but session safety behavior now derives fallback power
     * relative to [initialPowerWatts] and clamps it to
     * [IMPORTED_HR_SAFETY_MAX_POWER_WATTS].
     */
    val signalLossPowerWatts: Int,
    val hrUpperCapBpm: Int,
    val requiredCapabilities: Set<ImportedHrExecutionCapability>,
    val unavailableAtStartBehavior: HrUnavailableAtStartBehavior,
    val signalLossBehavior: HrSignalLossBehavior,
    val capBehavior: HrCapBehavior,
    val unreachableTargetBehavior: HrUnreachableTargetBehavior,
)

/**
 * Resolves the session safety fallback target relative to the authored start
 * power so different riders do not inherit the same absolute fallback watts.
 */
fun ImportedHrExecutionPolicyV1.resolvedSignalLossPowerWatts(): Int {
    return resolvedRelativeSafetyPower(
        fraction = IMPORTED_HR_SIGNAL_LOSS_FALLBACK_FRACTION,
        legacyAbsoluteWatts = signalLossPowerWatts,
    )
}

/**
 * Resolves the hard-cap throttle target from the authored start power and the
 * single code-owned safety max watt ceiling.
 */
fun ImportedHrExecutionPolicyV1.resolvedSafetyCapThrottlePowerWatts(): Int {
    return resolvedRelativeSafetyPower(
        fraction = IMPORTED_HR_CAP_THROTTLE_FRACTION,
        legacyAbsoluteWatts = minPowerWatts,
    )
}

private fun ImportedHrExecutionPolicyV1.resolvedRelativeSafetyPower(
    fraction: Double,
    legacyAbsoluteWatts: Int,
): Int {
    val safetyUpperBound = minOf(maxPowerWatts, IMPORTED_HR_SAFETY_MAX_POWER_WATTS)
    if (safetyUpperBound <= 0) {
        return legacyAbsoluteWatts.coerceAtLeast(1)
    }
    val relativeWatts = (initialPowerWatts.toDouble() * fraction).roundToInt()
    return relativeWatts.coerceAtLeast(1).coerceAtMost(safetyUpperBound)
}

/**
 * Availability of the imported HR policy object for the current step.
 */
sealed interface ImportedHrExecutionPolicyResolution {
    data object MissingCanonicalControl : ImportedHrExecutionPolicyResolution

    data class Available(
        val policy: ImportedHrExecutionPolicyV1,
    ) : ImportedHrExecutionPolicyResolution
}

/**
 * Runtime capabilities required before an imported HR step may start.
 */
enum class ImportedHrExecutionCapability {
    HEART_RATE_SIGNAL,
    TRAINER_CONTROL,
}

/**
 * Current runtime capability snapshot used for imported HR start checks.
 */
data class ImportedHrExecutionCapabilitySnapshot(
    val hasHeartRateSignal: Boolean,
    val hasTrainerControl: Boolean,
) {
    /**
     * Returns whether [capability] is currently available.
     */
    fun has(capability: ImportedHrExecutionCapability): Boolean {
        return when (capability) {
            ImportedHrExecutionCapability.HEART_RATE_SIGNAL -> hasHeartRateSignal
            ImportedHrExecutionCapability.TRAINER_CONTROL -> hasTrainerControl
        }
    }
}

/**
 * Capability-evaluation result for an imported HR step at segment start.
 */
sealed interface ImportedHrExecutionStartCapabilityEvaluation {
    data object MissingCanonicalControl : ImportedHrExecutionStartCapabilityEvaluation

    data class PolicyAvailable(
        val policy: ImportedHrExecutionPolicyV1,
        val missingCapabilities: Set<ImportedHrExecutionCapability>,
    ) : ImportedHrExecutionStartCapabilityEvaluation {
        val isReadyAtStart: Boolean
            get() = missingCapabilities.isEmpty()
    }
}

/**
 * Behavior when HR is unavailable at segment start.
 */
enum class HrUnavailableAtStartBehavior {
    FAIL_START,
}

/**
 * Behavior after HR signal loss during a segment.
 */
enum class HrSignalLossBehavior {
    FALLBACK_THEN_STOP,
}

/**
 * Behavior after breaching the hard HR safety cap.
 */
enum class HrCapBehavior {
    THROTTLE_THEN_STOP,
}

/**
 * Behavior when the rider cannot reach the target while clamped to a bound.
 */
enum class HrUnreachableTargetBehavior {
    HOLD_AT_BOUND_WITH_STATUS,
}
