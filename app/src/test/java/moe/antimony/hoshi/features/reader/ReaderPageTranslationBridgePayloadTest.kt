package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPageTranslationBridgePayloadTest {
    @Test
    fun parsesTranslationTargetFromJavascriptResult() {
        val javascriptResult = "\"{\\\"id\\\":\\\"hoshi-translation-3\\\",\\\"text\\\":\\\"第一段原文\\\"}\""

        assertEquals(
            ReaderPageTranslationHit(
                target = ReaderPageTranslationTarget(
                    id = "hoshi-translation-3",
                    text = "第一段原文",
                ),
                onTranslation = false,
            ),
            ReaderPageTranslationBridgePayload.hitFromJavascriptResult(javascriptResult),
        )
    }

    @Test
    fun marksHitAsTranslationBlockWhenJavascriptReportsIt() {
        val javascriptResult =
            "\"{\\\"id\\\":\\\"hoshi-translation-3\\\",\\\"text\\\":\\\"第一段原文\\\",\\\"onTranslation\\\":true}\""

        assertEquals(
            true,
            ReaderPageTranslationBridgePayload.hitFromJavascriptResult(javascriptResult)?.onTranslation,
        )
    }

    @Test
    fun ignoresHitWithoutText() {
        val javascriptResult = "\"{\\\"id\\\":\\\"hoshi-translation-3\\\",\\\"text\\\":\\\"  \\\"}\""

        assertNull(ReaderPageTranslationBridgePayload.hitFromJavascriptResult(javascriptResult))
    }
}
