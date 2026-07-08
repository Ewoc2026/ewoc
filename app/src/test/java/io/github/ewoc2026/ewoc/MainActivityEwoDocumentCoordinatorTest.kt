package io.github.ewoc2026.ewoc

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class MainActivityEwoDocumentCoordinatorTest {
    @Test
    fun handleOpenResult_ignoresCancelledAndReportsUnreadableResults() {
        val events = mutableListOf<String>()
        val uri = Mockito.mock(Uri::class.java)
        Mockito.`when`(uri.lastPathSegment).thenReturn("workout.ewo")
        val coordinator = MainActivityEwoDocumentCoordinator(
            readDocumentUtf8 = { uri ->
                events += "read:${uri.lastPathSegment}"
                null
            },
            onOpenDocumentLoaded = { _, _ ->
                events += "loaded"
            },
            onOpenError = { message ->
                events += "error:$message"
            },
            prepareExportJson = { null },
            writeDocumentUtf8 = { _, _ -> true },
            onSaveCompleted = { fileName ->
                events += "saved:$fileName"
            },
        )

        coordinator.handleOpenResult(null)
        coordinator.handleOpenResult(uri)

        assertEquals(listOf("read:workout.ewo", "error:Could not read file content"), events)
    }

    @Test
    fun handleOpenResult_reportsExceptionAsError() {
        val events = mutableListOf<String>()
        val uri = Mockito.mock(Uri::class.java)
        val coordinator = MainActivityEwoDocumentCoordinator(
            readDocumentUtf8 = { throw java.io.IOException("Permission denied") },
            onOpenDocumentLoaded = { _, _ -> events += "loaded" },
            onOpenError = { message -> events += "error:$message" },
            prepareExportJson = { null },
            writeDocumentUtf8 = { _, _ -> true },
            onSaveCompleted = {},
        )

        coordinator.handleOpenResult(uri)

        assertEquals(listOf("error:Failed to read file: Permission denied"), events)
    }

    @Test
    fun handleOpenResult_reportsEmptyFileAsError() {
        val events = mutableListOf<String>()
        val uri = Mockito.mock(Uri::class.java)
        val coordinator = MainActivityEwoDocumentCoordinator(
            readDocumentUtf8 = { "   " },
            onOpenDocumentLoaded = { _, _ -> events += "loaded" },
            onOpenError = { message -> events += "error:$message" },
            prepareExportJson = { null },
            writeDocumentUtf8 = { _, _ -> true },
            onSaveCompleted = {},
        )

        coordinator.handleOpenResult(uri)

        assertEquals(listOf("error:File is empty"), events)
    }

    @Test
    fun handleOpenResult_loadsReadableDocumentWithResolvedFileName() {
        var loadedJson: String? = null
        var loadedFileName: String? = null
        val uri = Mockito.mock(Uri::class.java)
        Mockito.`when`(uri.lastPathSegment).thenReturn("workout.ewo")
        val coordinator = MainActivityEwoDocumentCoordinator(
            readDocumentUtf8 = {
                "ewo-json"
            },
            resolveOpenedFileName = { uri ->
                "resolved:${uri.lastPathSegment}"
            },
            onOpenDocumentLoaded = { json, fileName ->
                loadedJson = json
                loadedFileName = fileName
            },
            prepareExportJson = { null },
            writeDocumentUtf8 = { _, _ -> true },
            onSaveCompleted = {},
        )

        coordinator.handleOpenResult(uri)

        assertEquals("ewo-json", loadedJson)
        assertEquals("resolved:workout.ewo", loadedFileName)
    }

    @Test
    fun handleSaveResult_returnsWhenCancelledOrExportJsonMissing() {
        val events = mutableListOf<String>()
        val uri = Mockito.mock(Uri::class.java)
        val coordinator = MainActivityEwoDocumentCoordinator(
            readDocumentUtf8 = { null },
            onOpenDocumentLoaded = { _, _ -> },
            prepareExportJson = {
                events += "prepare"
                null
            },
            writeDocumentUtf8 = { _, _ ->
                events += "write"
                true
            },
            onSaveCompleted = { fileName ->
                events += "saved:$fileName"
            },
        )

        coordinator.handleSaveResult(null)
        coordinator.handleSaveResult(uri)

        assertEquals(listOf("prepare"), events)
    }

    @Test
    fun handleSaveResult_completesOnlyAfterSuccessfulWrite() {
        val events = mutableListOf<String>()
        val uri = Mockito.mock(Uri::class.java)
        val coordinator = MainActivityEwoDocumentCoordinator(
            readDocumentUtf8 = { null },
            onOpenDocumentLoaded = { _, _ -> },
            prepareExportJson = {
                events += "prepare"
                "ewo-json"
            },
            writeDocumentUtf8 = { _, content ->
                events += "write:$content"
                false
            },
            onSaveCompleted = { fileName ->
                events += "saved:$fileName"
            },
        )

        coordinator.handleSaveResult(uri)

        assertEquals(listOf("prepare", "write:ewo-json"), events)
        assertTrue("Save completion must not run after failed writes", "saved" !in events)
    }

    @Test
    fun handleSaveResult_reportsResolvedFileNameAfterSuccessfulWrite() {
        val events = mutableListOf<String>()
        val uri = Mockito.mock(Uri::class.java)
        Mockito.`when`(uri.lastPathSegment).thenReturn("tempo.ewo")
        val coordinator = MainActivityEwoDocumentCoordinator(
            readDocumentUtf8 = { null },
            resolveOpenedFileName = { openedUri ->
                "saved:${openedUri.lastPathSegment}"
            },
            onOpenDocumentLoaded = { _, _ -> },
            prepareExportJson = { "ewo-json" },
            writeDocumentUtf8 = { _, _ -> true },
            onSaveCompleted = { fileName ->
                events += fileName
            },
        )

        coordinator.handleSaveResult(uri)

        assertEquals(listOf("saved:tempo.ewo"), events)
    }
}
