package moe.antimony.hoshi.features.readaloud

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Read Aloud queue building depends on the paragraph text keeping its sentence
 * terminators: `reader-translation.js` feeds the queue, and [ReadAloudSentences]
 * splits the text on 。！？. A normalized (punctuation-free) paragraph collapses
 * into a single queue item, which makes 上一句/下一句 do nothing.
 */
class ReadAloudSentencesTest {
    @Test
    fun splitsParagraphWithSentenceTerminators() {
        assertEquals(
            listOf(
                "言語も違うし、両親の顔立ちも日本人ではない。",
                "服装もなんだか民族衣装っぽい。",
            ),
            ReadAloudSentences.split("言語も違うし、両親の顔立ちも日本人ではない。服装もなんだか民族衣装っぽい。"),
        )
    }

    @Test
    fun keepsClosingBracketsWithTheirSentence() {
        assertEquals(
            listOf(
                "「これは何だ。」",
                "彼は首をかしげた！",
            ),
            ReadAloudSentences.split("「これは何だ。」彼は首をかしげた！"),
        )
    }

    @Test
    fun punctuationFreeParagraphCollapsesIntoOneItem() {
        assertEquals(
            listOf("言語も違うし両親の顔立ちも日本人ではない服装もなんだか民族衣装っぽい"),
            ReadAloudSentences.split("言語も違うし両親の顔立ちも日本人ではない服装もなんだか民族衣装っぽい"),
        )
    }

    @Test
    fun resolvesSentenceIndexFromText() {
        val paragraph = "第一句。第二句。第三句。"

        assertEquals(1, ReadAloudSentences.indexOfSentence(paragraph, "第二句。", null))
        assertEquals(2, ReadAloudSentences.indexOfSentence(paragraph, null, 8))
        assertEquals(0, ReadAloudSentences.indexOfSentence(paragraph, null, 0))
    }
}
