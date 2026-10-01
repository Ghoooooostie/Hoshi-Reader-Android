package moe.antimony.hoshi.features.translation

import java.io.IOException
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 腾讯 Transmart 的伪造 client_key，两种实现共用。 */
private fun buildTransmartClientKey(): String =
    "browser-firefox-110.0.0-Windows 10-${UUID.randomUUID()}-${System.currentTimeMillis()}"

/**
 * 腾讯交互翻译（对齐 LunaTranslator qqTranSmart.py）：
 * 先 text_analysis 分句，再 auto_translation 翻译后按序拼回。
 */
internal object QqTransmartWebTranslator : FreeWebTranslator {
    private const val API = "https://transmart.qq.com/api/imt"
    private const val SOURCE_LANGUAGE = "auto"
    private const val TARGET_LANGUAGE = "zh"

    override fun translate(text: String): String {
        val clientKey = buildTransmartClientKey()
        val textList = runCatching { splitSentences(text, clientKey) }.getOrDefault(listOf(text))
        val response = WebTranslationHttp.request(
            method = "POST",
            url = API,
            headers = mapOf("Cookie" to "client_key=$clientKey"),
            jsonBody = buildJsonObject {
                put(
                    "header",
                    buildJsonObject {
                        put("fn", "auto_translation")
                        put("client_key", clientKey)
                    },
                )
                put("type", "plain")
                put("model_category", "normal")
                put(
                    "source",
                    buildJsonObject {
                        put("lang", SOURCE_LANGUAGE)
                        put(
                            "text_list",
                            buildJsonArray {
                                add(JsonPrimitive(""))
                                textList.forEach { add(JsonPrimitive(it)) }
                                add(JsonPrimitive(""))
                            },
                        )
                    },
                )
                put("target", buildJsonObject { put("lang", TARGET_LANGUAGE) })
            }.toString(),
        )
        val parsed = Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
        val translations = parsed.jsonObject["auto_translation"]?.jsonArray
            ?: throw IOException("Transmart response missing auto_translation")
        return translations.joinToString("") { it.jsonPrimitive.content }
    }

    private fun splitSentences(text: String, clientKey: String): List<String> {
        val splitResponse = WebTranslationHttp.request(
            method = "POST",
            url = API,
            jsonBody = buildJsonObject {
                put(
                    "header",
                    buildJsonObject {
                        put("fn", "text_analysis")
                        put("client_key", clientKey)
                    },
                )
                put("type", "plain")
                put("text", text)
                put("normalize", buildJsonObject { put("merge_broken_line", "false") })
            }.toString(),
        )
        val sentenceList = Json.parseToJsonElement(WebTranslationHttp.requireSuccess(splitResponse))
            .jsonObject["sentence_list"]?.jsonArray
            ?: return listOf(text)
        if (sentenceList.isEmpty()) return listOf(text)
        return sentenceList.map { item ->
            val entry = item.jsonObject
            val start = entry["start"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: return@map null
            val length = entry["len"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: return@map null
            runCatching { text.substring(start, start + length) }.getOrNull()
        }.filterNotNull().ifEmpty { listOf(text) }
    }
}

/**
 * 腾讯 Transmart IMT 接口（对齐 LunaTranslator qqimt.py）：
 * 直接单条 auto_translation，伪造 client_key，无需真实凭据。
 */
internal object QqImtWebTranslator : FreeWebTranslator {
    private const val API = "https://transmart.qq.com/api/imt"
    private const val SOURCE_LANGUAGE = "auto"
    private const val TARGET_LANGUAGE = "zh"

    override fun translate(text: String): String {
        val clientKey = "browser-chrome-124.0.0-Windows_10-" +
            "${UUID.randomUUID()}-${System.currentTimeMillis() / 1000}"
        val response = WebTranslationHttp.request(
            method = "POST",
            url = API,
            headers = mapOf(
                "Origin" to "https://transmart.qq.com",
                "Referer" to "https://transmart.qq.com/",
                "X-Requested-With" to "XMLHttpRequest",
            ),
            jsonBody = buildJsonObject {
                put(
                    "header",
                    buildJsonObject {
                        put("fn", "auto_translation")
                        put("session", "")
                        put("client_key", clientKey)
                        put("user", "")
                    },
                )
                put("type", "plain")
                put("model_category", "normal")
                put("text_domain", "general")
                put(
                    "source",
                    buildJsonObject {
                        put("lang", SOURCE_LANGUAGE)
                        put(
                            "text_list",
                            buildJsonArray {
                                add(JsonPrimitive(""))
                                add(JsonPrimitive(text))
                                add(JsonPrimitive(""))
                            },
                        )
                    },
                )
                put("target", buildJsonObject { put("lang", TARGET_LANGUAGE) })
            }.toString(),
        )
        val parsed = Json.parseToJsonElement(WebTranslationHttp.requireSuccess(response))
        val translations = parsed.jsonObject["auto_translation"]?.jsonArray
            ?: throw IOException("QqImt response missing auto_translation")
        return translations.joinToString("") { it.jsonPrimitive.content }
    }
}
