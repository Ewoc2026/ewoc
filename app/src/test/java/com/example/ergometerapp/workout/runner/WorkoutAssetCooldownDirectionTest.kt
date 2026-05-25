package com.example.ergometerapp.workout.runner

import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutImportResult
import com.example.ergometerapp.workout.WorkoutImportService
import com.example.ergometerapp.workout.WorkoutFile
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutAssetCooldownDirectionTest {
    private val importService = WorkoutImportService()
    private val ftpWatts = 200

    @Test
    fun workoutTestAssetCooldownUsesPowerHighToPowerLowSemantics() {
        val workout = loadWorkoutFromAssetFixture()
        val timeline = runSecondBySecond(WorkoutStepper(workout, ftpWatts = ftpWatts))

        var secondCursor = 0
        var validatedCooldownCount = 0

        workout.steps.forEachIndexed { stepIndex, step ->
            if (step is Step.Cooldown) {
                val durationSec = step.durationSec ?: 0
                if (durationSec > 1 && step.powerHigh != null && step.powerLow != null) {
                    val firstSecond = timeline.getOrNull(secondCursor)
                    val secondSecond = timeline.getOrNull(secondCursor + 1)
                    requireNotNull(firstSecond) {
                        "Expected first cooldown target at step $stepIndex second $secondCursor"
                    }
                    requireNotNull(secondSecond) {
                        "Expected second cooldown target at step $stepIndex second ${secondCursor + 1}"
                    }

                    val expectedAtStart = ratioToWatts(step.powerHigh)
                    val expectedAtSecond = interpolatedWatts(
                        start = step.powerHigh,
                        end = step.powerLow,
                        durationSec = durationSec,
                        elapsedSec = 1,
                    )
                    assertEquals(
                        "Cooldown start must use PowerHigh at step $stepIndex.",
                        expectedAtStart,
                        firstSecond,
                    )
                    assertEquals(
                        "Cooldown second target must move toward PowerLow at step $stepIndex.",
                        expectedAtSecond,
                        secondSecond,
                    )

                    val delta = step.powerLow - step.powerHigh
                    if (delta < 0.0) {
                        assertTrue(
                            "Cooldown should descend when PowerHigh > PowerLow at step $stepIndex.",
                            secondSecond < firstSecond,
                        )
                    } else if (delta > 0.0) {
                        assertTrue(
                            "Cooldown should rise when PowerLow > PowerHigh at step $stepIndex.",
                            secondSecond > firstSecond,
                        )
                    } else {
                        assertEquals(
                            "Cooldown should stay flat when PowerHigh == PowerLow at step $stepIndex.",
                            firstSecond,
                            secondSecond,
                        )
                    }
                    validatedCooldownCount += 1
                }
            }
            secondCursor += stepDurationSec(step)
        }

        assertTrue(
            "No cooldown steps with sufficient data were validated from Workout_Test.xml.",
            validatedCooldownCount > 0,
        )
    }

    private fun loadWorkoutFromAssetFixture(): WorkoutFile {
        val xml = readWorkoutAssetXml()
        val result = importService.importFromText("Workout_Test.xml", xml)
        val success = result as? WorkoutImportResult.Success
            ?: throw AssertionError("Expected Workout_Test.xml import success, got $result")
        return requireNotNull(success.workoutFile) {
            "Expected Workout_Test.xml to import as a ZWO workout payload."
        }
    }

    private fun readWorkoutAssetXml(): String {
        val candidates = listOf(
            File("app/src/main/assets/Workout_Test.xml"),
            File("../app/src/main/assets/Workout_Test.xml"),
            File("../../app/src/main/assets/Workout_Test.xml"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError(
                "Fixture app/src/main/assets/Workout_Test.xml not found. Looked in: " +
                    candidates.joinToString { it.path },
            )
        return file.readText()
    }

    private fun runSecondBySecond(stepper: WorkoutStepper): List<Int?> {
        val targets = mutableListOf<Int?>()
        var nowMs = 0L
        val maxSeconds = 24 * 60 * 60
        stepper.start()

        while (targets.size < maxSeconds) {
            val output = stepper.tick(nowMs)
            if (output.done) {
                return targets
            }
            targets += output.targetPowerWatts
            nowMs += 1000L
        }
        throw AssertionError("Dry-run exceeded guard limit.")
    }

    private fun stepDurationSec(step: Step): Int {
        return when (step) {
            is Step.Warmup -> step.durationSec ?: 0
            is Step.Cooldown -> step.durationSec ?: 0
            is Step.SteadyState -> step.durationSec ?: 0
            is Step.Ramp -> step.durationSec ?: 0
            is Step.FreeRide -> step.durationSec ?: 0
            is Step.IntervalsT -> {
                val repeat = step.repeat ?: 0
                val on = step.onDurationSec ?: 0
                val off = step.offDurationSec ?: 0
                if (repeat <= 0 || on <= 0 || off <= 0) 0 else repeat * (on + off)
            }
            is Step.Unknown -> 0
        }
    }

    private fun ratioToWatts(ratio: Double): Int {
        return (ratio * ftpWatts).roundToInt()
    }

    private fun interpolatedWatts(
        start: Double,
        end: Double,
        durationSec: Int,
        elapsedSec: Int,
    ): Int {
        val t = elapsedSec.toDouble() / durationSec.toDouble()
        return ratioToWatts(start + (end - start) * t)
    }
}
