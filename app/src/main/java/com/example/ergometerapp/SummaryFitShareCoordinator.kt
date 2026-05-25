package com.example.ergometerapp

import android.content.Intent
import android.net.Uri
import com.example.ergometerapp.session.export.FitExportFailureReason
import com.example.ergometerapp.session.export.FitExportResult
import com.example.ergometerapp.session.export.SessionExportSnapshot
import java.io.File

/**
 * ViewModel-owned state bridge for summary FIT share flows.
 *
 * Invariants:
 * - Share preparation updates the same status channel as document export so SUMMARY actions stay aligned.
 * - Documents-folder status is only cleared after a ready chooser intent exists.
 */
internal interface SummaryFitShareStatePort {
    var statusMessage: String?
    var statusIsError: Boolean
}

/**
 * Result returned by [SummaryFitShareCoordinator.prepareShare].
 */
internal sealed interface SummaryFitSharePreparation {
    data class Ready(val chooserIntent: Intent) : SummaryFitSharePreparation
    data class ExportFailure(val failure: FitExportResult.Failure) : SummaryFitSharePreparation
    data object ShareUriUnavailable : SummaryFitSharePreparation
}

/**
 * Coordinates summary `.fit` share preparation through cache export and share-intent creation.
 *
 * Invariants:
 * - Share export always writes a `.fit` file into app cache before exposing a content URI.
 * - FIT timestamp validity guard matches document-export behavior.
 * - User-facing share/export status mapping stays centralized so `MainViewModel` only triggers the seam.
 */
internal class SummaryFitShareCoordinator(
    private val suggestFileName: (SessionExportSnapshot) -> String,
    private val noSummaryMessage: () -> String,
    private val exportFailureMessage: (FitExportResult.Failure) -> String,
    private val shareUriUnavailableMessage: () -> String,
    private val launchFailureMessage: () -> String,
    private val cacheDirectoryProvider: () -> File,
    private val buildFitBytes: (SessionExportSnapshot) -> ByteArray,
    private val resolveShareUri: (File) -> Uri?,
    private val createChooserIntent: (Uri) -> Intent,
    private val clearDocumentsFolderStatus: () -> Unit,
) {
    fun prepareShareIntent(
        snapshot: SessionExportSnapshot?,
        statePort: SummaryFitShareStatePort,
    ): Intent? {
        if (snapshot == null) {
            applyStatus(
                statePort = statePort,
                message = noSummaryMessage(),
                isError = true,
            )
            return null
        }

        clearStatus(statePort)
        val suggestedFileName = suggestFileName(snapshot)
        return when (
            val sharePreparation = prepareShare(
                snapshot = snapshot,
                suggestedFileName = suggestedFileName,
            )
        ) {
            is SummaryFitSharePreparation.Ready -> {
                clearDocumentsFolderStatus()
                sharePreparation.chooserIntent
            }

            is SummaryFitSharePreparation.ExportFailure -> {
                applyStatus(
                    statePort = statePort,
                    message = exportFailureMessage(sharePreparation.failure),
                    isError = true,
                )
                null
            }

            SummaryFitSharePreparation.ShareUriUnavailable -> {
                applyStatus(
                    statePort = statePort,
                    message = shareUriUnavailableMessage(),
                    isError = true,
                )
                null
            }
        }
    }

    fun onShareLaunchFailed(statePort: SummaryFitShareStatePort) {
        applyStatus(
            statePort = statePort,
            message = launchFailureMessage(),
            isError = true,
        )
    }

    private fun prepareShare(
        snapshot: SessionExportSnapshot,
        suggestedFileName: String,
    ): SummaryFitSharePreparation {
        if (snapshot.summary.stopTimestampMillis < snapshot.summary.startTimestampMillis) {
            return SummaryFitSharePreparation.ExportFailure(
                FitExportResult.Failure(FitExportFailureReason.INVALID_TIMESTAMPS),
            )
        }

        val cacheDirectory = cacheDirectoryProvider()
        val targetFile = File(
            cacheDirectory,
            SafFileNamePolicy.ensureExtension(suggestedFileName, ".fit"),
        )

        val payload = runCatching { buildFitBytes(snapshot) }
            .getOrElse { throwable ->
                return SummaryFitSharePreparation.ExportFailure(
                    FitExportResult.Failure(
                        reason = FitExportFailureReason.WRITE_FAILED,
                        detail = throwable.message,
                    ),
                )
            }

        val writeResult = runCatching {
            targetFile.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            targetFile.outputStream().use { stream ->
                stream.write(payload)
            }
        }
        if (writeResult.isFailure) {
            return SummaryFitSharePreparation.ExportFailure(
                FitExportResult.Failure(
                    reason = FitExportFailureReason.WRITE_FAILED,
                    detail = writeResult.exceptionOrNull()?.message,
                ),
            )
        }

        val shareUri = resolveShareUri(targetFile)
            ?: return SummaryFitSharePreparation.ShareUriUnavailable
        return SummaryFitSharePreparation.Ready(
            chooserIntent = createChooserIntent(shareUri),
        )
    }

    private fun clearStatus(statePort: SummaryFitShareStatePort) {
        statePort.statusMessage = null
        statePort.statusIsError = false
    }

    private fun applyStatus(
        statePort: SummaryFitShareStatePort,
        message: String?,
        isError: Boolean,
    ) {
        statePort.statusMessage = message
        statePort.statusIsError = isError
    }
}
