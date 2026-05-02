package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderResourceSanitizerTest {
    @Test
    fun cssSanitizerNormalizesKnownEpubPrivateDeclarationsBeforeWebViewRendering() {
        val css = """
            .h-valign-width {
              display: inline-block;
              -epub-writing-mode: horizontal-tb;
              -epub-line-break: strict;
              -epub-word-break: keep-all;
              vertical-align: middle;
            }
            .vrtl {
              -webkit-writing-mode: vertical-rl;
              -epub-writing-mode: vertical-rl;
              -epub-hyphens: auto;
              -epub-text-underline-position: under left;
              -epub-text-emphasis-style: filled sesame;
              -epub-text-emphasis-color: #000000;
              -epub-unknown-property: ignored;
            }
        """.trimIndent()

        val sanitized = sanitizeReaderCss(css)

        assertFalse(sanitized, sanitized.contains("-epub-"))
        assertTrue(sanitized.contains("display: inline-block;"))
        assertTrue(sanitized.contains("-webkit-writing-mode: vertical-rl;"))
        assertFalse(sanitized.contains("writing-mode: horizontal-tb;"))
        assertTrue(sanitized.contains("line-break: strict;"))
        assertTrue(sanitized.contains("word-break: keep-all;"))
        assertTrue(sanitized.contains("-webkit-hyphens: auto;"))
        assertTrue(sanitized.contains("hyphens: auto;"))
        assertTrue(sanitized.contains("text-underline-position: under left;"))
        assertTrue(sanitized.contains("-webkit-text-emphasis-style: filled sesame;"))
        assertTrue(sanitized.contains("text-emphasis-style: filled sesame;"))
        assertTrue(sanitized.contains("-webkit-text-emphasis-color: #000000;"))
        assertTrue(sanitized.contains("text-emphasis-color: #000000;"))
        assertTrue(sanitized.contains("vertical-align: middle;"))
    }

    @Test
    fun nonCssResourcesAreReturnedUnchanged() {
        val bytes = "<html><body>本文</body></html>".toByteArray()

        assertEquals(bytes.toList(), sanitizeReaderResource("application/xhtml+xml", bytes).toList())
    }
}
