package moe.antimony.hoshi.features.dictionary

import de.manhhao.hoshi.FrequencyEntry
import de.manhhao.hoshi.GlossaryEntry
import de.manhhao.hoshi.LookupResult
import de.manhhao.hoshi.PitchEntry
import de.manhhao.hoshi.TermResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionarySearchContentTest {
    @Test
    fun blankQueryClearsSearchContentLikeIos() {
        val state = DictionarySearchContent.runLookup(
            query = "   ",
            lookup = { error("lookup should not run for blank query") },
            assets = LookupPopupAssets(popupJs = "", popupCss = ""),
        )

        assertEquals("", state.lastQuery)
        assertEquals("", state.html)
        assertFalse(state.hasResults)
    }

    @Test
    fun nonBlankQueryRendersResultsThroughPopupPipeline() {
        val state = DictionarySearchContent.runLookup(
            query = " 猫 ",
            lookup = {
                listOf(
                    LookupResult(
                        matched = "猫",
                        deinflected = "猫",
                        process = emptyArray(),
                        term = TermResult(
                            expression = "猫",
                            reading = "ねこ",
                            rules = "",
                            glossaries = arrayOf(
                                GlossaryEntry(
                                    dictName = "JMdict",
                                    glossary = "cat",
                                    definitionTags = "",
                                    termTags = "",
                                ),
                            ),
                            frequencies = emptyArray<FrequencyEntry>(),
                            pitches = emptyArray<PitchEntry>(),
                        ),
                        preprocessorSteps = 0,
                    ),
                )
            },
            assets = LookupPopupAssets(
                popupJs = "window.renderPopup = function() {};",
                popupCss = ".entry-header {}",
            ),
        )

        assertEquals("猫", state.lastQuery)
        assertTrue(state.hasResults)
        assertTrue(state.html.contains("window.lookupEntries = ["))
        assertTrue(state.html.contains(""""expression":"猫""""))
        assertTrue(state.html.contains("""<div style="height: 118px;"></div>"""))
        assertTrue(state.html.contains("window.renderPopup();"))
    }
}
