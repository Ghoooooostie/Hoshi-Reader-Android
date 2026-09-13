package moe.antimony.hoshi.importing

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File

private const val MaxCopyDepth = 16

internal data class ImportDirectoryDocument<T>(
    val key: T,
    val name: String,
    val isDirectory: Boolean,
    val isVirtual: Boolean,
)

internal data class ImportDirectoryFile<T>(
    val key: T,
    val displayName: String,
)

internal interface ImportDirectoryTree<T> {
    fun children(directoryKey: T): List<ImportDirectoryDocument<T>>
}

internal class ImportDirectoryScanner<T>(
    private val tree: ImportDirectoryTree<T>,
) {
    fun scan(rootKey: T, type: ImportFileType): List<ImportDirectoryFile<T>> =
        buildList {
            collect(directoryKey = rootKey, prefix = "", type = type, output = this)
        }.sortedWith(
            compareBy<ImportDirectoryFile<T>> { it.displayName.lowercase() }
                .thenBy { it.displayName },
        )

    private fun collect(
        directoryKey: T,
        prefix: String,
        type: ImportFileType,
        output: MutableList<ImportDirectoryFile<T>>,
    ) {
        tree.children(directoryKey).forEach { child ->
            val displayName = child.name.takeIf { it.isNotBlank() } ?: return@forEach
            val relativeName = if (prefix.isBlank()) displayName else "$prefix/$displayName"
            when {
                child.isVirtual -> Unit
                child.isDirectory -> collect(
                    directoryKey = child.key,
                    prefix = relativeName,
                    type = type,
                    output = output,
                )
                type.matchesDisplayName(displayName) -> output += ImportDirectoryFile(
                    key = child.key,
                    displayName = relativeName,
                )
            }
        }
    }
}

internal class SafImportDirectoryScanner(
    private val contentResolver: ContentResolver,
) {
    fun scan(treeUri: Uri, type: ImportFileType): List<ImportDirectoryFile<Uri>> {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        return ImportDirectoryScanner(SafImportDirectoryTree(contentResolver, treeUri)).scan(rootDocumentUri, type)
    }

    fun scanFolder(
        treeUri: Uri,
        type: ImportFileType,
        isImportedDirectory: (List<String>) -> Boolean,
    ): List<ImportFolderEntry<Uri>> {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        val tree = SafImportDirectoryTree(contentResolver, treeUri)
        return ImportFolderScanner(tree).scan(
            rootKey = rootDocumentUri,
            rootDisplayName = tree.displayName(rootDocumentUri) ?: rootDocumentId.substringAfterLast('/'),
            archiveType = type,
            isImportedDirectory = isImportedDirectory,
        )
    }
}

