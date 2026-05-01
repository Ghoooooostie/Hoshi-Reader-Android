package moe.antimony.hoshi.features.audio

import android.database.sqlite.SQLiteDatabase
import java.io.File

class LocalAudioRepository(
    private val filesDir: File,
    private val externalFilesDir: File? = null,
) {
    private val privateDbFile: File
        get() = File(filesDir, AudioSettings.LocalAudioPath)

    val dropInDbFile: File?
        get() = externalFilesDir?.resolve("android.db")

    val dbFile: File
        get() = dropInDbFile?.takeIf { it.isFile } ?: privateDbFile

    fun deleteDatabase() {
        dbFile.delete()
    }

    fun databaseSizeBytes(): Long? =
        dbFile.takeIf { it.isFile }?.length()

    fun findAudio(term: String, reading: String): LocalAudioEntry? {
        if (!dbFile.isFile) return null
        val normalizedReading = LocalAudioResolver.katakanaToHiragana(reading)
        val rows = mutableListOf<LocalAudioEntry>()
        openReadOnlyDatabase().use { db ->
            val args: Array<String>
            val selection: String
            if (normalizedReading.isBlank()) {
                selection = "expression = ? AND file LIKE '%.mp3'"
                args = arrayOf(term)
            } else {
                selection = "(expression = ? OR reading = ?) AND file LIKE '%.mp3'"
                args = arrayOf(term, normalizedReading)
            }
            db.query(
                "entries",
                arrayOf("source", "expression", "reading", "file"),
                selection,
                args,
                null,
                null,
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows += LocalAudioEntry(
                        source = cursor.getString(0),
                        expression = cursor.getString(1),
                        reading = cursor.getString(2),
                        file = cursor.getString(3),
                    )
                }
            }
        }
        return LocalAudioResolver.resolve(term, normalizedReading, rows)
    }

    fun loadAudio(file: LocalAudioFile): ByteArray? {
        if (!dbFile.isFile) return null
        openReadOnlyDatabase().use { db ->
            db.query(
                "android",
                arrayOf("data"),
                "source = ? AND file = ?",
                arrayOf(file.source, file.file),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                return cursor.getBlob(0)
            }
        }
    }

    private fun openReadOnlyDatabase(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
}
