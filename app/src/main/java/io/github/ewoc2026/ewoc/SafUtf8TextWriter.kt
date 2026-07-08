package io.github.ewoc2026.ewoc

import android.net.Uri
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Writes UTF-8 text through SAF document URIs behind one reusable seam.
 *
 * This keeps editor export/save flows aligned on the same write mode and encoding so
 * ViewModel and Activity callers do not duplicate output-stream handling details.
 */
internal class SafUtf8TextWriter(
    private val openOutputStream: (Uri, String) -> OutputStream?,
) {
    fun write(uri: Uri, content: String): Boolean {
        return runCatching {
            openOutputStream(uri, "wt")?.use { output ->
                OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                    writer.write(content)
                }
            } ?: throw IllegalStateException("Output stream unavailable")
        }.isSuccess
    }
}
