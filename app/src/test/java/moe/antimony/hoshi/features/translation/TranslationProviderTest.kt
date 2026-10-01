package moe.antimony.hoshi.features.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationProviderTest {
    @Test
    fun fromIdMapsKnownIds() {
        assertEquals(TranslationProvider.Ai, TranslationProvider.fromId("ai"))
        assertEquals(TranslationProvider.Microsoft, TranslationProvider.fromId("microsoft"))
        assertEquals(TranslationProvider.Caiyun, TranslationProvider.fromId("caiyun"))
        assertEquals(TranslationProvider.QqTransmart, TranslationProvider.fromId("qqTransSmart"))
        assertEquals(TranslationProvider.Papago, TranslationProvider.fromId("papago"))
    }

    @Test
    fun fromIdFallsBackToAiForUnknownOrNull() {
        assertEquals(TranslationProvider.Ai, TranslationProvider.fromId(null))
        assertEquals(TranslationProvider.Ai, TranslationProvider.fromId(""))
        assertEquals(TranslationProvider.Ai, TranslationProvider.fromId("nope"))
    }

    @Test
    fun freeWebProvidersAreFlagged() {
        assertEquals(false, TranslationProvider.Ai.isFreeWeb)
        TranslationProvider.entries
            .filterNot { it == TranslationProvider.Ai }
            .forEach { provider -> assertEquals(true, provider.isFreeWeb) }
    }

    @Test
    fun unescapeHtmlEntitiesDecodesNamedAndNumericEntities() {
        assertEquals("<b>", unescapeHtmlEntities("&lt;b&gt;"))
        assertEquals("&lt;", unescapeHtmlEntities("&amp;lt;"))
        assertEquals("A", unescapeHtmlEntities("&#65;"))
        assertEquals("B", unescapeHtmlEntities("&#x42;"))
        assertEquals("'", unescapeHtmlEntities("&apos;"))
        assertEquals("plain text", unescapeHtmlEntities("plain text"))
    }

    @Test
    fun md5HexMatchesKnownVector() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5Hex("abc"))
    }

    @Test
    fun caiyunDecryptRestoresRot13Base64() {
        // base64("test") = "dGVzdA=="，经彩云 ROT13 字母表映射后为 "qTImqN=="。
        assertEquals("test", decryptCaiyun("qTImqN=="))
    }
}
