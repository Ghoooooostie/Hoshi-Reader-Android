package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageTranslationBridgePayloadTest {
    @Test
    fun parsesTranslationTargetFromJavascriptResult() {
        val javascriptResult = "\"{\\\"id\\\":\\\"hoshi-translation-3\\\",\\\"text\\\":\\\"第一段原文\\\"}\""

        assertEquals(
            ReaderPageTranslationTarget(
                id = "hoshi-translation-3",
                text = "第一段原文",
            ),
            ReaderPageTranslationBridgePayload.targetFromJavascriptResult(javascriptResult),
        )
    }
}
