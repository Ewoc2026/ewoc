package com.example.ergometerapp

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivitySummaryFitShareCoordinatorTest {
    @Test
    fun ignoresSecondShareRequestWhileChooserLaunchIsStillInFlight() {
        val events = mutableListOf<String>()
        val launchedIntents = mutableListOf<Intent>()
        val intent = Intent("share")
        val coordinator = MainActivitySummaryFitShareCoordinator(
            prepareShareIntent = { intent },
            launchActivity = { launchedIntents += it },
            onLaunchFailed = { events += "launchFailed" },
            logLaunchFailure = { events += "logFailure:${it.message}" },
            log = events::add,
        )

        coordinator.requestShare()
        coordinator.requestShare()

        assertEquals(listOf(intent), launchedIntents)
        assertEquals(
            listOf(
                "Launching summary FIT share chooser.",
                "Ignoring summary FIT share request because chooser launch is already in flight.",
            ),
            events,
        )
    }

    @Test
    fun hostResumeReleasesShareGateForNextRequest() {
        val events = mutableListOf<String>()
        val launchedIntents = mutableListOf<Intent>()
        val firstIntent = Intent("share-first")
        val secondIntent = Intent("share-second")
        var requestCount = 0
        val coordinator = MainActivitySummaryFitShareCoordinator(
            prepareShareIntent = {
                requestCount += 1
                if (requestCount == 1) firstIntent else secondIntent
            },
            launchActivity = { launchedIntents += it },
            onLaunchFailed = { events += "launchFailed" },
            logLaunchFailure = { events += "logFailure:${it.message}" },
            log = events::add,
        )

        coordinator.requestShare()
        coordinator.onHostResumed()
        coordinator.requestShare()

        assertEquals(listOf(firstIntent, secondIntent), launchedIntents)
        assertEquals(
            listOf(
                "Launching summary FIT share chooser.",
                "Summary FIT share chooser returned; gate released.",
                "Launching summary FIT share chooser.",
            ),
            events,
        )
    }

    @Test
    fun failedShareLaunchReleasesGateImmediately() {
        val events = mutableListOf<String>()
        val coordinator = MainActivitySummaryFitShareCoordinator(
            prepareShareIntent = { Intent("share") },
            launchActivity = { error("boom") },
            onLaunchFailed = { events += "launchFailed" },
            logLaunchFailure = { events += "logFailure:${it.message}" },
            log = events::add,
        )

        coordinator.requestShare()
        coordinator.requestShare()

        assertEquals(
            listOf(
                "Launching summary FIT share chooser.",
                "Summary FIT share launch failed; gate released.",
                "logFailure:boom",
                "launchFailed",
                "Launching summary FIT share chooser.",
                "Summary FIT share launch failed; gate released.",
                "logFailure:boom",
                "launchFailed",
            ),
            events,
        )
    }
}
