package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityDocumentFlowCoordinatorTest {
    @Test
    fun blankEwoTitleFallsBackToWorkoutFileName() {
        var launchedFileName: String? = null
        val coordinator = MainActivityDocumentFlowCoordinator(
            launchEwoSaveDocument = { fileName ->
                launchedFileName = fileName
            },
            prepareSessionFitExport = { null },
            tryExportPendingSessionFitToDocumentsFolder = { true },
            launchSessionFitExportDocument = {},
        )

        coordinator.requestEwoSave("")

        assertEquals("workout.ewo", launchedFileName)
    }

    @Test
    fun summaryFitExportLaunchesPickerOnlyWhenFolderWriteNeedsFallback() {
        val launchRequests = mutableListOf<String>()
        val coordinator = MainActivityDocumentFlowCoordinator(
            launchEwoSaveDocument = {},
            prepareSessionFitExport = { "session.fit" },
            tryExportPendingSessionFitToDocumentsFolder = { false },
            launchSessionFitExportDocument = { fileName ->
                launchRequests += fileName
            },
        )

        coordinator.requestSummaryFitExport()

        assertEquals(listOf("session.fit"), launchRequests)
    }
}
