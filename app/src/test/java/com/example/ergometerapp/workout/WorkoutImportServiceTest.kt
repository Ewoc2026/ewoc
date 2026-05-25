package com.example.ergometerapp.workout

import com.ewo.core.EwoCompileContext
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutImportServiceTest {
    private val service = WorkoutImportService()

    @Test
    fun importsZwoByFileExtension() {
        val xml = """
            <workout_file>
              <name>Test</name>
              <workout>
                <SteadyState Duration="60" Power="0.75" Cadence="90"/>
              </workout>
            </workout_file>
        """.trimIndent()

        val result = service.importFromText("test.zwo", xml)

        val success = requireSuccess(result)
        val workoutFile = requireWorkoutFile(success)
        assertEquals(WorkoutImportFormat.ZWO_XML, success.format)
        assertEquals(1, workoutFile.steps.size)
        assertTrue(workoutFile.steps.first() is Step.SteadyState)
    }

    @Test
    fun importsXmlByContentSniffingWithoutExtension() {
        val xml = """
            <workout_file>
              <TargetHeartrate>148</TargetHeartrate>
              <workout>
                <Ramp Duration="120" PowerLow="0.5" PowerHigh="0.8" />
              </workout>
            </workout_file>
        """.trimIndent()

        val result = service.importFromText("workout_data", xml)

        val success = requireSuccess(result)
        val workoutFile = requireWorkoutFile(success)
        assertEquals(WorkoutImportFormat.ZWO_XML, success.format)
        assertEquals(1, workoutFile.steps.size)
        assertTrue(workoutFile.steps.first() is Step.Ramp)
        assertEquals(148, workoutFile.heartRateTargets?.targetHeartRateBpm)
    }

    @Test
    fun importsXmlByContentSniffingWithHeartRateTagVariants() {
        val xml = """
            <workout_file>
              <target-heart-rate>149</target-heart-rate>
              <MAXHEARTRATE>186</MAXHEARTRATE>
              <workout>
                <Ramp Duration="120" PowerLow="0.5" PowerHigh="0.8" />
              </workout>
            </workout_file>
        """.trimIndent()

        val result = service.importFromText("variant_tags", xml)

        val success = requireSuccess(result)
        val workoutFile = requireWorkoutFile(success)
        assertEquals(WorkoutImportFormat.ZWO_XML, success.format)
        assertEquals(149, workoutFile.heartRateTargets?.targetHeartRateBpm)
        assertEquals(186, workoutFile.heartRateTargets?.maxHeartRateBpm)
    }

    @Test
    fun importsErgoWorkoutByFileExtension() {
        val ergoJson = """
            {
              "format": "ergo_workout",
              "version": "0.1",
              "title": "Imported power workout",
              "segments": [
                {
                  "type": "steady",
                  "duration_sec": 300,
                  "target": {
                    "metric": "power",
                    "value": 210
                  }
                }
              ]
            }
        """.trimIndent()

        val result = service.importFromText("power-builder.ewo", ergoJson)

        val success = requireSuccess(result)
        val ergoWorkout = requireErgoWorkout(success)
        assertEquals(WorkoutImportFormat.ERGO_WORKOUT_JSON, success.format)
        assertEquals("Imported power workout", ergoWorkout.title)
        assertEquals(300, ergoWorkout.totalDurationSec)
        assertNull(ergoWorkout.canonicalMetadata)
        val step = ergoWorkout.steps.single() as ImportedErgoWorkoutStep.PowerSteady
        assertEquals(0, step.stepIndex)
        assertEquals(0, step.startOffsetSec)
        assertEquals(300, step.durationSec)
        assertEquals(210, step.watts)
        assertNull(step.canonicalMetadata)
    }

    @Test
    fun importsCanonicalEwoByFileExtension() {
        val canonicalJson = """
            {
              "format": "ewo",
              "version": "1.0",
              "title": "Canonical import workout",
              "description": "Repeat-expanded import handoff",
              "control": {
                "initial_power_watts": 180,
                "min_power_watts": 120,
                "max_power_watts": 320,
                "signal_loss_power_watts": 160,
                "hr_upper_cap_bpm": 185
              },
              "segments": [
                {
                  "id": "warmup",
                  "type": "steady",
                  "duration_sec": 120,
                  "target": {
                    "metric": "power",
                    "value": 180
                  }
                },
                {
                  "id": "build",
                  "type": "ramp",
                  "duration_sec": 60,
                  "from_target": {
                    "metric": "power",
                    "value": 180
                  },
                  "to_target": {
                    "metric": "power",
                    "value": 240
                  }
                },
                {
                  "id": "hr-block",
                  "type": "repeat",
                  "count": 2,
                  "segments": [
                    {
                      "id": "hr-child-1",
                      "type": "steady",
                      "duration_sec": 90,
                      "target": {
                        "metric": "heart_rate",
                        "range": {
                          "low": 145,
                          "high": 155
                        }
                      }
                    },
                    {
                      "id": "hr-child-2",
                      "type": "steady",
                      "duration_sec": 30,
                      "target": {
                        "metric": "power",
                        "value": 200
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = service.importFromText("canonical-import.ewo", canonicalJson)

        val success = requireSuccess(result)
        val ergoWorkout = requireErgoWorkout(success)
        assertEquals(WorkoutImportFormat.CANONICAL_EWO_JSON, success.format)
        assertEquals("Canonical import workout", ergoWorkout.title)
        assertEquals("Repeat-expanded import handoff", ergoWorkout.description)
        assertEquals(420, ergoWorkout.totalDurationSec)
        assertEquals(6, ergoWorkout.steps.size)

        val workoutMetadata = requireNotNull(ergoWorkout.canonicalMetadata)
        assertEquals(180, requireNotNull(workoutMetadata.control).initialPowerWatts)
        assertEquals(120, requireNotNull(workoutMetadata.control).minPowerWatts)
        assertEquals(320, requireNotNull(workoutMetadata.control).maxPowerWatts)
        assertEquals(160, requireNotNull(workoutMetadata.control).signalLossPowerWatts)
        assertEquals(185, requireNotNull(workoutMetadata.control).hrUpperCapBpm)
        assertTrue(workoutMetadata.messages.isEmpty())

        val warmup = ergoWorkout.steps[0] as ImportedErgoWorkoutStep.PowerSteady
        assertEquals(0, warmup.stepIndex)
        assertEquals(0, warmup.startOffsetSec)
        assertEquals(120, warmup.durationSec)
        assertEquals(180, warmup.watts)
        val warmupMetadata = requireNotNull(warmup.canonicalMetadata)
        assertEquals("warmup", warmupMetadata.origin.sourceSegmentId)
        assertEquals(null, warmupMetadata.origin.enclosingRepeatSegmentId)
        assertEquals(null, warmupMetadata.origin.repeatIterationIndex)
        assertTrue(warmupMetadata.messages.isEmpty())

        val build = ergoWorkout.steps[1] as ImportedErgoWorkoutStep.PowerRamp
        assertEquals(1, build.stepIndex)
        assertEquals(120, build.startOffsetSec)
        assertEquals(60, build.durationSec)
        assertEquals(180, build.fromWatts)
        assertEquals(240, build.toWatts)
        val buildMetadata = requireNotNull(build.canonicalMetadata)
        assertEquals("build", buildMetadata.origin.sourceSegmentId)
        assertEquals(null, buildMetadata.origin.enclosingRepeatSegmentId)
        assertEquals(null, buildMetadata.origin.repeatIterationIndex)

        val hrFirst = ergoWorkout.steps[2] as ImportedErgoWorkoutStep.HeartRateSteady
        assertEquals(2, hrFirst.stepIndex)
        assertEquals(180, hrFirst.startOffsetSec)
        assertEquals(90, hrFirst.durationSec)
        assertEquals(145, hrFirst.lowBpm)
        assertEquals(155, hrFirst.highBpm)
        assertEquals(180, hrFirst.initialPowerWatts)
        assertEquals(120, hrFirst.minPowerWatts)
        assertEquals(320, hrFirst.maxPowerWatts)
        assertEquals(160, hrFirst.signalLossPowerWatts)
        val hrFirstMetadata = requireNotNull(hrFirst.canonicalMetadata)
        assertEquals("hr-child-1", hrFirstMetadata.origin.sourceSegmentId)
        assertEquals("hr-block", hrFirstMetadata.origin.enclosingRepeatSegmentId)
        assertEquals(0, hrFirstMetadata.origin.repeatIterationIndex)
        assertTrue(hrFirstMetadata.messages.isEmpty())

        val powerRepeatFirst = ergoWorkout.steps[3] as ImportedErgoWorkoutStep.PowerSteady
        assertEquals(3, powerRepeatFirst.stepIndex)
        assertEquals(270, powerRepeatFirst.startOffsetSec)
        assertEquals(30, powerRepeatFirst.durationSec)
        assertEquals(200, powerRepeatFirst.watts)
        val powerRepeatFirstMetadata = requireNotNull(powerRepeatFirst.canonicalMetadata)
        assertEquals("hr-child-2", powerRepeatFirstMetadata.origin.sourceSegmentId)
        assertEquals("hr-block", powerRepeatFirstMetadata.origin.enclosingRepeatSegmentId)
        assertEquals(0, powerRepeatFirstMetadata.origin.repeatIterationIndex)

        val hrSecond = ergoWorkout.steps[4] as ImportedErgoWorkoutStep.HeartRateSteady
        assertEquals(4, hrSecond.stepIndex)
        assertEquals(300, hrSecond.startOffsetSec)
        assertEquals(90, hrSecond.durationSec)
        assertEquals(145, hrSecond.lowBpm)
        assertEquals(155, hrSecond.highBpm)
        val hrSecondMetadata = requireNotNull(hrSecond.canonicalMetadata)
        assertEquals("hr-child-1", hrSecondMetadata.origin.sourceSegmentId)
        assertEquals("hr-block", hrSecondMetadata.origin.enclosingRepeatSegmentId)
        assertEquals(1, hrSecondMetadata.origin.repeatIterationIndex)

        val powerRepeatSecond = ergoWorkout.steps[5] as ImportedErgoWorkoutStep.PowerSteady
        assertEquals(5, powerRepeatSecond.stepIndex)
        assertEquals(390, powerRepeatSecond.startOffsetSec)
        assertEquals(30, powerRepeatSecond.durationSec)
        assertEquals(200, powerRepeatSecond.watts)
        val powerRepeatSecondMetadata = requireNotNull(powerRepeatSecond.canonicalMetadata)
        assertEquals("hr-child-2", powerRepeatSecondMetadata.origin.sourceSegmentId)
        assertEquals("hr-block", powerRepeatSecondMetadata.origin.enclosingRepeatSegmentId)
        assertEquals(1, powerRepeatSecondMetadata.origin.repeatIterationIndex)
    }

    @Test
    fun importsCanonicalEwoWithHrRelativeTargetsWhenCompileContextProvidesHrMax() {
        val canonicalJson = """
            {
              "format": "ewo",
              "version": "1.6",
              "title": "HR relative import workout",
              "control": {
                "initial_power_watts": 140,
                "min_power_watts": 80,
                "max_power_watts": 320,
                "signal_loss_power_watts": 100,
                "hr_upper_cap_bpm": 185
              },
              "segments": [
                {
                  "id": "main",
                  "type": "steady",
                  "duration_sec": 600,
                  "target": {
                    "metric": "heart_rate_relative",
                    "reference": "hr_max",
                    "range": {
                      "low": 0.65,
                      "high": 0.72
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val result = service.importFromText(
            sourceName = "hr-relative.ewo",
            content = canonicalJson,
            context = EwoCompileContext(hrMaxBpm = 180),
        )

        val success = requireSuccess(result)
        val ergoWorkout = requireErgoWorkout(success)
        val step = ergoWorkout.steps.single() as ImportedErgoWorkoutStep.HeartRateSteady
        assertEquals(117, step.lowBpm)
        assertEquals(130, step.highBpm)
    }

    @Test
    fun importsCanonicalEwoWhileDroppingInvalidTags() {
        val canonicalJson = """
            {
              "format": "ewo",
              "version": "1.3",
              "title": "Canonical import workout",
              "tags": ["endurance", "mäki", "tempo"],
              "segments": [
                {
                  "id": "steady-step",
                  "type": "steady",
                  "duration_sec": 180,
                  "target": {
                    "metric": "power",
                    "value": 190
                  }
                }
              ]
            }
        """.trimIndent()

        val result = service.importFromText("canonical-invalid-tags.ewo", canonicalJson)

        val success = requireSuccess(result)
        val workout = requireErgoWorkout(success)
        assertEquals(WorkoutImportFormat.CANONICAL_EWO_JSON, success.format)
        assertEquals("Canonical import workout", workout.title)
        assertEquals(listOf("endurance", "tempo"), workout.tags)
    }

    @Test
    fun preservesCanonicalMessagesAcrossImportHandoff() {
        val canonicalJson = """
            {
              "format": "ewo",
              "version": "1.0",
              "title": "Canonical messages workout",
              "messages": [
                {
                  "kind": "intro",
                  "when": "start",
                  "text": {
                    "default": "Ready to begin.",
                    "translations": {
                      "fi": "Valmis aloittamaan."
                    }
                  }
                }
              ],
              "segments": [
                {
                  "id": "warmup",
                  "type": "steady",
                  "duration_sec": 60,
                  "target": {
                    "metric": "power",
                    "value": 180
                  },
                  "messages": [
                    {
                      "kind": "instruction",
                      "when": "start",
                      "text": {
                        "default": "Hold steady."
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = service.importFromText("canonical-messages.ewo", canonicalJson)

        val success = requireSuccess(result)
        val ergoWorkout = requireErgoWorkout(success)
        val workoutMetadata = requireNotNull(ergoWorkout.canonicalMetadata)
        assertEquals(1, workoutMetadata.messages.size)
        assertEquals(ImportedErgoWorkoutMessageKind.INTRO, workoutMetadata.messages.single().kind)
        assertEquals(ImportedErgoWorkoutMessageAnchor.START, workoutMetadata.messages.single().timing.anchor)
        assertEquals(0, workoutMetadata.messages.single().timing.offsetSec)
        assertEquals("Ready to begin.", workoutMetadata.messages.single().text.defaultText)
        assertEquals("Valmis aloittamaan.", workoutMetadata.messages.single().text.translations["fi"])

        val step = ergoWorkout.steps.single() as ImportedErgoWorkoutStep.PowerSteady
        val stepMetadata = requireNotNull(step.canonicalMetadata)
        assertEquals(1, stepMetadata.messages.size)
        assertEquals(
            ImportedErgoWorkoutMessageKind.INSTRUCTION,
            stepMetadata.messages.single().kind,
        )
        assertEquals("Hold steady.", stepMetadata.messages.single().text.defaultText)
    }

    @Test
    fun rejectsCanonicalEwoImportWhenWorkoutContainsFreeRideSegment() {
        val canonicalJson = """
            {
              "format": "ewo",
              "version": "1.5",
              "uid": "free-ride-001",
              "revision": 2,
              "title": "Free Ride Import",
              "messages": [
                {
                  "kind": "intro",
                  "when": { "anchor": "end", "offset_sec": -20 },
                  "text": { "default": "Ride ends soon." }
                }
              ],
              "segments": [
                {
                  "id": "spin_out",
                  "type": "free_ride",
                  "label": "Spin Out",
                  "note": "Let the rider choose resistance.",
                  "duration_sec": 180,
                  "messages": [
                    {
                      "kind": "instruction",
                      "when": { "anchor": "end", "offset_sec": -5 },
                      "text": { "default": "Prepare to stop." }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = service.importFromText("free-ride.ewo", canonicalJson)

        val failure = result as? WorkoutImportResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
        assertEquals(WorkoutImportErrorCode.UNSUPPORTED_SEGMENT, failure.error.code)
        assertEquals(WorkoutImportFormat.CANONICAL_EWO_JSON, failure.error.detectedFormat)
        assertTrue(failure.error.message.contains("free-ride"))
        assertEquals("unsupported_segment: free_ride", failure.error.technicalDetails)
    }

    @Test
    fun rejectsZwoImportWhenWorkoutContainsFreeRideStep() {
        val xml = """
            <workout_file>
              <name>Legacy Free Ride</name>
              <workout>
                <FreeRide Duration="180"/>
              </workout>
            </workout_file>
        """.trimIndent()

        val result = service.importFromText("legacy-free-ride.zwo", xml)

        val failure = result as? WorkoutImportResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
        assertEquals(WorkoutImportErrorCode.UNSUPPORTED_SEGMENT, failure.error.code)
        assertEquals(WorkoutImportFormat.ZWO_XML, failure.error.detectedFormat)
        assertEquals("unsupported_segment: free_ride", failure.error.technicalDetails)
    }

    @Test
    fun preservesV16LocalizedRootMetadataAcrossImportHandoff() {
        val canonicalJson = """
            {
              "format": "ewo",
              "version": "1.6",
              "uid": "root-localized-001",
              "revision": 1,
              "title": "Threshold Builder",
              "description": "3 x 8 min near threshold with controlled recoveries.",
              "title_localized": {
                "default": "Threshold Builder",
                "translations": {
                  "fi": "Kynnystehon rakentaja"
                }
              },
              "description_localized": {
                "default": "3 x 8 min near threshold with controlled recoveries.",
                "translations": {
                  "fi": "3 x 8 min lahella kynnysta hallituilla palautuksilla."
                }
              },
              "segments": [
                {
                  "id": "warmup",
                  "type": "steady",
                  "duration_sec": 180,
                  "target": {
                    "metric": "power",
                    "value": 160
                  }
                }
              ]
            }
        """.trimIndent()

        val result = service.importFromText("root-localized.ewo", canonicalJson)

        val success = requireSuccess(result)
        val workout = requireErgoWorkout(success)
        assertEquals("Threshold Builder", workout.title)
        assertEquals("3 x 8 min near threshold with controlled recoveries.", workout.description)

        val metadata = requireNotNull(workout.canonicalMetadata)
        assertEquals("Threshold Builder", metadata.titleLocalized?.defaultText)
        assertEquals("Kynnystehon rakentaja", metadata.titleLocalized?.translations?.get("fi"))
        assertEquals(
            "3 x 8 min near threshold with controlled recoveries.",
            metadata.descriptionLocalized?.defaultText,
        )
        assertEquals(
            "3 x 8 min lahella kynnysta hallituilla palautuksilla.",
            metadata.descriptionLocalized?.translations?.get("fi"),
        )
    }

    @Test
    fun prefersErgoWorkoutContentSniffingForJsonExtension() {
        val ergoJson = """
            {
              "format": "ergo_workout",
              "version": "0.1",
              "title": "Sniffed workout",
              "segments": [
                {
                  "type": "steady",
                  "duration_sec": 180,
                  "target": {
                    "metric": "power",
                    "value": 190
                  }
                }
              ]
            }
        """.trimIndent()

        val result = service.importFromText("sniffed.json", ergoJson)

        val success = requireSuccess(result)
        assertEquals(WorkoutImportFormat.ERGO_WORKOUT_JSON, success.format)
        assertEquals("Sniffed workout", requireErgoWorkout(success).title)
    }

    @Test
    fun prefersCanonicalEwoContentSniffingForJsonExtension() {
        val canonicalJson = """
            {
              "format": "ewo",
              "version": "1.0",
              "title": "Sniffed canonical workout",
              "segments": [
                {
                  "id": "steady-step",
                  "type": "steady",
                  "duration_sec": 180,
                  "target": {
                    "metric": "power",
                    "value": 190
                  }
                }
              ]
            }
        """.trimIndent()

        val result = service.importFromText("sniffed.json", canonicalJson)

        val success = requireSuccess(result)
        assertEquals(WorkoutImportFormat.CANONICAL_EWO_JSON, success.format)
        assertEquals("Sniffed canonical workout", requireErgoWorkout(success).title)
    }

    @Test
    fun returnsParseFailedForInvalidErgoWorkoutJson() {
        val invalidErgoJson = """
            {
              "format": "ergo_workout",
              "version": "0.1",
              "title": "Broken ergo",
              "segments": [
                {
                  "type": "steady",
                  "duration_sec": 120,
                  "target": {
                    "metric": "power",
                    "value": 180
                  },
                  "unexpected": true
                }
              ]
            }
        """.trimIndent()

        val result = service.importFromText("broken.ewo", invalidErgoJson)

        val failure = result as? WorkoutImportResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
        val technicalDetails = requireNotNull(failure.error.technicalDetails)
        assertEquals(WorkoutImportErrorCode.PARSE_FAILED, failure.error.code)
        assertEquals(WorkoutImportFormat.ERGO_WORKOUT_JSON, failure.error.detectedFormat)
        assertEquals("ergo_workout JSON parsing failed.", failure.error.message)
        assertTrue(
            "Expected stable ergo validation code in technical details",
            technicalDetails.contains("unknown_field"),
        )
        assertTrue(
            "Expected stable ergo validation field path in technical details",
            technicalDetails.contains("$.segments[0].unexpected"),
        )
    }

    @Test
    fun returnsParseFailedForInvalidCanonicalEwoJson() {
        val invalidCanonicalJson = """
            {
              "format": "ewo",
              "version": "1.0",
              "title": "Broken canonical workout",
              "segments": [
                {
                  "id": "steady-step",
                  "type": "steady",
                  "duration_sec": 120,
                  "target": {
                    "metric": "power",
                    "value": 180
                  },
                  "unexpected": true
                }
              ]
            }
        """.trimIndent()

        val result = service.importFromText("broken-canonical.ewo", invalidCanonicalJson)

        val failure = result as? WorkoutImportResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
        val technicalDetails = requireNotNull(failure.error.technicalDetails)
        assertEquals(WorkoutImportErrorCode.PARSE_FAILED, failure.error.code)
        assertEquals(WorkoutImportFormat.CANONICAL_EWO_JSON, failure.error.detectedFormat)
        assertEquals("Canonical .ewo JSON parsing failed.", failure.error.message)
        assertTrue(
            "Expected stable canonical validation code in technical details",
            technicalDetails.contains("unknown_field"),
        )
        assertTrue(
            "Expected stable canonical validation field path in technical details",
            technicalDetails.contains("$.segments[0].unexpected"),
        )
    }

    @Test
    fun returnsParseFailedForMalformedXml() {
        val malformed = "<workout_file><workout><SteadyState Duration=\"60\" Power=\"0.8\"></workout_file>"

        val result = service.importFromText("broken.zwo", malformed)

        val failure = result as? WorkoutImportResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
        assertEquals(WorkoutImportErrorCode.PARSE_FAILED, failure.error.code)
        assertEquals(WorkoutImportFormat.ZWO_XML, failure.error.detectedFormat)
        assertTrue(
            "Expected parser diagnostics for malformed XML",
            !failure.error.technicalDetails.isNullOrBlank(),
        )
        assertTrue(
            "Expected malformed XML reason in technical details",
            failure.error.technicalDetails!!.contains("Malformed ZWO XML"),
        )
    }

    @Test
    fun returnsEmptyWorkoutForXmlWithoutSteps() {
        val emptyWorkoutXml = """
            <workout_file>
              <name>Empty</name>
              <workout />
            </workout_file>
        """.trimIndent()

        val result = service.importFromText("empty.xml", emptyWorkoutXml)

        val failure = result as? WorkoutImportResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
        assertEquals(WorkoutImportErrorCode.EMPTY_WORKOUT, failure.error.code)
        assertEquals(WorkoutImportFormat.ZWO_XML, failure.error.detectedFormat)
    }

    @Test
    fun returnsUnsupportedForMyWhooshJsonUntilAdapterExists() {
        val myWhooshJson = """{"name":"MyWhoosh Workout","steps":[{"type":"steady"}]}"""

        val result = service.importFromText("mywhoosh.json", myWhooshJson)

        val failure = result as? WorkoutImportResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
        assertEquals(WorkoutImportErrorCode.UNSUPPORTED_FORMAT, failure.error.code)
        assertEquals(WorkoutImportFormat.MYWHOOSH_JSON, failure.error.detectedFormat)
    }

    @Test
    fun returnsUnsupportedForUnknownFormat() {
        val result = service.importFromText("notes.txt", "plain text")

        val failure = result as? WorkoutImportResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
        assertEquals(WorkoutImportErrorCode.UNSUPPORTED_FORMAT, failure.error.code)
        assertEquals(WorkoutImportFormat.UNKNOWN, failure.error.detectedFormat)
    }

    @Test
    fun importsBundledCanonicalWorkoutAssets() {
        val ftpWatts = 200
        val assetFiles = readBundledWorkoutAssets()

        assertTrue(
            "Expected at least one bundled canonical workout asset.",
            assetFiles.isNotEmpty(),
        )

        assetFiles.forEach { file ->
            val result = service.importFromText(file.name, file.readText(), ftpWatts = ftpWatts)
            val durationMinutesFromFilename = Regex("""^(\d+)min_.*\.ewo$""")
                .matchEntire(file.name)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: throw AssertionError(
                    "Bundled workout asset ${file.name} does not follow the <minutes>min_<name>.ewo naming convention.",
                )

            val success = requireSuccess(result)
            val workout = requireErgoWorkout(success)
            assertEquals(WorkoutImportFormat.CANONICAL_EWO_JSON, success.format)
            assertTrue("Expected non-blank title for ${file.name}.", workout.title.isNotBlank())
            assertTrue(
                "Expected non-blank description for ${file.name}.",
                !workout.description.isNullOrBlank(),
            )
            assertTrue("Expected executable steps for ${file.name}.", workout.steps.isNotEmpty())
            assertTrue("Expected difficulty metadata for ${file.name}.", workout.difficulty != null)
            assertTrue("Expected tags for ${file.name}.", workout.tags.isNotEmpty())
            assertEquals(
                "Bundled workout duration must match the filename minute prefix for ${file.name}.",
                durationMinutesFromFilename * 60,
                workout.totalDurationSec,
            )
        }
    }

    @Test
    fun importsBundledStarterRideAssetWithLocalizedMessages() {
        val starterAsset = readBundledWorkoutAsset("25min_starter_ride.ewo")

        val result = service.importFromText(starterAsset.name, starterAsset.readText(), ftpWatts = 200)

        val success = requireSuccess(result)
        val workout = requireErgoWorkout(success)
        assertEquals(WorkoutImportFormat.CANONICAL_EWO_JSON, success.format)
        assertEquals("Starter Ride", workout.title)
        assertEquals(25 * 60, workout.totalDurationSec)
        assertEquals(10, workout.steps.size)
        assertEquals("Bundled FTP-based starter ride with short surges, guided messages, and a controlled threshold finish.", workout.description)

        val workoutMetadata = requireNotNull(workout.canonicalMetadata)
        assertEquals(1, workoutMetadata.messages.size)
        assertEquals(
            "Tämä paketoitu aloitusharjoitus näyttää, miten tiedostopohjainen treeni voi yhdistää rampit, terävät piikit, palautukset ja ohjaavat viestit yhdeksi rakenteiseksi kokonaisuudeksi.",
            workoutMetadata.messages.single().text.translations["fi"],
        )
        assertEquals(
            "この同梱スターターライドは、ファイルベースのワークアウトでランプ、短い刺激、回復、ガイドメッセージを1本の構造化セッションに組み合わせられることを示します。",
            workoutMetadata.messages.single().text.translations["ja"],
        )

        val stepMessages = workout.steps.mapNotNull { step ->
            step.canonicalMetadata?.messages?.takeIf { it.isNotEmpty() }
        }
        assertEquals(6, stepMessages.size)
        assertEquals("surge_one", requireNotNull(workout.steps[1].canonicalMetadata).origin.sourceSegmentId)
        assertEquals("tempo_block", requireNotNull(workout.steps[7].canonicalMetadata).origin.sourceSegmentId)
        assertEquals("threshold_finish", requireNotNull(workout.steps[8].canonicalMetadata).origin.sourceSegmentId)
    }

    @Test
    fun importsBundledWorkoutTestXmlFixture() {
        val xml = readWorkoutTestXml()

        val result = service.importFromText("Workout_Test.xml", xml)

        val success = requireSuccess(result)
        val workoutFile = requireWorkoutFile(success)
        assertEquals(WorkoutImportFormat.ZWO_XML, success.format)
        assertEquals(4, workoutFile.steps.size)
        assertTrue(workoutFile.steps[0] is Step.Warmup)
        assertTrue(workoutFile.steps[1] is Step.IntervalsT)
        assertTrue(workoutFile.steps[2] is Step.SteadyState)
        assertTrue(workoutFile.steps[3] is Step.Cooldown)
    }

    private fun readWorkoutTestXml(): String {
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

    private fun readBundledWorkoutAssets(): List<File> {
        val candidateDirectories = listOf(
            File("app/src/main/assets/workouts"),
            File("../app/src/main/assets/workouts"),
            File("../../app/src/main/assets/workouts"),
        )
        val directory = candidateDirectories.firstOrNull(File::isDirectory)
            ?: throw AssertionError(
                "Bundled workout asset directory not found. Looked in: " +
                    candidateDirectories.joinToString { it.path },
            )
        return directory.listFiles { file -> file.isFile && file.extension == "ewo" }
            ?.sortedBy(File::getName)
            ?: throw AssertionError(
                "Expected bundled workout assets in ${directory.path}, but directory listing failed.",
            )
    }

    private fun readBundledWorkoutAsset(fileName: String): File {
        return readBundledWorkoutAssets().firstOrNull { it.name == fileName }
            ?: throw AssertionError("Bundled workout asset $fileName not found.")
    }

    private fun requireSuccess(result: WorkoutImportResult): WorkoutImportResult.Success {
        return result as? WorkoutImportResult.Success
            ?: throw AssertionError("Expected success, got $result")
    }

    private fun requireWorkoutFile(success: WorkoutImportResult.Success): WorkoutFile {
        return requireNotNull(success.workoutFile) {
            "Expected ZWO workout payload, got ${success.payload::class.simpleName}"
        }
    }

    private fun requireErgoWorkout(success: WorkoutImportResult.Success): ImportedErgoWorkout {
        return requireNotNull(success.ergoWorkout) {
            "Expected ergo workout payload, got ${success.payload::class.simpleName}"
        }
    }
}
