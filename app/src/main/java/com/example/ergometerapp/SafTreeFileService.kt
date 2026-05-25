package com.example.ergometerapp

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tree-scoped SAF file operations for shared workout/FIT storage.
 */
object SafTreeFileService {
    private val conflictTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd_HH-mm-ss",
        Locale.US,
    )

    /**
     * One workout file candidate discovered under a selected documents tree.
     */
    data class WorkoutTreeFile(
        val displayName: String,
        val uri: Uri,
    )

    /**
     * Verifies read/write persistable permission and basic folder accessibility.
     */
    fun hasReadWriteAccess(context: Context, treeUri: Uri): Boolean {
        val persisted = context.contentResolver.persistedUriPermissions.firstOrNull {
            it.uri == treeUri
        } ?: return false
        if (!persisted.isReadPermission || !persisted.isWritePermission) return false
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        return root.exists() && root.isDirectory && root.canRead() && root.canWrite()
    }

    /**
     * Returns a user-facing folder label when the tree is reachable.
     */
    fun resolveFolderLabel(context: Context, treeUri: Uri): String? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        return root.name?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Lists supported workout import files (`.zwo`, `.xml`, and `.ewo`) from the selected tree.
     *
     * Results are sorted by display name.
     */
    fun listWorkoutFiles(context: Context, treeUri: Uri): List<WorkoutTreeFile> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        if (!root.exists() || !root.isDirectory || !root.canRead()) return emptyList()
        return root.listFiles()
            .asSequence()
            .filter { file ->
                file.isFile &&
                    file.canRead() &&
                    SafFileNamePolicy.isSupportedWorkoutImportFileName(file.name)
            }
            .mapNotNull { file ->
                val name = file.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                WorkoutTreeFile(displayName = name, uri = file.uri)
            }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }

    /**
     * Creates a writable document URI under [treeUri] using conflict-safe naming.
     */
    internal fun createWritableDocumentUri(
        context: Context,
        treeUri: Uri,
        preferredFileName: String,
        mimeType: String,
        existingFilePolicy: DocumentsFolderExistingFilePolicy =
            DocumentsFolderExistingFilePolicy.CREATE_COPY_ON_CONFLICT,
    ): Uri? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (!root.exists() || !root.isDirectory || !root.canWrite()) return null

        val existingFiles = root.listFiles()
        val normalizedPreferredName = preferredFileName.trim().ifBlank { return null }
        if (existingFilePolicy == DocumentsFolderExistingFilePolicy.OVERWRITE_EXISTING) {
            val matchingFile = existingFiles.firstOrNull { file ->
                file.isFile &&
                    file.name?.trim()?.equals(normalizedPreferredName, ignoreCase = true) == true
            }
            if (matchingFile != null) {
                return matchingFile.takeIf { it.canWrite() }?.uri
            }
            return root.createFile(mimeType, normalizedPreferredName)?.uri
        }

        val existingNames = existingFiles
            .mapNotNull { it.name?.trim()?.takeIf { name -> name.isNotEmpty() } }
            .toSet()
        val timestamp = LocalDateTime.now().format(conflictTimestampFormatter)
        val fileName = SafFileNamePolicy.resolveConflictSafeFileName(
            preferredFileName = normalizedPreferredName,
            timestampSuffix = timestamp,
            existingFileNames = existingNames,
        )
        return root.createFile(mimeType, fileName)?.uri
    }
}
