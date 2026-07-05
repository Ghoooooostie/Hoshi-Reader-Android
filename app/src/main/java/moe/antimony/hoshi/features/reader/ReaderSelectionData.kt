package moe.antimony.hoshi.features.reader

data class ReaderSelectionData(
    val text: String,
    val sentence: String,
    val rect: ReaderSelectionRect,
    val normalizedOffset: Int?,
    val sentenceOffset: Int? = null,
)

data class ReaderSelectionRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

/** 将多行选区合并成一个完整锚点矩形。 */
internal fun readerPopupAnchorRect(
    selectionRects: List<ReaderSelectionRect>,
    fallback: ReaderSelectionRect,
): ReaderSelectionRect {
    val rects = selectionRects.ifEmpty { return fallback }
    val left = rects.minOf { it.x }
    val top = rects.minOf { it.y }
    val right = rects.maxOf { it.x + it.width }
    val bottom = rects.maxOf { it.y + it.height }
    return ReaderSelectionRect(
        x = left,
        y = top,
        width = right - left,
        height = bottom - top,
    )
}
