package moe.antimony.hoshi.features.translation

import java.security.MessageDigest

/** 免 key web 翻译器的阻塞式接口；调用方负责切到 IO 线程。 */
internal interface FreeWebTranslator {
    fun translate(text: String): String

    /** 凭据类实现请求失败后调用：丢弃缓存凭据，下次请求时重新获取。 */
    fun reset() {}
}

/** 按翻译源分发到具体实现；任一次失败会重置凭据并重试一次（对应 LunaTranslator 的 needreinit 行为）。 */
internal object FreeWebTranslationCatalog {
    fun translate(provider: TranslationProvider, text: String): String {
        val translator = translatorFor(provider)
        return try {
            translator.translate(text)
        } catch (first: Exception) {
            translator.reset()
            translator.translate(text)
        }
    }

    fun translatorFor(provider: TranslationProvider): FreeWebTranslator = when (provider) {
        TranslationProvider.Microsoft -> MicrosoftWebTranslator
        TranslationProvider.Bing -> BingWebTranslator
        TranslationProvider.Google -> GoogleWebTranslator
        TranslationProvider.ModernMt -> ModernMtWebTranslator
        TranslationProvider.QqTransmart -> QqTransmartWebTranslator
        TranslationProvider.QqImt -> QqImtWebTranslator
        TranslationProvider.Ali -> AliWebTranslator
        TranslationProvider.Youdao -> YoudaoWebTranslator
        TranslationProvider.Caiyun -> CaiyunWebTranslator
        TranslationProvider.TranslateCom -> TranslateComWebTranslator
        TranslationProvider.Yandex -> YandexWebTranslator
        TranslationProvider.Huoshan -> HuoshanWebTranslator
        TranslationProvider.Papago -> PapagoWebTranslator
        TranslationProvider.Ai -> error("AI provider must be handled by AdvancedAiClient")
    }
}

/** 还原 web 翻译接口返回里残留的 HTML 实体。 */
internal fun unescapeHtmlEntities(text: String): String {
    if (!text.contains('&')) return text
    val regex = Regex("&(amp|lt|gt|quot|apos|nbsp|#([0-9]+|#x[0-9A-Fa-f]+));")
    return regex.replace(text) { match ->
        val name = match.groupValues[1]
        val numeric = match.groupValues[2]
        when {
            numeric.isNotEmpty() -> {
                val codePoint = if (numeric.startsWith("#x") || numeric.startsWith("#X")) {
                    numeric.substring(2).toInt(16)
                } else {
                    numeric.toInt()
                }
                String(Character.toChars(codePoint))
            }
            name == "amp" -> "&"
            name == "lt" -> "<"
            name == "gt" -> ">"
            name == "quot" -> "\""
            name == "apos" -> "'"
            name == "nbsp" -> "\u00A0"
            else -> match.value
        }
    }
}

internal fun md5Hex(text: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
