package moe.antimony.hoshi.features.translation

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Yandex 浏览器视频翻译接口（对齐 LunaTranslator yandex.py）：
 * 端点无鉴权，先 detect 源语言，再翻译为简体中文。
 */
internal object YandexWebTranslator : FreeWebTranslator {
    private const val SERVICE = "browser_video_translation"
    private const val TARGET_LANGUAGE = "zh"

    override fun translate(text: String): String {
        val detected = Json.parseToJsonElement(
            WebTranslationHttp.requireSuccess(
                WebTranslationHttp.request(
                    method = "GET",
                    url = "https://translate.yandex.net/api/v1/tr.json/detect",
                    query = mapOf("srv" to SERVICE, "text" to text),
                ),
            ),
        ).jsonObject["lang"]?.jsonPrimitive?.content
            ?: throw IOException("Yandex detect response missing lang")
        val response = WebTranslationHttp.request(
            method = "POST",
            url = "https://browser.translate.yandex.net/api/v1/tr.json/translate",
            query = mapOf(
                "lang" to "${detected}-${TARGET_LANGUAGE}",
                "text" to text,
                "srv" to SERVICE,
            ),
            form = linkedMapOf("maxRetryCount" to "2", "fetchAbortTimeout" to "500"),
        )
        val parsed = Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
        return parsed.jsonObject["text"]!!.jsonArray[0].jsonPrimitive.content
    }
}
