package moe.antimony.hoshi.features.dictionary

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class DictionarySearchFieldColorsTest {
    @Test
    fun cursorUsesReadableSearchFieldForegroundColor() {
        val darkModeForeground = Color(0xFFECE6F0)

        assertEquals(darkModeForeground, dictionarySearchCursorColor(darkModeForeground))
    }
}
