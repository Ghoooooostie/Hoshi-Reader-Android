package moe.antimony.hoshi.features.reader

internal data class ReaderPageTranslationTarget(
    val id: String,
    val text: String,
)

internal class ReaderPageTranslationCoordinator {
    private var activeChapterKey: String? = null
    private val pendingQueue = ArrayDeque<ReaderPageTranslationTarget>()
    private val queuedIds = linkedSetOf<String>()
    private var inFlightId: String? = null
    private val translatedByChapter = linkedMapOf<String, LinkedHashMap<String, String>>()

    fun clear() {
        clearActiveWork()
        translatedByChapter.clear()
    }

    fun clearActiveWork() {
        activeChapterKey = null
        pendingQueue.clear()
        queuedIds.clear()
        inFlightId = null
    }

    fun enqueue(
        chapterKey: String,
        targets: List<ReaderPageTranslationTarget>,
    ) {
        ensureChapter(chapterKey)
        val translated = translated(chapterKey)
        targets.forEach { target ->
            if (target.text.isBlank()) return@forEach
            if (translated.containsKey(target.id)) return@forEach
            if (queuedIds.contains(target.id)) return@forEach
            if (inFlightId == target.id) return@forEach
            pendingQueue.addLast(target)
            queuedIds += target.id
        }
    }

    fun pollNext(chapterKey: String): ReaderPageTranslationTarget? {
        ensureChapter(chapterKey)
        if (inFlightId != null) return null
        val next = pendingQueue.removeFirstOrNull() ?: return null
        queuedIds.remove(next.id)
        inFlightId = next.id
        return next
    }

    fun markSuccess(
        chapterKey: String,
        targetId: String,
        translation: String,
    ) {
        if (!matchesInFlight(chapterKey, targetId)) return
        inFlightId = null
        translated(chapterKey)[targetId] = translation
    }

    fun markFailure(
        chapterKey: String,
        targetId: String,
    ) {
        if (!matchesInFlight(chapterKey, targetId)) return
        inFlightId = null
    }

    fun cachedTranslation(
        chapterKey: String,
        targetId: String,
    ): String? {
        return translatedByChapter[chapterKey]?.get(targetId)
    }

    private fun matchesInFlight(chapterKey: String, targetId: String): Boolean =
        activeChapterKey == chapterKey && inFlightId == targetId

    private fun ensureChapter(chapterKey: String) {
        if (activeChapterKey == chapterKey) return
        activeChapterKey = chapterKey
        pendingQueue.clear()
        queuedIds.clear()
        inFlightId = null
    }

    private fun translated(chapterKey: String): LinkedHashMap<String, String> =
        translatedByChapter.getOrPut(chapterKey) { linkedMapOf() }
}
