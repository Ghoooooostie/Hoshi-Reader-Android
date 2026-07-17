package de.manhhao.hoshi

class ImportResult(
    val success: Boolean,
    val title: String,
    val termCount: Long,
    val metaCount: Long,
    val freqCount: Long,
    val pitchCount: Long,
    val mediaCount: Long,
)

class DictionaryStyle(
    val dictName: String,
    val styles: String,
)

class Frequency(
    val value: Int,
    val displayValue: String,
)

class GlossaryEntry(
    val dictName: String,
    val glossary: String,
    val definitionTags: String,
    val termTags: String,
)

class FrequencyEntry(
    val dictName: String,
    val frequencies: Array<Frequency>,
)

class PitchEntry(
    val dictName: String,
    val pitchPositions: IntArray,
    val transcriptions: Array<String>,
) {
    // 兼容旧版 JNI，只返回音高位置时补空转写。
    constructor(
        dictName: String,
        pitchPositions: IntArray,
    ) : this(
        dictName = dictName,
        pitchPositions = pitchPositions,
        transcriptions = emptyArray(),
    )
}

class TermResult(
    val expression: String,
    val reading: String,
    val rules: String,
    val glossaries: Array<GlossaryEntry>,
    val frequencies: Array<FrequencyEntry>,
    val pitches: Array<PitchEntry>,
)

class TransformGroup(
    val name: String,
    val description: String,
)

enum class TraceSource {
    ALGORITHM,
    DICTIONARY,
    BOTH,
}

class TraceCandidate(
    val deinflected: String,
    val preprocessorSteps: Int,
    val source: TraceSource,
    val trace: Array<TransformGroup>,
)

class LookupResult(
    val matched: String,
    val term: TermResult,
    val traceCandidates: Array<TraceCandidate>,
) {
    // 兼容旧版 JNI，把旧的去活用结果折叠成算法来源的单条 trace candidate。
    constructor(
        matched: String,
        deinflected: String,
        process: Array<TransformGroup>,
        term: TermResult,
        preprocessorSteps: Int,
    ) : this(
        matched = matched,
        term = term,
        traceCandidates = arrayOf(
            TraceCandidate(
                deinflected = deinflected,
                preprocessorSteps = preprocessorSteps,
                source = TraceSource.ALGORITHM,
                trace = process,
            ),
        ),
    )
}

object HoshiDicts {
    init {
        System.loadLibrary("hoshidicts_jni")
    }

    external fun importDictionary(zipPath: String, outputDir: String, lowRam: Boolean = false): ImportResult
    external fun createLookupObject(languageId: String): Long
    external fun destroyLookupObject(session: Long)
    external fun rebuildQuery(
        session: Long,
        termPaths: Array<String>,
        freqPaths: Array<String>,
        pitchPaths: Array<String>,
    )

    external fun lookup(session: Long, text: String, maxResults: Int, scanLength: Int): Array<LookupResult>
    external fun getStyles(session: Long): Array<DictionaryStyle>
    external fun getMediaFile(session: Long, dictName: String, mediaPath: String): ByteArray?
}
