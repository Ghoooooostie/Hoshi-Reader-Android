package de.manhhao.hoshi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
}
