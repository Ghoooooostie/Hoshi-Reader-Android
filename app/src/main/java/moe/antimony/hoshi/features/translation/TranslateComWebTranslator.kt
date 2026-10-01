package moe.antimony.hoshi.features.translation

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Translate.com 机器翻译接口（对齐 LunaTranslator TranslateCom.py）：
 * 表单提交、无任何鉴权，先自动检测源语言。
 */
internal object TranslateComWebTranslator : FreeWebTranslator {
    private const val TARGET_LANGUAGE = "zh-CN"

    override fun translate(text: String): String {
        val detected = Json.parseToJsonElement(
            WebTranslationHttp.requireSuccess(
                WebTranslationHttp.request(
                    method = "POST",
                    url = "https://www.translate.com/translator/ajax_lang_auto_detect",
                    form = mapOf("text_to_translate" to text),
                ),
            ),
        ).jsonObject["language"]?.jsonPrimitive?.content
            ?: throw IOException("TranslateCom detect response missing language")
        val response = WebTranslationHttp.request(
            method = "POST",
            url = "https://www.translate.com/translator/translate_mt",
            form = mapOf(
                "text_to_translate" to text,
                "source_lang" to detected,
                "translated_lang" to TARGET_LANGUAGE,
                "use_cache_only" to "false",
            ),
        )
        val translated = Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
            .jsonObject["translated_text"]?.jsonPrimitive?.content
            ?: throw IOException("TranslateCom response missing translated_text")
        return unescapeHtmlEntities(translated)
    }
}
