package de.manhhao.hoshi

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoshiDictsCompatibilityTest {
    @Test
    fun pitchEntryCarriesCompletePitchData() {
        val entry = PitchEntry(
            dictName = "JMdict",
            pitches = arrayOf(
                Pitch(
                    position = 1,
                    pattern = "LHL",
                    nasal = intArrayOf(2),
                    devoice = intArrayOf(3),
                ),
            ),
            transcriptions = arrayOf("tabe"),
        )

        assertEquals(1, entry.pitches.single().position)
        assertEquals("LHL", entry.pitches.single().pattern)
        assertEquals("tabe", entry.transcriptions.single())
    }

    @Test
    fun createLookupObjectBindingMatchesNativeLanguageSignature() {
        val bridgeSource = sourceFile(
            "src/main/java/de/manhhao/hoshi/HoshiDicts.kt",
            "app/src/main/java/de/manhhao/hoshi/HoshiDicts.kt",
        ).readText()
        val submoduleSource = sourceFile(
            "../third_party/hoshidicts-kotlin-bridge/app/src/main/java/de/manhhao/hoshi/HoshiDicts.kt",
            "third_party/hoshidicts-kotlin-bridge/app/src/main/java/de/manhhao/hoshi/HoshiDicts.kt",
        ).readText()

        assertTrue(submoduleSource.contains("external fun createLookupObject(languageId: String): Long"))
        assertTrue(bridgeSource.contains("external fun createLookupObject(languageId: String): Long"))
        assertFalse(bridgeSource.contains("private external fun createLookupObject(): Long"))
    }

    private fun sourceFile(vararg candidates: String): File =
        candidates.map(::File).firstOrNull(File::isFile)
            ?: error("Could not find source file. Tried: ${candidates.joinToString()}")
}
