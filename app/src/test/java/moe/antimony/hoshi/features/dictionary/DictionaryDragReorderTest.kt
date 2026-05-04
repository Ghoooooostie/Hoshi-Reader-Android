package moe.antimony.hoshi.features.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryDragReorderTest {
    @Test
    fun draggedCenterPastNextRowTargetsNextDictionary() {
        val target = DictionaryDragReorder.targetIndex(
            startIndex = 0,
            draggedCenterY = 150f,
            visibleRows = listOf(
                DictionaryDragReorder.RowBounds(index = 0, top = 0f, bottom = 100f),
                DictionaryDragReorder.RowBounds(index = 1, top = 100f, bottom = 200f),
            ),
        )

        assertEquals(1, target)
    }

    @Test
    fun draggedCenterBeforePreviousRowTargetsPreviousDictionary() {
        val target = DictionaryDragReorder.targetIndex(
            startIndex = 1,
            draggedCenterY = 50f,
            visibleRows = listOf(
                DictionaryDragReorder.RowBounds(index = 0, top = 0f, bottom = 100f),
                DictionaryDragReorder.RowBounds(index = 1, top = 100f, bottom = 200f),
            ),
        )

        assertEquals(0, target)
    }

    @Test
    fun missingVisibleRowsKeepOriginalIndex() {
        val target = DictionaryDragReorder.targetIndex(
            startIndex = 1,
            draggedCenterY = 500f,
            visibleRows = emptyList(),
        )

        assertEquals(1, target)
    }

    @Test
    fun draggedCenterAboveVisibleRowsTargetsFirstVisibleDictionary() {
        val target = DictionaryDragReorder.targetIndex(
            startIndex = 2,
            draggedCenterY = -200f,
            visibleRows = listOf(
                DictionaryDragReorder.RowBounds(index = 1, top = 100f, bottom = 200f),
                DictionaryDragReorder.RowBounds(index = 2, top = 200f, bottom = 300f),
            ),
        )

        assertEquals(1, target)
    }

    @Test
    fun draggedCenterBelowVisibleRowsTargetsLastVisibleDictionary() {
        val target = DictionaryDragReorder.targetIndex(
            startIndex = 0,
            draggedCenterY = 700f,
            visibleRows = listOf(
                DictionaryDragReorder.RowBounds(index = 0, top = 0f, bottom = 100f),
                DictionaryDragReorder.RowBounds(index = 1, top = 100f, bottom = 200f),
            ),
        )

        assertEquals(1, target)
    }

    @Test
    fun previewOrderMovesDraggedItemDown() {
        val order = DictionaryDragReorder.previewOrder(
            items = listOf("a", "b", "c", "d"),
            fromIndex = 0,
            toIndex = 2,
        )

        assertEquals(listOf("b", "c", "a", "d"), order)
    }

    @Test
    fun previewOrderMovesDraggedItemUp() {
        val order = DictionaryDragReorder.previewOrder(
            items = listOf("a", "b", "c", "d"),
            fromIndex = 3,
            toIndex = 1,
        )

        assertEquals(listOf("a", "d", "b", "c"), order)
    }

    @Test
    fun previewOrderKeepsOriginalOrderForInvalidIndexes() {
        val order = DictionaryDragReorder.previewOrder(
            items = listOf("a", "b"),
            fromIndex = 5,
            toIndex = 1,
        )

        assertEquals(listOf("a", "b"), order)
    }

    @Test
    fun itemOffsetKeepsDraggedItemUnderPointerWhenMovingDown() {
        val offsets = List(4) { index ->
            DictionaryDragReorder.itemOffset(
                index = index,
                startIndex = 0,
                targetIndex = 2,
                dragOffset = 140f,
                draggedItemSize = 72f,
            )
        }

        assertEquals(listOf(140f, -72f, -72f, 0f), offsets)
    }

    @Test
    fun itemOffsetKeepsDraggedItemUnderPointerWhenMovingUp() {
        val offsets = List(4) { index ->
            DictionaryDragReorder.itemOffset(
                index = index,
                startIndex = 3,
                targetIndex = 1,
                dragOffset = -140f,
                draggedItemSize = 72f,
            )
        }

        assertEquals(listOf(0f, 72f, 72f, -140f), offsets)
    }

    @Test
    fun itemOffsetDoesNotMoveNeighborsWhenTargetIsStart() {
        val offsets = List(3) { index ->
            DictionaryDragReorder.itemOffset(
                index = index,
                startIndex = 1,
                targetIndex = 1,
                dragOffset = 20f,
                draggedItemSize = 72f,
            )
        }

        assertEquals(listOf(0f, 20f, 0f), offsets)
    }

    @Test
    fun adjustedDragOffsetCompensatesWhenWorkingOrderMovesDown() {
        val adjusted = DictionaryDragReorder.adjustedDragOffsetAfterMove(
            dragOffset = 160f,
            fromIndex = 0,
            toIndex = 1,
            itemSize = 72f,
        )

        assertEquals(88f, adjusted, 0.001f)
    }

    @Test
    fun adjustedDragOffsetCompensatesWhenWorkingOrderMovesUp() {
        val adjusted = DictionaryDragReorder.adjustedDragOffsetAfterMove(
            dragOffset = -160f,
            fromIndex = 3,
            toIndex = 1,
            itemSize = 72f,
        )

        assertEquals(-16f, adjusted, 0.001f)
    }

    @Test
    fun autoScrollDeltaIsNegativeNearTopEdge() {
        val delta = DictionaryDragReorder.autoScrollDelta(
            pointerY = 12f,
            viewportStart = 0f,
            viewportEnd = 600f,
            edgeThreshold = 64f,
            maxDelta = 24f,
        )

        assertEquals(-19.5f, delta, 0.001f)
    }

    @Test
    fun autoScrollDeltaIsPositiveNearBottomEdge() {
        val delta = DictionaryDragReorder.autoScrollDelta(
            pointerY = 580f,
            viewportStart = 0f,
            viewportEnd = 600f,
            edgeThreshold = 80f,
            maxDelta = 20f,
        )

        assertEquals(15f, delta, 0.001f)
    }

    @Test
    fun autoScrollDeltaIsZeroAwayFromEdges() {
        val delta = DictionaryDragReorder.autoScrollDelta(
            pointerY = 300f,
            viewportStart = 0f,
            viewportEnd = 600f,
            edgeThreshold = 80f,
            maxDelta = 20f,
        )

        assertEquals(0f, delta, 0.001f)
    }
}
