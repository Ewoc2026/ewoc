package io.github.ewoc2026.ewoc.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPreviewCoverageTest {
    @Test
    fun destinationScreenFilesHaveCompanionPreviews() {
        val uiDirectory = resolveUiDirectory()
        val screenFiles = uiDirectory
            .listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    file.name.endsWith("Screen.kt") &&
                    file.name != "Screens.kt"
            }
            .sortedBy { it.name }

        val failures = mutableListOf<String>()
        for (screenFile in screenFiles) {
            val previewFile = File(
                uiDirectory,
                screenFile.name.removeSuffix(".kt") + "Previews.kt",
            )
            if (!previewFile.isFile) {
                failures += "${screenFile.name} is missing ${previewFile.name}."
                continue
            }

            val screenNames = screenComposableRegex.findAll(screenFile.readText())
                .map { match -> match.groupValues[1] }
                .toList()
            val previewSource = previewFile.readText()
            if (!previewMarkerRegex.containsMatchIn(previewSource)) {
                failures += "${previewFile.name} must declare at least one preview annotation."
            }

            val missingScreenReferences = screenNames.filterNot { screenName ->
                previewSource.contains("$screenName(")
            }
            if (missingScreenReferences.isNotEmpty()) {
                failures += buildString {
                    append(previewFile.name)
                    append(" does not reference screen composables: ")
                    append(missingScreenReferences.joinToString())
                    append(".")
                }
            }
        }

        assertTrue(
            failures.joinToString(separator = "\n"),
            failures.isEmpty(),
        )
    }

    private fun resolveUiDirectory(): File {
        val candidates = listOf(
            File("app/src/main/java/io/github/ewoc2026/ewoc/ui"),
            File("src/main/java/io/github/ewoc2026/ewoc/ui"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate the UI source directory from ${System.getProperty("user.dir")}.")
    }

    private companion object {
        val screenComposableRegex = Regex(
            pattern = """@Composable\s+(?:internal|private|public)?\s*fun\s+(\w+Screen)\s*\(""",
            options = setOf(RegexOption.MULTILINE),
        )
        val previewMarkerRegex = Regex("""@(DestinationScreenPreviews|Preview)\b""")
    }
}
