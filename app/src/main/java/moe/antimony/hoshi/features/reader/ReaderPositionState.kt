package moe.antimony.hoshi.features.reader

internal data class ReaderPositionState(
    val loadPosition: ReaderChapterPosition,
    val displayedPosition: ReaderChapterPosition = loadPosition,
) {
    fun recordPageProgress(progress: Double): ReaderPositionState =
        copy(displayedPosition = loadPosition.withProgress(progress))

    fun prepareReloadAtDisplayedPosition(): ReaderPositionState =
        copy(loadPosition = displayedPosition)

    fun jumpTo(position: ReaderChapterPosition): ReaderPositionState =
        copy(loadPosition = position, displayedPosition = position)
}
