package moe.antimony.hoshi.features.reader

internal data class ReaderPageTranslationTarget(
    val id: String,
    val text: String,
)

/** 自动重排失败后，多久内不再自动重试该段落（避免滚动回来反复打接口）。 */
private const val FAILURE_BACKOFF_MILLIS = 30_000L

internal class ReaderPageTranslationCoordinator {
    private var activeChapterKey: String? = null
    private val pendingQueue = ArrayDeque<ReaderPageTranslationTarget>()
    private val queuedIds = linkedSetOf<String>()
    // 在途请求记录其发起章节，便于翻页后仍能正确缓存到原章节，而不是被丢弃（浪费 token）。
    private var inFlightChapterKey: String? = null
    private var inFlightId: String? = null
    private val translatedByChapter = linkedMapOf<String, LinkedHashMap<String, String>>()
    // 每章节记录失败的 targetId -> 下次允许自动重试的时间戳。
    private val failedByChapter = linkedMapOf<String, LinkedHashMap<String, Long>>()

    fun clear() {
        clearActiveWork()
        translatedByChapter.clear()
        failedByChapter.clear()
    }

    fun clearActiveWork() {
        activeChapterKey = null
        pendingQueue.clear()
        queuedIds.clear()
        // 故意不清 inFlightChapterKey / inFlightId，让正在飞行的请求完成并缓存。
    }

    fun setActiveChapter(chapterKey: String) {
        if (activeChapterKey == chapterKey) return
        activeChapterKey = chapterKey
        pendingQueue.clear()
        queuedIds.clear()
        // 切换章节时不打断在途请求。
    }

    fun enqueue(
        chapterKey: String,
        targets: List<ReaderPageTranslationTarget>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        setActiveChapter(chapterKey)
        val translated = translated(chapterKey)
        val failed = failed(chapterKey)
        targets.forEach { target ->
            if (target.text.isBlank()) return@forEach
            if (translated.containsKey(target.id)) return@forEach
            val retryAt = failed[target.id]
            if (retryAt != null && nowMillis < retryAt) return@forEach
            if (queuedIds.contains(target.id)) return@forEach
            if (inFlightId == target.id) return@forEach
            pendingQueue.addLast(target)
            queuedIds += target.id
        }
    }

    fun pollNext(): ReaderPageTranslationTarget? {
        if (inFlightId != null) return null
        val chapterKey = activeChapterKey ?: return null
        val next = pendingQueue.removeFirstOrNull() ?: return null
        queuedIds.remove(next.id)
        inFlightChapterKey = chapterKey
        inFlightId = next.id
        return next
    }

    fun markSuccess(
        targetId: String,
        translation: String,
    ) {
        if (!matchesInFlight(targetId)) return
        val chapterKey = inFlightChapterKey ?: return
        inFlightChapterKey = null
        inFlightId = null
        cacheTranslation(chapterKey, targetId, translation)
        failedByChapter[chapterKey]?.remove(targetId)
    }

    fun markFailure(
        targetId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!matchesInFlight(targetId)) return
        val chapterKey = inFlightChapterKey ?: return
        inFlightChapterKey = null
        inFlightId = null
        failed(chapterKey)[targetId] = nowMillis + FAILURE_BACKOFF_MILLIS
    }

    /** 当前在途请求所属章节，供上层决定是否要把译文应用到当前可见页。 */
    fun inFlightChapter(): String? = inFlightChapterKey

    fun cachedTranslation(
        chapterKey: String,
        targetId: String,
    ): String? {
        return translatedByChapter[chapterKey]?.get(targetId)
    }

    fun cacheTranslation(
        chapterKey: String,
        targetId: String,
        translation: String,
    ) {
        translated(chapterKey)[targetId] = translation
    }

    private fun matchesInFlight(targetId: String): Boolean =
        inFlightId == targetId && inFlightChapterKey != null

    private fun translated(chapterKey: String): LinkedHashMap<String, String> =
        translatedByChapter.getOrPut(chapterKey) { linkedMapOf() }

    private fun failed(chapterKey: String): LinkedHashMap<String, Long> =
        failedByChapter.getOrPut(chapterKey) { linkedMapOf() }
}
