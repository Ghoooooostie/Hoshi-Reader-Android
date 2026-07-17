package de.manhhao.hoshi

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoshiDictsCompatibilityTest {
    @Test
    fun legacyPitchEntryConstructorDefaultsTranscriptions() {
        val entry = PitchEntry(
            dictName = "JMdict",
            pitchPositions = intArrayOf(1, 2),
        )

        assertArrayEquals(emptyArray<String>(), entry.transcriptions)
    }

    @Test
    fun legacyLookupResultConstructorMapsToAlgorithmTraceCandidate() {
        val result = LookupResult(
            matched = "食べる",
            deinflected = "食べる",
            process = arrayOf(TransformGroup(name = "v1", description = "一段动词")),
            term = TermResult(
                expression = "食べる",
                reading = "たべる",
                rules = "",
                glossaries = emptyArray(),
                frequencies = emptyArray(),
                pitches = emptyArray(),
            ),
            preprocessorSteps = 2,
        )

        assertEquals("食べる", result.matched)
        assertEquals(1, result.traceCandidates.size)
        assertEquals(TraceSource.ALGORITHM, result.traceCandidates.single().source)
        assertEquals(2, result.traceCandidates.single().preprocessorSteps)
        assertEquals("食べる", result.traceCandidates.single().deinflected)
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