internal class SafDirectoryCopier(
    private val contentResolver: ContentResolver,
) {
    fun copy(documentUri: Uri, target: File) {
        copy(documentUri, target, depth = 0)
    }

    private fun copy(documentUri: Uri, target: File, depth: Int) {
        require(depth <= MaxCopyDepth) { "Folder is nested too deeply." }
        val documentId = DocumentsContract.getDocumentId(documentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(documentUri, documentId)
        target.mkdirs()
        contentResolver.query(childrenUri, ChildProjection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val childDocumentId = cursor.string(Document.COLUMN_DOCUMENT_ID) ?: continue
                val name = cursor.string(Document.COLUMN_DISPLAY_NAME) ?: childDocumentId.substringAfterLast('/')
                val fileName = name.toSafeFileName() ?: continue
                val childUri = DocumentsContract.buildDocumentUriUsingTree(documentUri, childDocumentId)
                val childTarget = File(target, fileName)
                if (cursor.string(Document.COLUMN_MIME_TYPE) == Document.MIME_TYPE_DIR) {
                    copy(childUri, childTarget, depth + 1)
                } else {
                    contentResolver.openInputStream(childUri).use { input ->
                        requireNotNull(input) { "Unable to read $fileName." }
                        childTarget.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}

internal enum class ImportFolderEntryKind {
    Archive,
    ImportedDirectory,
}

internal data class ImportFolderEntry<T>(
    val key: T,
    val displayName: String,
    val kind: ImportFolderEntryKind,
)

internal class ImportFolderScanner<T>(
    private val tree: ImportDirectoryTree<T>,
) {
    /**
     * Collects archives matching [archiveType] and already imported resource directories.
     * [isImportedDirectory] receives the child names of a directory and reports whether it is
     * an already imported resource that must be imported as a whole instead of being descended.
     */
    fun scan(
        rootKey: T,
        rootDisplayName: String,
        archiveType: ImportFileType,
        isImportedDirectory: (List<String>) -> Boolean,
    ): List<ImportFolderEntry<T>> {
        if (isImportedDirectory(tree.childNames(rootKey))) {
            return listOf(ImportFolderEntry(rootKey, rootDisplayName, ImportFolderEntryKind.ImportedDirectory))
        }
        return buildList {
            collect(
                directoryKey = rootKey,
                prefix = "",
                archiveType = archiveType,
                isImportedDirectory = isImportedDirectory,
                output = this,
            )
        }.sortedWith(compareBy<ImportFolderEntry<T>> { it.displayName.lowercase() }.thenBy { it.displayName })
    }

    private fun collect(
        directoryKey: T,
        prefix: String,
        archiveType: ImportFileType,
        isImportedDirectory: (List<String>) -> Boolean,
        output: MutableList<ImportFolderEntry<T>>,
    ) {
        tree.children(directoryKey).forEach { child ->
            val displayName = child.name.takeIf { it.isNotBlank() } ?: return@forEach
            val relativeName = if (prefix.isBlank()) displayName else "$prefix/$displayName"
            when {
                child.isVirtual -> Unit
                child.isDirectory -> {
                    if (isImportedDirectory(tree.childNames(child.key))) {
                        output += ImportFolderEntry(child.key, relativeName, ImportFolderEntryKind.ImportedDirectory)
                    } else {
                        collect(
                            directoryKey = child.key,
                            prefix = relativeName,
                            archiveType = archiveType,
                            isImportedDirectory = isImportedDirectory,
                            output = output,
                        )
                    }
                }
                archiveType.matchesDisplayName(displayName) -> output += ImportFolderEntry(
                    key = child.key,
                    displayName = relativeName,
                    kind = ImportFolderEntryKind.Archive,
                )
            }
        }
    }
}

private fun <T> ImportDirectoryTree<T>.childNames(directoryKey: T): List<String> =
    children(directoryKey).map { it.name }

internal class SafImportDirectoryTree(
    private val contentResolver: ContentResolver,
    private val treeUri: Uri,
) : ImportDirectoryTree<Uri> {
    fun displayName(documentUri: Uri): String? =
        contentResolver.query(documentUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.string(Document.COLUMN_DISPLAY_NAME) else null }

    override fun children(directoryKey: Uri): List<ImportDirectoryDocument<Uri>> {
        val directoryDocumentId = DocumentsContract.getDocumentId(directoryKey)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryDocumentId)
        return contentResolver.query(
            childrenUri,
            ChildProjection,
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val documentId = cursor.string(Document.COLUMN_DOCUMENT_ID) ?: continue
                    val name = cursor.string(Document.COLUMN_DISPLAY_NAME)
                        ?: documentId.substringAfterLast('/')
                    val mimeType = cursor.string(Document.COLUMN_MIME_TYPE)
                    val flags = cursor.int(Document.COLUMN_FLAGS)
                    add(
                        ImportDirectoryDocument(
                            key = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            name = name,
                            isDirectory = mimeType == Document.MIME_TYPE_DIR,
                            isVirtual = flags and Document.FLAG_VIRTUAL_DOCUMENT != 0,
                        ),
                    )
                }
            }
        }.orEmpty()
    }
}

private val ChildProjection = arrayOf(
    Document.COLUMN_DOCUMENT_ID,
    Document.COLUMN_DISPLAY_NAME,
    Document.COLUMN_MIME_TYPE,
    Document.COLUMN_FLAGS,
)

private fun Cursor.string(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private fun Cursor.int(columnName: String): Int {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getInt(index) else 0
}

private fun String.toSafeFileName(): String? {
    val name = trim()
    if (name.isEmpty() || name == "." || name == "..") return null
    if (name.any { it == '/' || it == '\\' || it.code < 0x20 }) return null
    return name
}

