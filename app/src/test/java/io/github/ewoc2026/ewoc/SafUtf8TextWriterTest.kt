package io.github.ewoc2026.ewoc

import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class SafUtf8TextWriterTest {
    @Test
    fun write_usesTextWriteModeAndUtf8Encoding() {
        val uri = Mockito.mock(Uri::class.java)
        val output = ByteArrayOutputStream()
        var capturedMode: String? = null
        val writer = SafUtf8TextWriter { _, mode ->
            capturedMode = mode
            output
        }

        val writeSucceeded = writer.write(uri, "teho-alue")

        assertTrue(writeSucceeded)
        assertEquals("wt", capturedMode)
        assertEquals("teho-alue", output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun write_returnsFalseWhenOutputStreamIsUnavailable() {
        val uri = Mockito.mock(Uri::class.java)
        val writer = SafUtf8TextWriter { _, _ -> null }

        val writeSucceeded = writer.write(uri, "content")

        assertFalse(writeSucceeded)
    }

    @Test
    fun write_returnsFalseWhenStreamWriteThrows() {
        val uri = Mockito.mock(Uri::class.java)
        val writer = SafUtf8TextWriter { _, _ ->
            object : OutputStream() {
                override fun write(b: Int) {
                    throw IOException("boom")
                }
            }
        }

        val writeSucceeded = writer.write(uri, "content")

        assertFalse(writeSucceeded)
    }
}
