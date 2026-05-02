package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPositionStateTest {
    @Test
    fun pageProgressDoesNotChangeTheWebViewReloadTarget() {
        val state = ReaderPositionState(ReaderChapterPosition(index = 4, progress = 0.0))

        val updated = state.recordPageProgress(0.42)

        assertEquals(ReaderChapterPosition(index = 4, progress = 0.0), updated.loadPosition)
        assertEquals(ReaderChapterPosition(index = 4, progress = 0.42), updated.displayedPosition)
    }

    @Test
    fun appearanceReloadUsesTheLatestDisplayedPosition() {
        val state = ReaderPositionState(ReaderChapterPosition(index = 4, progress = 0.0))
            .recordPageProgress(0.42)

        val updated = state.prepareReloadAtDisplayedPosition()

        assertEquals(ReaderChapterPosition(index = 4, progress = 0.42), updated.loadPosition)
        assertEquals(ReaderChapterPosition(index = 4, progress = 0.42), updated.displayedPosition)
    }

    @Test
    fun chapterNavigationUpdatesBothDisplayedPositionAndReloadTarget() {
        val target = ReaderChapterPosition(index = 8, progress = 0.25)

        val updated = ReaderPositionState(ReaderChapterPosition(index = 4, progress = 0.0))
            .jumpTo(target)

        assertEquals(target, updated.loadPosition)
        assertEquals(target, updated.displayedPosition)
    }

    @Test
    fun internalLinkJumpKeepsFragmentLoadedAfterRestoreSyncsProgress() {
        val target = ReaderChapterPosition(index = 8, progress = 0.0)

        val jumped = ReaderPositionState(ReaderChapterPosition(index = 4, progress = 0.0))
            .jumpTo(target, fragment = "toc-001")
        val synced = jumped.recordPageProgress(0.13)

        assertEquals(target, jumped.loadPosition)
        assertEquals("toc-001", jumped.loadFragment)
        assertEquals(ReaderChapterPosition(index = 8, progress = 0.13), synced.displayedPosition)
        assertEquals("toc-001", synced.loadFragment)
    }
}
