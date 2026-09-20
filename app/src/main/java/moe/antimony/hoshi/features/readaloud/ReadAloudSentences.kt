package moe.antimony.hoshi.features.readaloud

internal object ReadAloudSentences {
    private val terminators = charArrayOf('。', '．', '！', '？', '!', '?')
    private val closers = charArrayOf('」', '』', '）', ')', '”', '"', '》', '〉', '】', '］')

    /**
     * Splits a Japanese paragraph into speakable sentences.
     * Sentence terminators stay attached to their sentence, and trailing closing
     * brackets follow the terminator so quotes are not left dangling.
     */
    fun split(paragraph: String): List<String> =
        sentenceRanges(paragraph).map { paragraph.substring(it).trim() }

    /**
     * Raw character ranges [start, end) of the speakable sentences within [paragraph].
     * Blank fragments and lone terminators are skipped, matching [split].
     */
    fun sentenceRanges(paragraph: String): List<IntRange> {
        if (paragraph.isBlank()) return emptyList()
        val ranges = ArrayList<IntRange>()
        var start = 0
        var index = 0
        while (index < paragraph.length) {
            val char = paragraph[index]
            if (char == '\n' || char == '\r') {
                appendRange(ranges, paragraph, start, index)
                start = index + 1
            } else if (terminators.contains(char)) {
                var end = index + 1
                while (end < paragraph.length && closers.contains(paragraph[end])) end++
                appendRange(ranges, paragraph, start, end)
                index = end - 1
                start = end
            }
            index++
        }
        appendRange(ranges, paragraph, start, paragraph.length)
        return ranges
    }

    /**
     * Finds the index of the sentence at [offset] (character offset within the paragraph's
     * normalized text), falling back to the first sentence of the paragraph. Returns 0 when
     * the paragraph has no sentences.
     */
    fun indexOfSentenceAtOffset(paragraph: String, offset: Int): Int {
        val ranges = sentenceRanges(paragraph)
        if (ranges.isEmpty()) return 0
        if (offset <= 0) return 0
        var acc = 0
        ranges.forEachIndexed { i, range ->
            acc += range.last - range.first + 1
            if (offset < acc) return i
        }
        return ranges.lastIndex
    }

    /**
     * Resolves which sentence (0-based) of [paragraph] the long-pressed [sentenceText] belongs to.
     * Prefers an exact / whitespace-normalized text match, then containment, then falls back to
     * [offset] (see [indexOfSentenceAtOffset]), and finally the first sentence.
     */
    fun indexOfSentence(
        paragraph: String,
        sentenceText: String?,
        offset: Int?,
    ): Int {
        val ranges = sentenceRanges(paragraph)
        if (ranges.isEmpty()) return 0
        if (!sentenceText.isNullOrBlank()) {
            val target = sentenceText.trim()
            val textOf: (IntRange) -> String = { paragraph.substring(it).trim() }
            ranges.indexOfFirst { textOf(it) == target }.let { if (it >= 0) return it }
            val norm = target.replace(Regex("\\s+"), " ")
            ranges.indexOfFirst { textOf(it).replace(Regex("\\s+"), " ") == norm }.let { if (it >= 0) return it }
            ranges.indexOfFirst { s ->
                val t = textOf(s)
                t.contains(target) || target.contains(t)
            }.let { if (it >= 0) return it }
        }
        if (offset != null) return indexOfSentenceAtOffset(paragraph, offset)
        return 0
    }

    private fun appendRange(ranges: ArrayList<IntRange>, paragraph: String, start: Int, end: Int) {
        if (end <= start) return
        val sentence = paragraph.substring(start, end).trim()
        if (sentence.isBlank()) return
        if (sentence.length == 1 && terminators.contains(sentence[0])) return
        ranges.add(start until end)
    }
}
