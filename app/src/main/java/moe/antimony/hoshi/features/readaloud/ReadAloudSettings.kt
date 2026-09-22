package moe.antimony.hoshi.features.readaloud

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    val selectedModelId: String? = null,
    val selectedSystemEngineName: String? = null,
    /** 忽略音频焦点：与其他应用同时播放音频。 */
    val ignoreAudioFocus: Boolean = false,
    /** 来电期间暂停朗读（配合忽略音频焦点使用的电话状态监听）。 */
    val pauseWhilePhoneCalls: Boolean = false,
    /** 朗读期间持有唤醒锁，防止后台朗读被系统休眠中断。 */
    val wakeLock: Boolean = false,
    /** 媒体按钮上一首/下一首映射到上一段/下一段，而不是上一句/下一句。 */
    val mediaButtonPerNext: Boolean = false,
    /** 按页朗读：整页作为一个朗读单元，翻页时停顿一下。 */
    val readAloudByPage: Boolean = false,
    /** 长按句子时同时从此句开始朗读（需先在朗读设置中开启）。 */
    val startReadingFromLongPress: Boolean = false,
    /** 朗读播放中点击查词时自动暂停（参考有声书的查词自动暂停）。 */
    val pauseForLookup: Boolean = true,
    /** 朗读播放中整页翻译覆盖层显示时自动暂停。 */
    val pauseForPageTranslation: Boolean = true,
    /** 播放时高亮当前正在朗读的句子；关闭后仍会滚动跟随，只是不显示高亮。 */
    val highlightWhilePlaying: Boolean = true,
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
    selectedModelId = this[Keys.selectedModelId]?.takeIf { it.isNotEmpty() },
    selectedSystemEngineName = this[Keys.selectedSystemEngineName]?.takeIf { it.isNotEmpty() },
    ignoreAudioFocus = this[Keys.ignoreAudioFocus] ?: false,
    pauseWhilePhoneCalls = this[Keys.pauseWhilePhoneCalls] ?: false,
    wakeLock = this[Keys.wakeLock] ?: false,
    mediaButtonPerNext = this[Keys.mediaButtonPerNext] ?: false,
    readAloudByPage = this[Keys.readAloudByPage] ?: false,
    startReadingFromLongPress = this[Keys.startReadingFromLongPress] ?: false,
    pauseForLookup = this[Keys.pauseForLookup] ?: true,
    pauseForPageTranslation = this[Keys.pauseForPageTranslation] ?: true,
    highlightWhilePlaying = this[Keys.highlightWhilePlaying] ?: true,
)

private fun MutablePreferences.writeReadAloudSettings(settings: ReadAloudSettings) {
    this[Keys.engine] = settings.engineId.rawValue
    this[Keys.speechRate] = settings.speechRate
    this[Keys.selectedModelId] = settings.selectedModelId ?: ""
    this[Keys.selectedSystemEngineName] = settings.selectedSystemEngineName ?: ""
    this[Keys.ignoreAudioFocus] = settings.ignoreAudioFocus
    this[Keys.pauseWhilePhoneCalls] = settings.pauseWhilePhoneCalls
    this[Keys.wakeLock] = settings.wakeLock
    this[Keys.mediaButtonPerNext] = settings.mediaButtonPerNext
    this[Keys.readAloudByPage] = settings.readAloudByPage
    this[Keys.startReadingFromLongPress] = settings.startReadingFromLongPress
    this[Keys.pauseForLookup] = settings.pauseForLookup
    this[Keys.pauseForPageTranslation] = settings.pauseForPageTranslation
    this[Keys.highlightWhilePlaying] = settings.highlightWhilePlaying
}

private object Keys {
    val engine = stringPreferencesKey("engine")
    val speechRate = floatPreferencesKey("speechRate")
    val selectedModelId = stringPreferencesKey("selectedModelId")
    val selectedSystemEngineName = stringPreferencesKey("selectedSystemEngineName")
    val ignoreAudioFocus = booleanPreferencesKey("ignoreAudioFocus")
    val pauseWhilePhoneCalls = booleanPreferencesKey("pauseWhilePhoneCalls")
    val wakeLock = booleanPreferencesKey("wakeLock")
    val mediaButtonPerNext = booleanPreferencesKey("mediaButtonPerNext")
    val readAloudByPage = booleanPreferencesKey("readAloudByPage")
    val startReadingFromLongPress = booleanPreferencesKey("startReadingFromLongPress")
    val pauseForLookup = booleanPreferencesKey("pauseForLookup")
    val pauseForPageTranslation = booleanPreferencesKey("pauseForPageTranslation")
    val highlightWhilePlaying = booleanPreferencesKey("highlightWhilePlaying")
}
