package moe.antimony.hoshi.features.readaloud

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ReadAloudEngineId(val rawValue: String) {
    System("system"),
    Local("local"),
    ;

    companion object {
        fun fromRawValue(value: String?): ReadAloudEngineId =
            entries.firstOrNull { it.rawValue == value } ?: System
    }
}

data class ReadAloudSettings(
    val engineId: ReadAloudEngineId = ReadAloudEngineId.System,
    val speechRate: Float = DefaultSpeechRate,
) {
    companion object {
        const val DefaultSpeechRate = 1.0f
        const val MinimumSpeechRate = 0.5f
        const val MaximumSpeechRate = 2.0f
    }
}

internal fun ReadAloudSettings.normalized(): ReadAloudSettings = copy(
    speechRate = speechRate.coerceIn(
        ReadAloudSettings.MinimumSpeechRate,
        ReadAloudSettings.MaximumSpeechRate,
    ),
)

private val Context.readAloudDataStore by preferencesDataStore(name = "read-aloud-settings")

fun Context.readAloudSettingsRepository(): ReadAloudSettingsRepository =
    ReadAloudSettingsRepository(readAloudDataStore)

class ReadAloudSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<ReadAloudSettings> = dataStore.data.map { preferences ->
        preferences.toReadAloudSettings().normalized()
    }

    suspend fun update(transform: (ReadAloudSettings) -> ReadAloudSettings) {
        dataStore.edit { preferences ->
            val current = preferences.toReadAloudSettings()
            preferences.writeReadAloudSettings(transform(current).normalized())
        }
    }
}

private fun Preferences.toReadAloudSettings(): ReadAloudSettings = ReadAloudSettings(
    engineId = ReadAloudEngineId.fromRawValue(this[Keys.engine]),
    speechRate = this[Keys.speechRate] ?: ReadAloudSettings.DefaultSpeechRate,
)

private fun MutablePreferences.writeReadAloudSettings(settings: ReadAloudSettings) {
    this[Keys.engine] = settings.engineId.rawValue
    this[Keys.speechRate] = settings.speechRate
}

private object Keys {
    val engine = stringPreferencesKey("engine")
    val speechRate = floatPreferencesKey("speechRate")
}
