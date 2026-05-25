package com.example.ergometerapp

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWorkoutAutomationFilesTest {
    @Test
    fun resolveSelectionTarget_mapsRelativePathIntoStagingDirectory() {
        val stagingDirectory = Files.createTempDirectory("debug-workout-staging").toFile()

        val resolved = DebugWorkoutAutomationFiles.resolveSelectionTarget(
            requestedPath = "tempo/build.ewo",
            stagingDirectory = stagingDirectory,
            appOwnedRoots = emptyList(),
        )

        require(resolved is DebugWorkoutSelectionTarget.Resolved)
        assertEquals(
            File(stagingDirectory, "tempo/build.ewo").canonicalFile,
            resolved.file,
        )
    }

    @Test
    fun resolveSelectionTarget_rejectsAbsolutePathOutsideAppOwnedRoots() {
        val stagingDirectory = Files.createTempDirectory("debug-workout-staging").toFile()
        val sharedStorageFile = Files.createTempFile("debug-workout-download", ".ewo").toFile()

        val resolved = DebugWorkoutAutomationFiles.resolveSelectionTarget(
            requestedPath = sharedStorageFile.absolutePath,
            stagingDirectory = stagingDirectory,
            appOwnedRoots = listOf(stagingDirectory),
        )

        require(resolved is DebugWorkoutSelectionTarget.UnsupportedAbsolutePath)
        assertEquals(sharedStorageFile.canonicalFile, resolved.file)
    }

    @Test
    fun resolveSelectionTarget_keepsAbsolutePathInsideAppOwnedRoots() {
        val appOwnedRoot = Files.createTempDirectory("debug-workout-app-owned").toFile()
        val stagedFile = File(appOwnedRoot, "nested/recovery.ewo").apply {
            parentFile?.mkdirs()
            writeText("dummy")
        }

        val resolved = DebugWorkoutAutomationFiles.resolveSelectionTarget(
            requestedPath = stagedFile.absolutePath,
            stagingDirectory = null,
            appOwnedRoots = listOf(appOwnedRoot),
        )

        require(resolved is DebugWorkoutSelectionTarget.Resolved)
        assertTrue(resolved.file.path.startsWith(appOwnedRoot.canonicalPath))
        assertEquals(stagedFile.canonicalFile, resolved.file)
    }
}
