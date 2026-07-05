package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTranslationAiSheetTest {
    @Test
    fun longPressModeSegmentsUseFullWidthForDescriptiveLabels() {
        assertTrue(
            translationAiSegmentedControlUsesFullWidth(
                listOf("整句翻译", "长难句分析"),
            ),
        )
        assertTrue(
            translationAiSegmentedControlUsesFullWidth(
                listOf("Sentence Translation", "Sentence Analysis"),
            ),
        )
    }
}
