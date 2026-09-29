package moe.antimony.hoshi.features.sasayaki

enum class SasayakiAudiobookFormat {
    Unknown,
    Mp3,
    M4b,
    Opus,
}

data class SasayakiAudiobookInfo(
    val format: SasayakiAudiobookFormat = SasayakiAudiobookFormat.Unknown,
    val metadata: SasayakiAudiobookMetadata = SasayakiAudiobookMetadata.Empty,
    val chapters: List<SasayakiAudiobookChapter> = emptyList(),
    val durationSeconds: Double? = null,
) {
    companion object {
        val Empty = SasayakiAudiobookInfo()
    }
}

data class SasayakiAudiobookPlatformInfo(
    val metadata: SasayakiAudiobookMetadata = SasayakiAudiobookMetadata.Empty,
    val durationSeconds: Double? = null,
) {
    companion object {
        val Empty = SasayakiAudiobookPlatformInfo()
    }
}
