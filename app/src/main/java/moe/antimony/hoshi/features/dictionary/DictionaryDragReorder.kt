package moe.antimony.hoshi.features.dictionary

internal object DictionaryDragReorder {
    data class RowBounds(
        val index: Int,
        val top: Float,
        val bottom: Float,
    )

    fun targetIndex(
        startIndex: Int,
        draggedCenterY: Float,
        visibleRows: List<RowBounds>,
    ): Int {
        if (visibleRows.isEmpty()) return startIndex
        return visibleRows
            .minBy { row ->
                val center = (row.top + row.bottom) / 2f
                kotlin.math.abs(center - draggedCenterY)
            }
            .index
    }

    fun <T> previewOrder(
        items: List<T>,
        fromIndex: Int,
        toIndex: Int,
    ): List<T> {
        if (items.isEmpty() || fromIndex !in items.indices || toIndex !in items.indices) {
            return items
        }
        if (fromIndex == toIndex) return items
        val reordered = items.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        return reordered
    }

    fun itemOffset(
        index: Int,
        startIndex: Int,
        targetIndex: Int,
        dragOffset: Float,
        draggedItemSize: Float,
    ): Float {
        if (startIndex < 0 || targetIndex < 0 || draggedItemSize <= 0f) return 0f
        if (index == startIndex) return dragOffset
        return when {
            targetIndex > startIndex && index in (startIndex + 1)..targetIndex -> -draggedItemSize
            targetIndex < startIndex && index in targetIndex until startIndex -> draggedItemSize
            else -> 0f
        }
    }

    fun adjustedDragOffsetAfterMove(
        dragOffset: Float,
        fromIndex: Int,
        toIndex: Int,
        itemSize: Float,
    ): Float {
        if (itemSize <= 0f || fromIndex == toIndex) return dragOffset
        return dragOffset - (toIndex - fromIndex) * itemSize
    }

    fun autoScrollDelta(
        pointerY: Float,
        viewportStart: Float,
        viewportEnd: Float,
        edgeThreshold: Float,
        maxDelta: Float,
    ): Float {
        if (edgeThreshold <= 0f || maxDelta <= 0f || viewportEnd <= viewportStart) return 0f
        val topDistance = pointerY - viewportStart
        if (topDistance in 0f..edgeThreshold) {
            return -maxDelta * ((edgeThreshold - topDistance) / edgeThreshold)
        }
        val bottomDistance = viewportEnd - pointerY
        if (bottomDistance in 0f..edgeThreshold) {
            return maxDelta * ((edgeThreshold - bottomDistance) / edgeThreshold)
        }
        return 0f
    }
}
