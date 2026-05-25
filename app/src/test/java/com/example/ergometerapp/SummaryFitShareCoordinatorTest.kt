package com.example.ergometerapp

import android.content.Intent
import android.net.Uri
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.session.export.FitExportFailureReason
import com.example.ergometerapp.session.export.SessionExportSnapshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File

class SummaryFitShareCoordinatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun prepareShareIntent_returnsChooserAndClearsDocumentsStatusWhenReady() {
        val cacheDir = temporaryFolder.newFolder("fit-share-cache")
        val fitPayload = byteArrayOf(0x11, 0x22, 0x33)
        var capturedShareUri: Uri? = null
        var writtenFile: File? = null
        var clearDocumentsFolderStatusCalls = 0
        val chooserIntent = Intent("summary.fit.share")
        val resolvedShareUri = Mockito.mock(Uri::class.java)
        val statePort = TestSummaryFitShareStatePort(
            statusMessage = "stale",
            statusIsError = true,
        )
        val coordinator = createCoordinator(
            cacheDirectoryProvider = { cacheDir },
            buildFitBytes = { fitPayload },
            resolveShareUri = { file ->
                writtenFile = file
                resolvedShareUri
            },
            createChooserIntent = { shareUri ->
                capturedShareUri = shareUri
                chooserIntent
            },
            clearDocumentsFolderStatus = {
                clearDocumentsFolderStatusCalls += 1
            },
        )

        val intent = coordinator.prepareShareIntent(
            snapshot = sampleSnapshot(),
            statePort = statePort,
        )

