package moe.antimony.hoshi.features.reader

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal data class ReaderPageTranslationHit(
    val target: ReaderPageTranslationTarget,
    val onTranslation: Boolean,
)

internal object ReaderPageTranslationCommand {
    fun collectVisibleTargets(): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.collectVisibleTargets()"

    /** 按文档顺序取 targetId 之后的段落，供朗读按内容推进（不依赖"当前可见集合"）。 */
    fun collectTargetsAfter(targetId: String?, limit: Int): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.collectTargetsAfter(" +
            "${if (targetId == null) "null" else readerJavaScriptStringLiteral(targetId)}, $limit)"

    fun targetAtPoint(
        x: Float,
        y: Float,
        includeOriginal: Boolean,
    ): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.targetAtPoint(" +
            "$x, $y, $includeOriginal)"

    fun applyTranslation(
        targetId: String,
        translation: String,
    ): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.applyTranslation(" +
            "${readerJavaScriptStringLiteral(targetId)}, ${readerJavaScriptStringLiteral(translation)})"

    fun revealTranslation(targetId: String): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.revealTranslation(" +
            "${readerJavaScriptStringLiteral(targetId)})"

    fun setDisplayMode(mode: ReaderAiFullPageTranslationDisplayMode): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.setDisplayMode(" +
            "${readerJavaScriptStringLiteral(mode.jsValue)})"

    fun clearTranslations(): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.clearTranslations()"

    /**
     * Highlights the reader paragraph currently being spoken and optionally scrolls to it.
     * When [highlightVisible] is false only the scroll/reveal side effects run, so playback can
     * follow along without drawing the highlight.
     */
    fun highlightReadAloudTarget(
        targetId: String,
        reveal: Boolean,
        highlightVisible: Boolean = true,
    ): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.highlightReadAloudTarget(" +
            "${readerJavaScriptStringLiteral(targetId)}, $reveal, $highlightVisible)"

    /**
     * Highlights the specific sentence currently being spoken within its paragraph and scrolls to
     * it (音量键上一句/下一句跳转后让显示的文字也跟随变化). Falls back to the whole paragraph when the
     * sentence text cannot be located in the DOM.
     *
     * When [highlightVisible] is false only the scroll/reveal side effects run.
     */
    fun highlightReadAloudSentence(
        targetId: String,
        sentenceText: String?,
        reveal: Boolean,
        highlightVisible: Boolean = true,
    ): String {
        val escapedSentence = sentenceText?.let { readerJavaScriptStringLiteral(it) } ?: "null"
        return "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.highlightReadAloudSentence(" +
            "${readerJavaScriptStringLiteral(targetId)}, $escapedSentence, $reveal, $highlightVisible)"
    }

    fun clearReadAloudHighlight(): String =
        "window.hoshiReaderPageTranslation && window.hoshiReaderPageTranslation.clearReadAloudHighlight()"
}

internal object ReaderPageTranslationBridgePayload {
    private val json = Json { ignoreUnknownKeys = true }

    fun hitFromJavascriptResult(result: String?): ReaderPageTranslationHit? {
        val payload = decodePayload(result) ?: return null
        val target = payload.toTarget() ?: return null
        return ReaderPageTranslationHit(
            target = target,
            onTranslation = payload.onTranslation,
        )
    }

    fun targetsFromJavascriptResult(result: String?): List<ReaderPageTranslationTarget> {
        val payload = runCatching { json.decodeFromString<String>(result.orEmpty()) }.getOrNull()
            ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Payload.serializer()), payload)
        }.getOrDefault(emptyList()).mapNotNull(Payload::toTarget)
    }

    private fun decodePayload(result: String?): Payload? {
        val payload = runCatching { json.decodeFromString<String>(result.orEmpty()) }.getOrNull()
            ?: return null
        return runCatching {
            json.decodeFromString(Payload.serializer(), payload)
        }.getOrNull()
    }

    @Serializable
    private data class Payload(
        val id: String,
        val text: String,
        val onTranslation: Boolean = false,
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
