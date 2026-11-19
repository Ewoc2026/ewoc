package io.github.ewoc2026.ewoc.session.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.github.ewoc2026.ewoc.session.SessionSummary
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/**
 * Calls the production FIT exporter while replacing only the Android destination boundary.
 * Open and write failures should reach callers through the export result contract.
 */
class FitExportFailureBoundaryTest {
    private val context = Mockito.mock(Context::class.java)
    private val resolver = Mockito.mock(ContentResolver::class.java)
    private val uri = Mockito.mock(Uri::class.java)

    init {
        Mockito.`when`(context.contentResolver).thenReturn(resolver)
    }

    @Test
    fun destinationOpenExceptionReturnsFailure() {
        Mockito.`when`(resolver.openOutputStream(uri, "w"))
            .thenThrow(FileNotFoundException("R4: destination unavailable"))

        val result = FitExportService.exportToUri(context, uri, snapshot())

        assertTrue("Opening the destination should return an export failure", result is FitExportResult.Failure)
    }

    @Test
    fun interruptedWriteReturnsFailureAndClosesStream() {
        val output = object : OutputStream() {
            var bytesWritten = 0
            var closed = false

            override fun write(value: Int) {
                if (bytesWritten == 8) throw IOException("R4: write interrupted")
                bytesWritten += 1
            }

            override fun close() {
                closed = true
            }
        }
        Mockito.`when`(resolver.openOutputStream(uri, "w")).thenReturn(output)

        val result = FitExportService.exportToUri(context, uri, snapshot())

        assertEquals(
            FitExportResult.Failure(FitExportFailureReason.WRITE_FAILED, "R4: write interrupted"),
            result,
        )
        assertEquals(8, output.bytesWritten)
        assertTrue("A failed write must still close the stream", output.closed)
    }

    @Test
    fun writableDestinationReceivesPayloadAndClosesStream() {
        val output = object : ByteArrayOutputStream() {
            var closed = false

            override fun close() {
                closed = true
                super.close()
            }
        }
        Mockito.`when`(resolver.openOutputStream(uri, "w")).thenReturn(output)

        val result = FitExportService.exportToUri(context, uri, snapshot())

        assertEquals(FitExportResult.Success, result)
        assertTrue("A successful export must write a payload", output.size() > 0)
        assertTrue("A successful export must close the stream", output.closed)
        Mockito.verify(resolver).openOutputStream(uri, "w")
    }

    private fun snapshot() = SessionExportSnapshot(
        summary = SessionSummary(
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
        ),
        timeline = emptyList(),
    )
}
