package moe.antimony.hoshi.features.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class LocalAudioRepositoryTest {
    @Test
    fun exposesAnkiConnectCompatibleDropInDatabasePath() {
        val filesDir = Files.createTempDirectory("hoshi-local-audio-internal").toFile()
        val externalFilesDir = Files.createTempDirectory("hoshi-local-audio-external").toFile()
        val repository = LocalAudioRepository(filesDir, externalFilesDir)

        assertEquals(externalFilesDir.resolve("android.db"), repository.dropInDbFile)
        assertNull(repository.databaseSizeBytes())
    }

    @Test
    fun usesAnkiConnectCompatibleExternalDatabaseWhenPresent() {
        val filesDir = Files.createTempDirectory("hoshi-local-audio-internal").toFile()
        val externalFilesDir = Files.createTempDirectory("hoshi-local-audio-external").toFile()
        val externalDatabase = externalFilesDir.resolve("android.db")
        externalDatabase.writeBytes("external database".toByteArray())

        val repository = LocalAudioRepository(filesDir, externalFilesDir)

        assertEquals(externalDatabase, repository.dbFile)
        assertEquals(externalDatabase.length(), repository.databaseSizeBytes())
    }

    @Test
    fun fallsBackToLegacyPrivateDatabaseWhenDropInDatabaseIsMissing() {
        val filesDir = Files.createTempDirectory("hoshi-local-audio-internal").toFile()
        val externalFilesDir = Files.createTempDirectory("hoshi-local-audio-external").toFile()
        val legacyDatabase = filesDir.resolve(AudioSettings.LocalAudioPath)
        legacyDatabase.parentFile?.mkdirs()
        legacyDatabase.writeBytes("legacy database".toByteArray())

        val repository = LocalAudioRepository(filesDir, externalFilesDir)

        assertEquals(legacyDatabase, repository.dbFile)
        assertEquals(legacyDatabase.length(), repository.databaseSizeBytes())
    }
}
