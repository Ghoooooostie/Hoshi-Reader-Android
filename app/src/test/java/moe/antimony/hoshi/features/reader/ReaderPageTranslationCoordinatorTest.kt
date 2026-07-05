package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPageTranslationCoordinatorTest {
    @Test
    fun translatedTargetsAreQueuedOnlyOnceAndCachedAfterSuccess() {
        val coordinator = ReaderPageTranslationCoordinator()
        val chapterKey = "book-1:0"
        val first = ReaderPageTranslationTarget(id = "p-1", text = "第一段")
        val second = ReaderPageTranslationTarget(id = "p-2", text = "第二段")

        coordinator.enqueue(chapterKey, listOf(first, second, first))

        assertEquals(first, coordinator.pollNext(chapterKey))
        coordinator.markSuccess(chapterKey, first.id, "translation-1")

        coordinator.enqueue(chapterKey, listOf(first, second))

        assertEquals("translation-1", coordinator.cachedTranslation(chapterKey, first.id))
        assertEquals(second, coordinator.pollNext(chapterKey))
        assertNull(coordinator.pollNext(chapterKey))
    }

    @Test
    fun failedTargetCanBeQueuedAgain() {
        val coordinator = ReaderPageTranslationCoordinator()
        val chapterKey = "book-1:0"
        val target = ReaderPageTranslationTarget(id = "p-1", text = "第一段")

        coordinator.enqueue(chapterKey, listOf(target))

        assertEquals(target, coordinator.pollNext(chapterKey))
        coordinator.markFailure(chapterKey, target.id)

        coordinator.enqueue(chapterKey, listOf(target))

        assertEquals(target, coordinator.pollNext(chapterKey))
    }

    @Test
    fun changingChapterClearsPendingQueueButKeepsChapterCacheForRevisit() {
        val coordinator = ReaderPageTranslationCoordinator()
        val firstChapter = "book-1:0"
        val secondChapter = "book-1:1"
        val firstTarget = ReaderPageTranslationTarget(id = "p-1", text = "第一段")
        val secondTarget = ReaderPageTranslationTarget(id = "p-2", text = "第二段")

        coordinator.enqueue(firstChapter, listOf(firstTarget))
        assertEquals(firstTarget, coordinator.pollNext(firstChapter))
        coordinator.markSuccess(firstChapter, firstTarget.id, "translation-1")

        coordinator.enqueue(secondChapter, listOf(secondTarget))

        assertEquals("translation-1", coordinator.cachedTranslation(firstChapter, firstTarget.id))
        assertNull(coordinator.cachedTranslation(secondChapter, firstTarget.id))
        assertEquals(secondTarget, coordinator.pollNext(secondChapter))
        assertNull(coordinator.pollNext(firstChapter))
    }

    @Test
    fun clearRemovesCachedTranslationsFromEveryChapter() {
        val coordinator = ReaderPageTranslationCoordinator()
        val firstChapter = "book-1:0"
        val secondChapter = "book-1:1"
        val firstTarget = ReaderPageTranslationTarget(id = "p-1", text = "第一段")
        val secondTarget = ReaderPageTranslationTarget(id = "p-2", text = "第二段")

        coordinator.enqueue(firstChapter, listOf(firstTarget))
        assertEquals(firstTarget, coordinator.pollNext(firstChapter))
        coordinator.markSuccess(firstChapter, firstTarget.id, "translation-1")

        coordinator.enqueue(secondChapter, listOf(secondTarget))
        assertEquals(secondTarget, coordinator.pollNext(secondChapter))
        coordinator.markSuccess(secondChapter, secondTarget.id, "translation-2")

        coordinator.clear()

        assertNull(coordinator.cachedTranslation(firstChapter, firstTarget.id))
        assertNull(coordinator.cachedTranslation(secondChapter, secondTarget.id))
    }
}
