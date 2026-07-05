package moe.antimony.hoshi.features.reader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderWebAssetsTest {
    @Test
    fun readerWebAssetsExistInNeutralAssetTree() {
        val assets = listOf(
            "hoshi-web/shared/language-ja.js",
            "hoshi-web/shared/selection-ja.js",
            "hoshi-web/shared/selection-en.js",
            "hoshi-web/shared/selection.js",
            "hoshi-web/reader/reader-paginated.js",
            "hoshi-web/reader/reader-continuous.js",
            "hoshi-web/reader/reader-visual-novel.js",
            "hoshi-web/reader/reader-text-semantics.js",
            "hoshi-web/reader/reader-dom-text.js",
            "hoshi-web/reader/reader-media-semantics.js",
            "hoshi-web/reader/reader-vn-content-stream.js",
            "hoshi-web/reader/reader-vn-range-map.js",
            "hoshi-web/reader/highlights.js",
            "hoshi-web/reader/reader.css",
            "hoshi-web/popup/popup.js",
            "hoshi-web/popup/popup.css",
            "hoshi-web/popup/iframe.html",
            "hoshi-web/popup/reader-popup-host.js",
            "hoshi-web/popup/icons/close.svg",
        )

        assets.forEach { path ->
            val file = listOf(
                File("app/src/main/assets/$path"),
                File("src/main/assets/$path"),
            ).firstOrNull(File::isFile)
                ?: File("app/src/main/assets/$path")
            assertTrue("$path should exist", file.isFile)
            assertTrue("$path should not be empty", file.length() > 0)
        }
    }

    @Test
    fun generatedReaderCssDoesNotExposeTemplatePlaceholders() {
        val css = ReaderContentStyles.css()

        assertFalse(css.contains("__HOSHI_"))
    }

    @Test
    fun popupAdvancedAiCardDoesNotPaintAnExtraBackgroundLayer() {
        val declarations = cssDeclarationsForSelector(
            assetText("hoshi-web/popup/popup.css"),
            ".advanced-ai-card",
        )

        assertEquals("transparent", declarations["background"])
        assertEquals("0", declarations["border"])
    }

    private fun assetText(path: String): String =
        listOf(
            File("app/src/main/assets/$path"),
            File("src/main/assets/$path"),
        ).firstOrNull(File::isFile)
            ?.readText(Charsets.UTF_8)
            ?: File("app/src/main/assets/$path").readText(Charsets.UTF_8)

    private fun cssDeclarationsForSelector(css: String, selector: String): Map<String, String> {
        val pattern = Regex("${Regex.escape(selector)}\\s*\\{([^}]*)\\}")
        val body = pattern.find(css)?.groupValues?.getOrNull(1).orEmpty()
        return body.lines()
            .map(String::trim)
            .filter { it.isNotEmpty() && it.contains(':') }
            .associate { line ->
                val declaration = line.removeSuffix(";")
                val separator = declaration.indexOf(':')
                declaration.substring(0, separator).trim() to declaration.substring(separator + 1).trim()
            }
    }
}
