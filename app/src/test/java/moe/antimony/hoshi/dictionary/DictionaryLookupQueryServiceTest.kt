package moe.antimony.hoshi.dictionary

import de.manhhao.hoshi.DictionaryStyle
import de.manhhao.hoshi.GlossaryEntry
import de.manhhao.hoshi.LookupResult
import de.manhhao.hoshi.KanjiEntry
import de.manhhao.hoshi.KanjiResult
import de.manhhao.hoshi.TermResult
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryLookupQueryServiceTest {
    @Test
    fun rebuildForwardsEnabledPathsByDictionaryTypeToNativeBridge() {
        val bridge = RecordingDictionaryNativeBridge()
        val service = DictionaryLookupQueryService(bridge)
        val termPath = File("/dicts/Term/JMdict").absolutePath
        val frequencyPath = File("/dicts/Frequency/Freq").absolutePath
        val pitchPath = File("/dicts/Pitch/Pitch").absolutePath
        val kanjiPath = File("/dicts/Kanji/KANJIDIC").absolutePath

        service.rebuild(
            termDictionaries = listOf(File(termPath)),
            frequencyDictionaries = listOf(File(frequencyPath)),
            pitchDictionaries = listOf(File(pitchPath)),
            kanjiDictionaries = listOf(File(kanjiPath)),
            dictionaryLanguageId = "en",
        )

        assertEquals(listOf("en"), bridge.createdLanguageIds)
        assertArrayEquals(arrayOf(termPath), bridge.termPaths)
        assertArrayEquals(arrayOf(frequencyPath), bridge.freqPaths)
        assertArrayEquals(arrayOf(pitchPath), bridge.pitchPaths)
        assertArrayEquals(arrayOf(kanjiPath), bridge.kanjiPaths)
    }

    @Test
    fun rebuildPublishesNewQueryWithoutMutatingCurrentQueryInPlace() {
        val bridge = RecordingDictionaryNativeBridge()
        val service = DictionaryLookupQueryService(bridge)
        val oldPath = File("/dicts/Term/Old").absolutePath
        val newPath = File("/dicts/Term/New").absolutePath

        service.rebuild(
            termDictionaries = listOf(File(oldPath)),
            frequencyDictionaries = emptyList(),
            pitchDictionaries = emptyList(),
            kanjiDictionaries = emptyList(),
            dictionaryLanguageId = "ja",
        )
        val oldResult = service.lookup("食べる").single().term.glossaries.single().glossary

        service.rebuild(
            termDictionaries = listOf(File(newPath)),
            frequencyDictionaries = emptyList(),
            pitchDictionaries = emptyList(),
            kanjiDictionaries = emptyList(),
            dictionaryLanguageId = "en",
        )

        assertEquals("session-1:$oldPath", oldResult)
        assertEquals("session-2:$newPath", service.lookup("食べる").single().term.glossaries.single().glossary)
        assertEquals(listOf("ja", "en"), bridge.createdLanguageIds)
        assertEquals(listOf(1L), bridge.destroyedSessions)
    }

    @Test
    fun failedRebuildKeepsCurrentQueryAvailable() {
        val bridge = RecordingDictionaryNativeBridge()
        val service = DictionaryLookupQueryService(bridge)
        val stablePath = File("/dicts/Term/Stable").absolutePath
        val brokenPath = File("/dicts/Term/Broken").absolutePath

        service.rebuild(
            termDictionaries = listOf(File(stablePath)),
            frequencyDictionaries = emptyList(),
            pitchDictionaries = emptyList(),
            kanjiDictionaries = emptyList(),
            dictionaryLanguageId = "ja",
        )
        bridge.failNextRebuild = true

        val failure = runCatching {
            service.rebuild(
                termDictionaries = listOf(File(brokenPath)),
                frequencyDictionaries = emptyList(),
                pitchDictionaries = emptyList(),
                kanjiDictionaries = emptyList(),
                dictionaryLanguageId = "en",
            )
        }

        assertTrue(failure.isFailure)
        assertEquals("session-1:$stablePath", service.lookup("食べる").single().term.glossaries.single().glossary)
        assertEquals(listOf(2L), bridge.destroyedSessions)
    }

    @Test
    fun rebuildDoesNotDestroyPreviousQueryWhileLookupIsReadingIt() {
        val lookupStarted = CountDownLatch(1)
        val releaseLookup = CountDownLatch(1)
        val oldPath = File("/dicts/Term/Old").absolutePath
        val newPath = File("/dicts/Term/New").absolutePath
        val bridge = RecordingDictionaryNativeBridge(
            onLookup = { session ->
                if (session == 1L) {
                    lookupStarted.countDown()
                    assertTrue(releaseLookup.await(5, TimeUnit.SECONDS))
                }
            },
        )
        val service = DictionaryLookupQueryService(bridge)
        service.rebuild(
            termDictionaries = listOf(File(oldPath)),
            frequencyDictionaries = emptyList(),
            pitchDictionaries = emptyList(),
            kanjiDictionaries = emptyList(),
            dictionaryLanguageId = "ja",
        )

        val lookupThread = thread(start = true) {
            service.lookup("食べる")
        }
        assertTrue(lookupStarted.await(5, TimeUnit.SECONDS))

        val rebuildThread = thread(start = true) {
            service.rebuild(
                termDictionaries = listOf(File(newPath)),
                frequencyDictionaries = emptyList(),
                pitchDictionaries = emptyList(),
                kanjiDictionaries = emptyList(),
                dictionaryLanguageId = "en",
            )
        }
        Thread.sleep(100)

        assertFalse(bridge.destroyedSessions.contains(1L))
        releaseLookup.countDown()
        lookupThread.join(5_000)
        rebuildThread.join(5_000)
        assertEquals(listOf(1L), bridge.destroyedSessions)
        assertEquals("session-2:$newPath", service.lookup("食べる").single().term.glossaries.single().glossary)
    }

    @Test
    fun queryKanjiUsesCurrentAtomicSession() {
        val bridge = RecordingDictionaryNativeBridge()
        val service = DictionaryLookupQueryService(bridge)
        service.rebuild(
            termDictionaries = emptyList(),
            frequencyDictionaries = emptyList(),
            pitchDictionaries = emptyList(),
            kanjiDictionaries = listOf(File("/dicts/Kanji/KANJIDIC")),
        )

        val result = service.queryKanji("星")

        assertEquals("星", result.character)
        assertEquals("session-1:/dicts/Kanji/KANJIDIC", result.entries.single().definitions.single())
    }

    private class RecordingDictionaryNativeBridge : DictionaryNativeBridge {
        constructor()

        constructor(onLookup: (Long) -> Unit) {
            this.onLookup = onLookup
        }

        lateinit var termPaths: Array<String>
        lateinit var freqPaths: Array<String>
        lateinit var pitchPaths: Array<String>
        lateinit var kanjiPaths: Array<String>
        val destroyedSessions = mutableListOf<Long>()
        var failNextRebuild = false
        private var nextSession = 1L
        private val sessionTermPaths = mutableMapOf<Long, Array<String>>()
        private val sessionKanjiPaths = mutableMapOf<Long, Array<String>>()
        private var onLookup: (Long) -> Unit = {}
        val createdLanguageIds = mutableListOf<String>()

        override fun importDictionary(zipPath: String, outputDir: String, lowRam: Boolean): NativeDictionaryImportResult =
            NativeDictionaryImportResult(
                success = true,
                title = "",
                termCount = 1,
                metaCount = 0,
                freqCount = 0,
                pitchCount = 0,
                mediaCount = 0,
            )

        override fun createLookupObject(languageId: String): Long {
            createdLanguageIds += languageId
            return nextSession++
        }

        override fun destroyLookupObject(session: Long) {
            destroyedSessions += session
        }

        override fun rebuildQuery(
            session: Long,
            termPaths: Array<String>,
            freqPaths: Array<String>,
            pitchPaths: Array<String>,
            kanjiPaths: Array<String>,
        ) {
            if (failNextRebuild) {
                failNextRebuild = false
                error("Unable to rebuild query.")
            }
            this.termPaths = termPaths
            this.freqPaths = freqPaths
            this.pitchPaths = pitchPaths
            this.kanjiPaths = kanjiPaths
            sessionTermPaths[session] = termPaths
            sessionKanjiPaths[session] = kanjiPaths
        }

        override fun queryKanji(session: Long, kanji: String): KanjiResult =
            KanjiResult(
                character = kanji,
                entries = arrayOf(
                    KanjiEntry(
                        dictName = "KANJIDIC",
                        onyomi = "セイ",
                        kunyomi = "ほし",
                        tags = "",
                        definitions = arrayOf("session-$session:${sessionKanjiPaths.getValue(session).single()}"),
                        stats = emptyArray(),
                    ),
                ),
            )

        override fun lookup(session: Long, text: String, maxResults: Int, scanLength: Int): List<LookupResult> {
            onLookup(session)
            val termPath = sessionTermPaths.getValue(session).singleOrNull().orEmpty()
            return listOf(
                LookupResult(
                    matched = text,
                    term = TermResult(
                        expression = text,
                        reading = text,
                        rules = "",
                        glossaries = arrayOf(
                            GlossaryEntry(
                                dictName = "Test",
                                glossary = "session-$session:$termPath",
                                definitionTags = "",
                                termTags = "",
                            ),
                        ),
                        frequencies = emptyArray(),
                        pitches = emptyArray(),
                    ),
                    traceCandidates = emptyArray(),
                ),
            )
        }

        override fun getStyles(session: Long): List<DictionaryStyle> = emptyList()

        override fun getMediaFile(session: Long, dictionary: String, path: String): ByteArray? = null
    }
}
