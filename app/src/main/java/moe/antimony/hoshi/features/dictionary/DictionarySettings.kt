package moe.antimony.hoshi.features.dictionary

import android.content.Context

data class DictionarySettings(
    val dictionaryTabDefault: Boolean = false,
    val maxResults: Int = 16,
    val scanLength: Int = 16,
    val collapseDictionaries: Boolean = false,
    val compactGlossaries: Boolean = true,
    val showExpressionTags: Boolean = false,
    val harmonicFrequency: Boolean = false,
    val deduplicatePitchAccents: Boolean = false,
    val compactPitchAccents: Boolean = true,
    val customCSS: String = "",
) {
    fun normalized(): DictionarySettings = copy(
        maxResults = maxResults.coerceIn(MIN_MAX_RESULTS, MAX_MAX_RESULTS),
        scanLength = scanLength.coerceIn(MIN_SCAN_LENGTH, MAX_SCAN_LENGTH),
    )

    companion object {
        const val MIN_MAX_RESULTS = 1
        const val MAX_MAX_RESULTS = 50
        const val MIN_SCAN_LENGTH = 1
        const val MAX_SCAN_LENGTH = 64
    }
}

class DictionarySettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("dictionary-settings", Context.MODE_PRIVATE)

    fun load(): DictionarySettings = DictionarySettings(
        dictionaryTabDefault = preferences.getBoolean(KEY_DICTIONARY_TAB_DEFAULT, false),
        maxResults = preferences.getInt(KEY_MAX_RESULTS, 16),
        scanLength = preferences.getInt(KEY_SCAN_LENGTH, 16),
        collapseDictionaries = preferences.getBoolean(KEY_COLLAPSE_DICTIONARIES, false),
        compactGlossaries = preferences.getBoolean(KEY_COMPACT_GLOSSARIES, true),
        showExpressionTags = preferences.getBoolean(KEY_SHOW_EXPRESSION_TAGS, false),
        harmonicFrequency = preferences.getBoolean(KEY_HARMONIC_FREQUENCY, false),
        deduplicatePitchAccents = preferences.getBoolean(KEY_DEDUPLICATE_PITCH_ACCENTS, false),
        compactPitchAccents = preferences.getBoolean(KEY_COMPACT_PITCH_ACCENTS, true),
        customCSS = preferences.getString(KEY_CUSTOM_CSS, "").orEmpty(),
    ).normalized()

    fun save(settings: DictionarySettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putBoolean(KEY_DICTIONARY_TAB_DEFAULT, normalized.dictionaryTabDefault)
            .putInt(KEY_MAX_RESULTS, normalized.maxResults)
            .putInt(KEY_SCAN_LENGTH, normalized.scanLength)
            .putBoolean(KEY_COLLAPSE_DICTIONARIES, normalized.collapseDictionaries)
            .putBoolean(KEY_COMPACT_GLOSSARIES, normalized.compactGlossaries)
            .putBoolean(KEY_SHOW_EXPRESSION_TAGS, normalized.showExpressionTags)
            .putBoolean(KEY_HARMONIC_FREQUENCY, normalized.harmonicFrequency)
            .putBoolean(KEY_DEDUPLICATE_PITCH_ACCENTS, normalized.deduplicatePitchAccents)
            .putBoolean(KEY_COMPACT_PITCH_ACCENTS, normalized.compactPitchAccents)
            .putString(KEY_CUSTOM_CSS, normalized.customCSS)
            .apply()
    }

    private companion object {
        const val KEY_DICTIONARY_TAB_DEFAULT = "dictionaryTabDefault"
        const val KEY_MAX_RESULTS = "maxResults"
        const val KEY_SCAN_LENGTH = "scanLength"
        const val KEY_COLLAPSE_DICTIONARIES = "collapseDictionaries"
        const val KEY_COMPACT_GLOSSARIES = "compactGlossaries"
        const val KEY_SHOW_EXPRESSION_TAGS = "showExpressionTags"
        const val KEY_HARMONIC_FREQUENCY = "harmonicFrequency"
        const val KEY_DEDUPLICATE_PITCH_ACCENTS = "deduplicatePitchAccents"
        const val KEY_COMPACT_PITCH_ACCENTS = "compactPitchAccents"
        const val KEY_CUSTOM_CSS = "customCSS"
    }
}
