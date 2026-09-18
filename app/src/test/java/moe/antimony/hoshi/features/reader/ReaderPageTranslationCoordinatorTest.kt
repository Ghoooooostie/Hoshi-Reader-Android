package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPageTranslationCoordinatorTest {
    @Test
    fun cachedTranslationCanBeOverwrittenForRetranslate() {
        val coordinator = ReaderPageTranslationCoordinator()
        val chapterKey = "book-1:0"

        coordinator.cacheTranslation(chapterKey, "p-1", "旧译文")
        coordinator.cacheTranslation(chapterKey, "p-1", "新译文")

        assertEquals("新译文", coordinator.cachedTranslation(chapterKey, "p-1"))
    }

    @Test
    fun translatedTargetsAreQueuedOnlyOnceAndCachedAfterSuccess() {
        val coordinator = ReaderPageTranslationCoordinator()
        val chapterKey = "book-1:0"
        val first = ReaderPageTranslationTarget(id = "p-1", text = "第一段")
        val second = ReaderPageTranslationTarget(id = "p-2", text = "第二段")

        coordinator.enqueue(chapterKey, listOf(first, second, first))

        assertEquals(first, coordinator.pollNext())
        coordinator.markSuccess(first.id, "translation-1")

        coordinator.enqueue(chapterKey, listOf(first, second))

        assertEquals("translation-1", coordinator.cachedTranslation(chapterKey, first.id))
        assertEquals(second, coordinator.pollNext())
        assertNull(coordinator.pollNext())
    }

    @Test
    fun failedTargetEntersBackoffThenCanBeRetried() {
        val coordinator = ReaderPageTranslationCoordinator()
        val chapterKey = "book-1:0"
        val target = ReaderPageTranslationTarget(id = "p-1", text = "第一段")

        coordinator.enqueue(chapterKey, listOf(target))
        assertEquals(target, coordinator.pollNext())
        coordinator.markFailure(target.id, nowMillis = 0)

        // 退避期内自动重排被阻止。
        coordinator.enqueue(chapterKey, listOf(target), nowMillis = 1_000)
        assertNull(coordinator.pollNext())

        // 退避结束后可再次排队。
        coordinator.enqueue(chapterKey, listOf(target), nowMillis = 60_000)
        assertEquals(target, coordinator.pollNext())
    }

    @Test
    fun changingChapterKeepsChapterCacheForRevisit() {
        val coordinator = ReaderPageTranslationCoordinator()
        val firstChapter = "book-1:0"
        val secondChapter = "book-1:1"
        val firstTarget = ReaderPageTranslationTarget(id = "p-1", text = "第一段")
        val secondTarget = ReaderPageTranslationTarget(id = "p-2", text = "第二段")

        coordinator.enqueue(firstChapter, listOf(firstTarget))
        assertEquals(firstTarget, coordinator.pollNext())
        coordinator.markSuccess(firstTarget.id, "translation-1")

        coordinator.enqueue(secondChapter, listOf(secondTarget))

        assertEquals("translation-1", coordinator.cachedTranslation(firstChapter, firstTarget.id))
        assertNull(coordinator.cachedTranslation(secondChapter, firstTarget.id))
        assertEquals(secondTarget, coordinator.pollNext())
        assertNull(coordinator.pollNext())
    }

    @Test
    fun inflightRequestCachesToOriginatingChapterAfterChapterSwitch() {
        val coordinator = ReaderPageTranslationCoordinator()
        val firstChapter = "book-1:0"
        val secondChapter = "book-1:1"
        val target = ReaderPageTranslationTarget(id = "p-1", text = "第一段")

        coordinator.enqueue(firstChapter, listOf(target))
        assertEquals(target, coordinator.pollNext())
        assertEquals(firstChapter, coordinator.inFlightChapter())

        // 翻页：切换到另一章节，但不打断在途请求。
        coordinator.setActiveChapter(secondChapter)

        coordinator.markSuccess(target.id, "translation-1")

        // 译文应按发起章节（firstChapter）缓存，而不是被丢弃或落到新章节。
        assertEquals("translation-1", coordinator.cachedTranslation(firstChapter, target.id))
        assertNull(coordinator.cachedTranslation(secondChapter, target.id))
    }

    @Test
    fun markSuccessIsIgnoredWithoutMatchingInflight() {
        val coordinator = ReaderPageTranslationCoordinator()
        val chapterKey = "book-1:0"

        coordinator.markSuccess("p-1", "stale")
        assertNull(coordinator.cachedTranslation(chapterKey, "p-1"))
    }

    @Test
    fun clearRemovesCachedTranslationsFromEveryChapter() {
        val coordinator = ReaderPageTranslationCoordinator()
        val firstChapter = "book-1:0"
        val secondChapter = "book-1:1"
        val firstTarget = ReaderPageTranslationTarget(id = "p-1", text = "第一段")
        val secondTarget = ReaderPageTranslationTarget(id = "p-2", text = "第二段")

        coordinator.enqueue(firstChapter, listOf(firstTarget))
        assertEquals(firstTarget, coordinator.pollNext())
        coordinator.markSuccess(firstTarget.id, "translation-1")

        coordinator.enqueue(secondChapter, listOf(secondTarget))
        assertEquals(secondTarget, coordinator.pollNext())
        coordinator.markSuccess(secondTarget.id, "translation-2")

        coordinator.clear()

        assertNull(coordinator.cachedTranslation(firstChapter, firstTarget.id))
        assertNull(coordinator.cachedTranslation(secondChapter, secondTarget.id))
    }
}