        assertSame(chooserIntent, intent)
        assertEquals("session_2026-03-05_08-00-00.fit", writtenFile?.name)
        assertArrayEquals(fitPayload, writtenFile?.readBytes())
        assertSame(resolvedShareUri, capturedShareUri)
        assertEquals(1, clearDocumentsFolderStatusCalls)
        assertNull(statePort.statusMessage)
        assertFalse(statePort.statusIsError)
    }

    @Test
    fun prepareShareIntent_setsNoSummaryStatusWhenSnapshotMissing() {
        val statePort = TestSummaryFitShareStatePort()
        val coordinator = createCoordinator(
            cacheDirectoryProvider = { temporaryFolder.newFolder("fit-share-no-summary") },
            buildFitBytes = { byteArrayOf(0x01) },
            resolveShareUri = { _ -> Mockito.mock(Uri::class.java) },
            createChooserIntent = { _ -> Intent("summary.fit.share") },
        )

        val intent = coordinator.prepareShareIntent(
            snapshot = null,
            statePort = statePort,
        )

        assertNull(intent)
        assertEquals("No summary available.", statePort.statusMessage)
        assertTrue(statePort.statusIsError)
    }

    @Test
    fun prepareShareIntent_setsExportFailureStatusWhenSummaryTimesAreReversed() {
        var buildCalls = 0
        val statePort = TestSummaryFitShareStatePort()
        val coordinator = createCoordinator(
            cacheDirectoryProvider = { temporaryFolder.newFolder("fit-share-invalid") },
            buildFitBytes = {
                buildCalls += 1
                byteArrayOf(0x00)
            },
            resolveShareUri = { _ -> Mockito.mock(Uri::class.java) },
            createChooserIntent = { _ -> Intent("summary.fit.share") },
        )

        val intent = coordinator.prepareShareIntent(
            snapshot = sampleSnapshot(startMillis = 2_000L, stopMillis = 1_999L),
            statePort = statePort,
        )

        assertNull(intent)
        assertEquals(0, buildCalls)
        assertEquals(
            "Export failed: ${FitExportFailureReason.INVALID_TIMESTAMPS}",
            statePort.statusMessage,
        )
        assertTrue(statePort.statusIsError)
    }

    @Test
    fun prepareShareIntent_setsExportFailureStatusWhenPayloadBuildThrows() {
        val statePort = TestSummaryFitShareStatePort()
        val coordinator = createCoordinator(
            cacheDirectoryProvider = { temporaryFolder.newFolder("fit-share-build-failure") },
            buildFitBytes = {
                throw IllegalStateException("boom")
            },
            resolveShareUri = { _ -> Mockito.mock(Uri::class.java) },
            createChooserIntent = { _ -> Intent("summary.fit.share") },
        )

        val intent = coordinator.prepareShareIntent(
            snapshot = sampleSnapshot(),
            statePort = statePort,
        )

        assertNull(intent)
        assertEquals("Export failed: ${FitExportFailureReason.WRITE_FAILED}:boom", statePort.statusMessage)
        assertTrue(statePort.statusIsError)
    }

    @Test
    fun prepareShareIntent_setsUriFailureStatusWhenFileProviderCannotResolveUri() {
        val statePort = TestSummaryFitShareStatePort()
        val coordinator = createCoordinator(
            cacheDirectoryProvider = { temporaryFolder.newFolder("fit-share-uri-failure") },
            buildFitBytes = { byteArrayOf(0x01, 0x02) },
            resolveShareUri = { _ -> null },
            createChooserIntent = { _ -> Intent("summary.fit.share") },
        )

        val intent = coordinator.prepareShareIntent(
            snapshot = sampleSnapshot(),
            statePort = statePort,
        )

        assertNull(intent)
        assertEquals("Share URI unavailable.", statePort.statusMessage)
        assertTrue(statePort.statusIsError)
    }

    @Test
    fun prepareShareIntent_setsExportFailureStatusWhenCachePathIsNotDirectory() {
        val notDirectory = temporaryFolder.newFile("fit-share-parent-file")
        val statePort = TestSummaryFitShareStatePort()
        val coordinator = createCoordinator(
            cacheDirectoryProvider = { notDirectory },
            buildFitBytes = { byteArrayOf(0x7F) },
            resolveShareUri = { _ -> Mockito.mock(Uri::class.java) },
            createChooserIntent = { _ -> Intent("summary.fit.share") },
        )

        val intent = coordinator.prepareShareIntent(
            snapshot = sampleSnapshot(),
            statePort = statePort,
        )

        assertNull(intent)
        assertTrue(
            statePort.statusMessage?.startsWith("Export failed: ${FitExportFailureReason.WRITE_FAILED}") == true,
        )
        assertTrue(statePort.statusIsError)
    }

    @Test
    fun onShareLaunchFailed_setsLaunchFailureStatus() {
        val statePort = TestSummaryFitShareStatePort()
        val coordinator = createCoordinator(
            cacheDirectoryProvider = { temporaryFolder.newFolder("fit-share-launch-failure") },
            buildFitBytes = { byteArrayOf(0x01) },
            resolveShareUri = { _ -> Mockito.mock(Uri::class.java) },
            createChooserIntent = { _ -> Intent("summary.fit.share") },
        )

        coordinator.onShareLaunchFailed(statePort)

        assertEquals("Share launch failed.", statePort.statusMessage)
        assertTrue(statePort.statusIsError)
    }

    private fun createCoordinator(
        cacheDirectoryProvider: () -> File,
        buildFitBytes: (SessionExportSnapshot) -> ByteArray,
        resolveShareUri: (File) -> Uri?,
        createChooserIntent: (Uri) -> Intent,
        clearDocumentsFolderStatus: () -> Unit = {},
    ): SummaryFitShareCoordinator {
        return SummaryFitShareCoordinator(
            suggestFileName = { "session_2026-03-05_08-00-00" },
            noSummaryMessage = { "No summary available." },
            exportFailureMessage = { failure ->
                buildString {
                    append("Export failed: ")
                    append(failure.reason)
                    failure.detail?.let {
                        append(":")
                        append(it)
                    }
                }
            },
            shareUriUnavailableMessage = { "Share URI unavailable." },
            launchFailureMessage = { "Share launch failed." },
            cacheDirectoryProvider = cacheDirectoryProvider,
            buildFitBytes = buildFitBytes,
            resolveShareUri = resolveShareUri,
            createChooserIntent = createChooserIntent,
            clearDocumentsFolderStatus = clearDocumentsFolderStatus,
        )
    }

    private fun sampleSnapshot(
        startMillis: Long = 1_000L,
        stopMillis: Long = 2_000L,
    ): SessionExportSnapshot {
        return SessionExportSnapshot(
            summary = SessionSummary(
                startTimestampMillis = startMillis,
                stopTimestampMillis = stopMillis,
                durationSeconds = ((stopMillis - startMillis) / 1_000L).toInt().coerceAtLeast(0),
                actualTss = 42.0,
                avgPower = 210,
                maxPower = 400,
                avgCadence = 88,
                maxCadence = 102,
                avgHeartRate = 145,
                maxHeartRate = 166,
                distanceMeters = 12_345,
                totalEnergyKcal = 678,
            ),
            timeline = emptyList(),
        )
    }

    private class TestSummaryFitShareStatePort(
        override var statusMessage: String? = null,
        override var statusIsError: Boolean = false,
    ) : SummaryFitShareStatePort
}
