package moe.antimony.hoshi.features.dictionary

import de.manhhao.hoshi.FrequencyEntry
import de.manhhao.hoshi.Frequency
import de.manhhao.hoshi.GlossaryEntry
import de.manhhao.hoshi.LookupResult
import de.manhhao.hoshi.PitchEntry
import de.manhhao.hoshi.TermResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupPopupHtmlTest {
    @Test
    fun rendersThroughIosPopupJavascriptPipeline() {
        val html = LookupPopupHtml.render(
            listOf(
                lookupResult(
                    expression = "冷や",
                    reading = "ひや",
                    glossary = """
                        [{"content":[{"content":{"content":"cold water","tag":"li"},"tag":"ul"}],"type":"structured-content"}]
                    """.trimIndent(),
                ),
            ),
            assets = LookupPopupAssets(
                popupJs = "window.renderPopup = function() {};",
                popupCss = ".entry-header {}",
                selectionJs = "window.hoshiSelection = { selectText: function() {} };",
            ),
        )

        assertTrue(html.contains("<style>.entry-header {}</style>"))
        assertTrue(html.contains("<script>window.hoshiSelection = { selectText: function() {} };</script>"))
        assertTrue(html.contains("<script>window.renderPopup = function() {};</script>"))
        assertTrue(html.contains("""<div id="entries-container"></div>"""))
        assertTrue(html.contains("window.renderPopup();"))
        assertFalse(html.contains("""<section class="entry">"""))
    }

    @Test
    fun serializesLookupEntriesUsingIosPopupShape() {
        val html = LookupPopupHtml.render(
            listOf(
                lookupResult(
                    expression = "冷や",
                    reading = "ひや",
                    glossary = "<b>cold water</b>",
                ),
            ),
            assets = LookupPopupAssets(
                popupJs = "",
                popupCss = "",
            ),
        )

        assertTrue(html.contains("window.lookupEntries = ["))
        assertTrue(html.contains(""""expression":"冷や""""))
        assertTrue(html.contains(""""reading":"ひや""""))
        assertTrue(html.contains(""""matched":"冷や""""))
        assertTrue(html.contains(""""deinflectionTrace":[]"""))
        assertTrue(html.contains(""""glossaries":[{""""))
        assertTrue(html.contains(""""dictionary":"JMdict""""))
        assertTrue(html.contains(""""content":"<b>cold water</b>""""))
        assertTrue(html.contains(""""definitionTags":""""))
        assertTrue(html.contains(""""termTags":""""))
        assertTrue(html.contains(""""frequencies":[]"""))
        assertTrue(html.contains(""""pitches":[]"""))
        assertTrue(html.contains(""""rules":[]"""))
    }

    @Test
    fun serializesFrequencyAndPitchMetadataUsingIosPopupShape() {
        val html = LookupPopupHtml.render(
            listOf(
                lookupResult(
                    expression = "食べる",
                    reading = "たべる",
                    glossary = "eat",
                    frequencies = arrayOf(
                        FrequencyEntry(
                            dictName = "Jiten",
                            frequencies = arrayOf(Frequency(value = 1139, displayValue = "1,139")),
                        ),
                    ),
                    pitches = arrayOf(
                        PitchEntry(
                            dictName = "アクセント辞典",
                            pitchPositions = intArrayOf(2),
                        ),
                    ),
                ),
            ),
            assets = LookupPopupAssets(
                popupJs = "",
                popupCss = "",
            ),
        )

        assertTrue(html.contains(""""frequencies":[{""""))
        assertTrue(html.contains(""""dictionary":"Jiten""""))
        assertTrue(html.contains(""""value":1139"""))
        assertTrue(html.contains(""""displayValue":"1,139""""))
        assertTrue(html.contains(""""pitches":[{""""))
        assertTrue(html.contains(""""dictionary":"アクセント辞典""""))
        assertTrue(html.contains(""""pitchPositions":[2]"""))
    }

    private fun lookupResult(
        expression: String,
        reading: String,
        glossary: String,
        frequencies: Array<FrequencyEntry> = emptyArray(),
        pitches: Array<PitchEntry> = emptyArray(),
    ): LookupResult = LookupResult(
        expression,
        expression,
        emptyArray(),
        TermResult(
            expression = expression,
            reading = reading,
            rules = "",
            glossaries = arrayOf(
                GlossaryEntry(
                    dictName = "JMdict",
                    glossary = glossary,
                    definitionTags = "",
                    termTags = "",
                ),
            ),
            frequencies = frequencies,
            pitches = pitches,
        ),
        0,
    )
}
