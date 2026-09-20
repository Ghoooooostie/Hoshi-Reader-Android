package moe.antimony.hoshi.features.readaloud

internal object ReadAloudSentences {
    private val terminators = charArrayOf('。', '．', '！', '？', '!', '?')
    private val closers = charArrayOf('」', '』', '）', ')', '”', '"', '》', '〉', '】', '］')

    /**
     * Splits a Japanese paragraph into speakable sentences.
     * Sentence terminators stay attached to their sentence, and trailing closing
     * brackets follow the terminator so quotes are not left dangling.
     */
    fun split(paragraph: String): List<String> {
        if (paragraph.isBlank()) return emptyList()
        val sentences = ArrayList<String>()
        var start = 0
        var index = 0
        while (index < paragraph.length) {
            val char = paragraph[index]
            if (char == '\n' || char == '\r') {
                appendSentence(sentences, paragraph, start, index)
                start = index + 1
            } else if (terminators.contains(char)) {
                var end = index + 1
                while (end < paragraph.length && closers.contains(paragraph[end])) end++
                appendSentence(sentences, paragraph, start, end)
                index = end - 1
                start = end
            }
            index++
        }
        appendSentence(sentences, paragraph, start, paragraph.length)
        return sentences
    }

    private fun appendSentence(
        sentences: ArrayList<String>,
        text: String,
        start: Int,
        end: Int,
    ) {
        if (end <= start) return
        val sentence = text.substring(start, end).trim()
        if (sentence.isBlank()) return
        if (sentence.length == 1 && terminators.contains(sentence[0])) return
        sentences.add(sentence)
    }
}
