package moe.antimony.hoshi.features.reader

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal object ReaderPageTranslationCommand {
    fun collectVisibleTargets(): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.collectVisibleTargets()"

    fun applyTranslation(
        targetId: String,
        translation: String,
    ): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.applyTranslation(" +
            "${readerJavaScriptStringLiteral(targetId)}, ${readerJavaScriptStringLiteral(translation)})"

    fun clearTranslations(): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.clearTranslations()"
}

internal object ReaderPageTranslationBridgePayload {
    private val json = Json { ignoreUnknownKeys = true }

    fun targetsFromJavascriptResult(result: String?): List<ReaderPageTranslationTarget> {
        val payload = runCatching { json.decodeFromString<String>(result.orEmpty()) }.getOrNull()
            ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Payload.serializer()), payload)
        }.getOrDefault(emptyList()).mapNotNull { item ->
            val id = item.id.trim()
            val text = item.text.trim()
            if (id.isBlank() || text.isBlank()) {
                null
            } else {
                ReaderPageTranslationTarget(id = id, text = text)
            }
        }
    }

    @Serializable
    private data class Payload(
        val id: String,
        val text: String,
    )
}
