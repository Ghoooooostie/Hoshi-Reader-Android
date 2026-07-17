package moe.antimony.hoshi.features.reader

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal object ReaderPageTranslationCommand {
    fun collectVisibleTargets(): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.collectVisibleTargets()"

    fun targetAtPoint(
        x: Float,
        y: Float,
    ): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.targetAtPoint($x, $y)"

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

    fun targetFromJavascriptResult(result: String?): ReaderPageTranslationTarget? {
        val payload = runCatching { json.decodeFromString<String>(result.orEmpty()) }.getOrNull()
            ?: return null
        return runCatching {
            json.decodeFromString(Payload.serializer(), payload)
        }.getOrNull()?.toTarget()
    }

    fun targetsFromJavascriptResult(result: String?): List<ReaderPageTranslationTarget> {
        val payload = runCatching { json.decodeFromString<String>(result.orEmpty()) }.getOrNull()
            ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Payload.serializer()), payload)
        }.getOrDefault(emptyList()).mapNotNull(Payload::toTarget)
    }

    @Serializable
    private data class Payload(
        val id: String,
        val text: String,
    ) {
        fun toTarget(): ReaderPageTranslationTarget? {
            val trimmedId = id.trim()
            val trimmedText = text.trim()
            if (trimmedId.isBlank() || trimmedText.isBlank()) {
                return null
            }
            return ReaderPageTranslationTarget(id = trimmedId, text = trimmedText)
        }
    }
}
