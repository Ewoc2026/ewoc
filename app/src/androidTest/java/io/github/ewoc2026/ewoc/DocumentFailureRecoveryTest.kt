package io.github.ewoc2026.ewoc

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ewoc2026.ewoc.session.SessionManager
import io.github.ewoc2026.ewoc.session.SessionSummary
import io.github.ewoc2026.ewoc.session.export.FitExportService
import io.github.ewoc2026.ewoc.ui.DebugAutomationTags
import java.io.File
import java.io.FileNotFoundException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Exercises real document-picker callbacks and Android storage after the R1/R4 fixes.
 * Summary metrics are a fixture, not evidence of a trainer session. Only the failing FIT
 * target is delivered directly to the callback: a deleted MediaStore row cannot be picked.
 */
class DocumentFailureRecoveryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val automation get() = instrumentation.uiAutomation
    private val createdUris = mutableListOf<Uri>()
    private var exportedFileName: String? = null
    private var createdFolder = false
    private lateinit var exportState: DocumentsFolderUiState

    @After
    fun removeFixtures() {
        // Do not leave the next activity launch pointing at a fixture deleted by this test.
        WorkoutSelectionSettingsStorage.clear(context)
        createdUris.forEach { context.contentResolver.delete(it, null, null) }
        exportedFileName?.let { shell("rm -f /sdcard/Download/$it") }
        if (createdFolder) {
            SafSharedFolderSettingsStorage.saveTreeUri(context, null)
            context.contentResolver.persistedUriPermissions.filter {
                it.uri.toString().contains("EwocR1R4")
            }.forEach {
                context.contentResolver.releasePersistableUriPermission(it.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            shell("rmdir /sdcard/Documents/EwocR1R4")
        }
    }

    @Test
    fun invalidFormatsThroughPickerShowErrorsAndAllowValidImport() {
        createDownload("r1-array.ewo", """{"format":[]}""")
        createDownload("r1-object.json", """{"format":{}}""")
        createDownload("r1-valid.json", """
            {"format":"ewo","version":"1.0","title":"R1 Android recovery",
             "segments":[{"id":"steady","type":"steady","duration_sec":60,
             "target":{"metric":"power","value":100}}]}
        """.trimIndent())
        composeRule.onNodeWithTag(DebugAutomationTags.MENU_STEP_FILE_BASED).performClick()

        for (name in listOf("r1-array.ewo", "r1-object.json")) {
            importThroughPicker(name)
            composeRule.runOnIdle {
                val vm = viewModel()
                assertEquals(name, vm.uiState.selectedWorkoutFileName.value)
                assertFalse(vm.uiState.workoutReady.value)
                assertNull(vm.uiState.selectedWorkout.value)
                assertNull(vm.uiState.selectedImportedWorkout.value)
                assertFalse(vm.canStartSession())
                assertFalse(vm.uiState.selectedWorkoutImportError.value.isNullOrBlank())
            }
            val error = requireNotNull(viewModel().uiState.selectedWorkoutImportError.value)
            composeRule.onNodeWithText(error, substring = true).performScrollTo().assertIsDisplayed()
            capture(name.substringBefore('.'))
        }

        importThroughPicker("r1-valid.json")
        composeRule.runOnIdle {
            val vm = viewModel()
            assertEquals("r1-valid.json", vm.uiState.selectedWorkoutFileName.value)
            assertNull(vm.uiState.selectedWorkoutImportError.value)
            assertNotNull(vm.uiState.selectedImportedWorkout.value)
            assertEquals(1, vm.uiState.selectedWorkoutStepCount.value)
            assertTrue(vm.uiState.workoutReady.value)
        }
        capture("r1-valid-recovery")
    }

    @Test
    fun deletedDestinationShowsErrorThenPickerCancelAndRetryPreserveSummary() {
        linkTestFolderThroughPicker()
        val summary = SessionSummary(
            startTimestampMillis = 1_705_000_000_000L,
            stopTimestampMillis = 1_705_000_060_000L,
            durationSeconds = 60,
            actualTss = null,
            avgPower = 100,
            maxPower = 100,
            avgCadence = null,
            maxCadence = null,
            avgHeartRate = null,
            maxHeartRate = null,
            distanceMeters = null,
            totalEnergyKcal = null,
        )
        composeRule.runOnIdle {
            val vm = viewModel()
            exportState = MainViewModel::class.java.getDeclaredField("documentsFolderUiState")
                .apply { isAccessible = true }.get(vm) as DocumentsFolderUiState
            // Supply a completed-session fixture without starting Bluetooth, timers, or audio.
            val managerField = MainViewModel::class.java.getDeclaredField("sessionManager")
                .apply { isAccessible = true }
            val manager = managerField.get(vm) as SessionManager
            SessionManager::class.java.getDeclaredField("lastSummary").apply {
                isAccessible = true
                set(manager, summary)
            }
            vm.uiState.summary.value = summary
            vm.onSummaryFitExportPreferenceSelected(FitExportPreference.ASK_EVERY_TIME)
            vm.uiState.screen.value = AppScreen.SUMMARY
            assertNotNull(vm.prepareSessionFitExport())
        }

        val deletedTarget = createDownload("r4-deleted.fit", "")
        assertEquals(1, context.contentResolver.delete(deletedTarget, null, null))
        createdUris.remove(deletedTarget)
        val openError = runCatching {
            context.contentResolver.openOutputStream(deletedTarget, "w")?.close()
        }.exceptionOrNull()
        assertTrue("A real Android provider must reject the deleted target: $openError",
            openError is FileNotFoundException)
        composeRule.runOnIdle {
            viewModel().onSessionFitExportTargetSelected(deletedTarget)
            assertTrue(viewModel().summaryFitExportStatusIsErrorState.value)
            assertEquals(summary, viewModel().uiState.summary.value)
            assertEquals(AppScreen.SUMMARY, viewModel().uiState.screen.value)
        }
        val error = requireNotNull(viewModel().summaryFitExportStatusMessageState.value)
        composeRule.onNodeWithText(error).performScrollTo().assertIsDisplayed()
        capture("r4-open-failure")

        requestExportThroughPicker()
        capture("r4-picker-before-cancel")
        cancelPicker()
        composeRule.runOnIdle {
            assertEquals(summary, viewModel().uiState.summary.value)
            assertNull(viewModel().summaryFitExportStatusMessageState.value)
            assertFalse(viewModel().summaryFitExportStatusIsErrorState.value)
            assertNull(exportState.pendingFitExportSnapshot)
        }
        capture("r4-picker-cancelled")

        exportedFileName = FitExportService.suggestedFileName(summary)
        requestExportThroughPicker()
        openDownloads()
        clickSystemNode { it.text?.toString().equals("Save", ignoreCase = true) }
        waitForApp()
        composeRule.runOnIdle {
            assertEquals(summary, viewModel().uiState.summary.value)
            assertFalse(viewModel().summaryFitExportStatusIsErrorState.value)
            assertEquals(context.getString(R.string.summary_fit_export_success),
                viewModel().summaryFitExportStatusMessageState.value)
        }
        composeRule.onNodeWithText(context.getString(R.string.summary_fit_export_success))
            .performScrollTo().assertIsDisplayed()
        val bytes = shell("cat /sdcard/Download/${exportedFileName}")
        assertTrue("A real destination must contain a FIT payload", bytes.size > 14)
        assertEquals(".FIT", String(bytes.copyOfRange(8, 12), Charsets.US_ASCII))
        val evidence = File(context.getExternalFilesDir(null), "r1-r4-evidence").apply { mkdirs() }
        File(evidence, "recovered-export.fit").writeBytes(bytes)
        capture("r4-retry-success")
    }

    private fun importThroughPicker(name: String) {
        composeRule.onNodeWithTag(DebugAutomationTags.MENU_FILE_SELECT_WORKOUT_FILE)
            .performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.menu_workout_import_browse_files))
            .performClick()
        waitForPicker()
        openDownloads()
        clickSystemNode { it.text?.toString() == name }
        waitForApp()
    }

    private fun requestExportThroughPicker() {
        composeRule.waitUntil(timeoutMillis = 10_000) { composeRule.activity.hasWindowFocus() }
        composeRule.runOnIdle {
            // The app's normal FIT path writes to its linked folder. Use its existing one-shot
            // debug fallback to exercise the real CreateDocument picker and result callback.
            viewModel().armDebugDocumentsFolderWriteFailureOnce()
        }
        composeRule.onNodeWithText(context.getString(R.string.summary_export_fit))
            .performScrollTo().assertIsEnabled().performClick()
        waitForPicker()
    }

    private fun cancelPicker() {
        // Back first traverses folders or dismisses the keyboard. Observe the result callback
        // on the main thread before another Back; the accessibility window cache can lag behind.
        assertTrue(hasPendingExport())
        repeat(5) {
            shell("input keyevent KEYCODE_BACK")
            val deadline = SystemClock.uptimeMillis() + 1_000
            while (SystemClock.uptimeMillis() < deadline) {
                if (!hasPendingExport()) {
                    waitForApp()
                    return
                }
                SystemClock.sleep(50)
            }
        }
        throw AssertionError("The picker did not deliver a cancellation result")
    }

    private fun hasPendingExport(): Boolean {
        var pending = false
        instrumentation.runOnMainSync { pending = exportState.pendingFitExportSnapshot != null }
        return pending
    }

    private fun linkTestFolderThroughPicker() {
        shell("mkdir -p /sdcard/Documents/EwocR1R4")
        createdFolder = true
        composeRule.onNodeWithTag(DebugAutomationTags.MENU_STEP_FILE_BASED).performClick()
        composeRule.onNodeWithTag(DebugAutomationTags.MENU_FILE_LINK_FOLDER)
            .performScrollTo().performClick()
        waitForPicker()
        // A fresh API 33 OpenDocumentTree starts at device storage without a roots drawer.
        clickSystemNode { it.text?.toString() == "Documents" }
        clickSystemNode { it.text?.toString() == "EwocR1R4" }
        clickSystemNode { it.text?.toString().equals("Use this folder", ignoreCase = true) }
        clickSystemNode { it.text?.toString().equals("Allow", ignoreCase = true) }
        waitForApp()
        composeRule.runOnIdle { assertTrue(viewModel().documentsFolderReadyState.value) }
    }

    private fun openDownloads() {
        if (findNode(automation.rootInActiveWindow) { it.text?.toString() == "Downloads" } != null) {
            return
        }
        clickSystemNode { it.contentDescription?.toString() == "Show roots" }
        clickSystemNode { it.text?.toString() == "Downloads" }
    }

    private fun createDownload(name: String, text: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE,
                if (name.endsWith(".json")) "application/json" else "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = requireNotNull(context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        createdUris += uri
        requireNotNull(context.contentResolver.openOutputStream(uri)).use {
            it.write(text.toByteArray())
        }
        return uri
    }

    private fun viewModel() = ViewModelProvider(composeRule.activity)[MainViewModel::class.java]

    private fun waitForPicker() = waitUntil("Android document picker") {
        automation.rootInActiveWindow?.packageName?.toString()?.endsWith("documentsui") == true
    }

    private fun waitForApp() {
        waitUntil("Ewoc activity") {
            automation.rootInActiveWindow?.packageName?.toString() == context.packageName
        }
        composeRule.waitForIdle()
    }

    private fun clickSystemNode(predicate: (AccessibilityNodeInfo) -> Boolean) {
        waitUntil("visible system control") {
            val match = findNode(automation.rootInActiveWindow) {
                predicate(it) && clickableAncestor(it) != null
            } ?: return@waitUntil false
            clickableAncestor(match)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var candidate: AccessibilityNodeInfo? = node
        while (candidate != null) {
            if (candidate.isClickable && candidate.isEnabled) return candidate
            candidate = candidate.parent
        }
        return null
    }

    private fun findNode(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean):
        AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isVisibleToUser && predicate(node)) return node
        for (index in 0 until node.childCount) {
            findNode(node.getChild(index), predicate)?.let { return it }
        }
        return null
    }

    private fun waitUntil(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= deadline) {
                capture("timeout")
                throw AssertionError("Timed out waiting for $label")
            }
            SystemClock.sleep(100)
        }
    }

    private fun shell(command: String): ByteArray =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
            it.readBytes()
        }

    private fun capture(label: String) {
        automation.waitForIdle(250, 2_000)
        val directory = File(context.getExternalFilesDir(null), "r1-r4-evidence").apply { mkdirs() }
        automation.takeScreenshot()?.let { bitmap ->
            File(directory, "$label.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        Log.i("R1R4Validation", "Checkpoint: $label")
    }
}
