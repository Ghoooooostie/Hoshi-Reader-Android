package moe.antimony.hoshi.features.reader

internal data class ReaderPositionState(
    val loadPosition: ReaderChapterPosition,
    val displayedPosition: ReaderChapterPosition = loadPosition,
    val loadFragment: String? = null,
) {
    fun recordPageProgress(progress: Double): ReaderPositionState =
        copy(displayedPosition = loadPosition.withProgress(progress))

    fun prepareReloadAtDisplayedPosition(): ReaderPositionState =
        copy(loadPosition = displayedPosition, loadFragment = null)

    fun jumpTo(position: ReaderChapterPosition, fragment: String? = null): ReaderPositionState =
        copy(loadPosition = position, displayedPosition = position, loadFragment = fragment)
}
