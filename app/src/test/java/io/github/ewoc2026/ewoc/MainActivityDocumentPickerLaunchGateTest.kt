package io.github.ewoc2026.ewoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityDocumentPickerLaunchGateTest {
    @Test
    fun blocksSecondLaunchWhileFirstPickerIsStillInFlight() {
        val events = mutableListOf<String>()
        val gate = MainActivityDocumentPickerLaunchGate(log = events::add)

        val firstLaunchStarted = gate.tryLaunch("openWorkout") {
            events += "launch:first"
        }
        val secondLaunchStarted = gate.tryLaunch("openEwo") {
            events += "launch:second"
        }

        assertTrue(firstLaunchStarted)
        assertFalse(secondLaunchStarted)
        assertEquals(
            listOf(
                "Launching SAF request: openWorkout",
                "launch:first",
                "Ignoring SAF launch for openEwo because openWorkout is already in flight.",
            ),
            events,
        )
    }

    @Test
    fun resultDeliveryReleasesGateForNextLaunch() {
        val events = mutableListOf<String>()
        val gate = MainActivityDocumentPickerLaunchGate(log = events::add)

        gate.tryLaunch("openWorkout") {
            events += "launch:first"
        }
        gate.onResultDelivered("openWorkout")
        val nextLaunchStarted = gate.tryLaunch("openEwo") {
            events += "launch:second"
        }

        assertTrue(nextLaunchStarted)
        assertEquals(
            listOf(
                "Launching SAF request: openWorkout",
                "launch:first",
                "SAF result delivered for openWorkout; gate released.",
                "Launching SAF request: openEwo",
                "launch:second",
            ),
            events,
        )
    }

    @Test
    fun failedLaunchReleasesGateImmediately() {
        val events = mutableListOf<String>()
        val gate = MainActivityDocumentPickerLaunchGate(log = events::add)

        runCatching {
            gate.tryLaunch("openWorkout") {
                events += "launch:first"
                error("boom")
            }
        }

        val nextLaunchStarted = gate.tryLaunch("openEwo") {
            events += "launch:second"
        }

        assertTrue(nextLaunchStarted)
        assertEquals(
            listOf(
                "Launching SAF request: openWorkout",
                "launch:first",
                "SAF launch failed for openWorkout; gate released.",
                "Launching SAF request: openEwo",
                "launch:second",
            ),
            events,
        )
    }
}
