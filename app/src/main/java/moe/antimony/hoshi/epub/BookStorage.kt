package moe.antimony.hoshi.epub

import android.content.ContentResolver
import android.net.Uri
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.zip.ZipInputStream

@Serializable
data class Bookmark(
    val chapterIndex: Int,
    val progress: Double,
    val characterCount: Int,
    val lastModified: Double? = null,
)

class BookStorage(private val filesDir: File) {
    private val booksDirectory = File(filesDir, "Books")
    val currentBookFile: File = File(booksDirectory, "current.epub")
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "    "
        encodeDefaults = true
    }

    fun loadAllBooks(): List<File> =
        if (currentBookFile.isDirectory) listOf(currentBookFile) else emptyList()

    fun loadBookmark(bookRoot: File): Bookmark? {
        val file = bookRoot.resolve(BOOKMARK_FILE_NAME)
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<Bookmark>(file.readText()) }.getOrNull()
    }

    fun saveBookmark(bookRoot: File, bookmark: Bookmark) {
        bookRoot.mkdirs()
        bookRoot.resolve(BOOKMARK_FILE_NAME).writeText(json.encodeToString(bookmark))
    }

    fun currentAppleReferenceDateSeconds(): Double {
        val now = Instant.now()
        return now.epochSecond.toDouble() + (now.nano.toDouble() / 1_000_000_000.0) - APPLE_REFERENCE_EPOCH_SECONDS
    }

    fun importBook(contentResolver: ContentResolver, uri: Uri): File {
        booksDirectory.mkdirs()
        if (currentBookFile.exists()) {
            currentBookFile.deleteRecursively()
        }
        currentBookFile.mkdirs()
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected EPUB" }
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val output = currentBookFile.resolve(entry.name).canonicalFile
                    val root = currentBookFile.canonicalFile
                    require(output.path == root.path || output.path.startsWith(root.path + File.separator)) {
                        "Unsafe EPUB entry: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        output.outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return currentBookFile
    }

    private companion object {
        const val BOOKMARK_FILE_NAME = "bookmark.json"
        const val APPLE_REFERENCE_EPOCH_SECONDS = 978_307_200.0
    }
}
