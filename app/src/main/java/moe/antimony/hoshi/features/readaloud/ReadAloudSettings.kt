package moe.antimony.hoshi.features.readaloud

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ReadAloudSettings(
    val speechRate: Float = DefaultSpeechRate,
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
    /** 朗读播放中点击查词时自动暂停（参考有声书的查词自动暂停）。 */
    val pauseForLookup: Boolean = true,
    /** 朗读播放中整页翻译覆盖层显示时自动暂停。 */
    val pauseForPageTranslation: Boolean = true,
    /** 播放时高亮当前正在朗读的句子；关闭后仍会滚动跟随，只是不显示高亮。 */
    val highlightWhilePlaying: Boolean = true,
    /** 跟读翻译：朗读到哪一句就翻译哪一句，译文显示在原段落下方。 */
    val translateCurrentSentence: Boolean = false,
    /** 每个朗读单元（一句，或按页朗读时的整页）连续朗读几遍后才进入下一个单元。 */
    val sentenceRepeatCount: Int = DefaultSentenceRepeatCount,
) {
    companion object {
        const val DefaultSpeechRate = 1.0f
        const val MinimumSpeechRate = 0.5f
        const val MaximumSpeechRate = 2.0f
        const val DefaultSentenceRepeatCount = 1
        const val MinimumSentenceRepeatCount = 1
        const val MaximumSentenceRepeatCount = 5
    }
}

internal fun ReadAloudSettings.normalized(): ReadAloudSettings = copy(
    speechRate = speechRate.coerceIn(
        ReadAloudSettings.MinimumSpeechRate,
        ReadAloudSettings.MaximumSpeechRate,
    ),
    sentenceRepeatCount = sentenceRepeatCount.coerceIn(
        ReadAloudSettings.MinimumSentenceRepeatCount,
        ReadAloudSettings.MaximumSentenceRepeatCount,
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
    speechRate = this[Keys.speechRate] ?: ReadAloudSettings.DefaultSpeechRate,
    selectedSystemEngineName = this[Keys.selectedSystemEngineName]?.takeIf { it.isNotEmpty() },
    ignoreAudioFocus = this[Keys.ignoreAudioFocus] ?: false,
    pauseWhilePhoneCalls = this[Keys.pauseWhilePhoneCalls] ?: false,
    wakeLock = this[Keys.wakeLock] ?: false,
    mediaButtonPerNext = this[Keys.mediaButtonPerNext] ?: false,
    readAloudByPage = this[Keys.readAloudByPage] ?: false,
    pauseForLookup = this[Keys.pauseForLookup] ?: true,
    pauseForPageTranslation = this[Keys.pauseForPageTranslation] ?: true,
    highlightWhilePlaying = this[Keys.highlightWhilePlaying] ?: true,
    translateCurrentSentence = this[Keys.translateCurrentSentence] ?: false,
    sentenceRepeatCount = this[Keys.sentenceRepeatCount] ?: ReadAloudSettings.DefaultSentenceRepeatCount,
)

private fun MutablePreferences.writeReadAloudSettings(settings: ReadAloudSettings) {
    this[Keys.speechRate] = settings.speechRate
    this[Keys.selectedSystemEngineName] = settings.selectedSystemEngineName ?: ""
    this[Keys.ignoreAudioFocus] = settings.ignoreAudioFocus
    this[Keys.pauseWhilePhoneCalls] = settings.pauseWhilePhoneCalls
    this[Keys.wakeLock] = settings.wakeLock
    this[Keys.mediaButtonPerNext] = settings.mediaButtonPerNext
    this[Keys.readAloudByPage] = settings.readAloudByPage
    this[Keys.pauseForLookup] = settings.pauseForLookup
    this[Keys.pauseForPageTranslation] = settings.pauseForPageTranslation
    this[Keys.highlightWhilePlaying] = settings.highlightWhilePlaying
    this[Keys.translateCurrentSentence] = settings.translateCurrentSentence
    this[Keys.sentenceRepeatCount] = settings.sentenceRepeatCount
}

private object Keys {
    val speechRate = floatPreferencesKey("speechRate")
    val selectedSystemEngineName = stringPreferencesKey("selectedSystemEngineName")
    val ignoreAudioFocus = booleanPreferencesKey("ignoreAudioFocus")
    val pauseWhilePhoneCalls = booleanPreferencesKey("pauseWhilePhoneCalls")
    val wakeLock = booleanPreferencesKey("wakeLock")
    val mediaButtonPerNext = booleanPreferencesKey("mediaButtonPerNext")
    val readAloudByPage = booleanPreferencesKey("readAloudByPage")
    val pauseForLookup = booleanPreferencesKey("pauseForLookup")
    val pauseForPageTranslation = booleanPreferencesKey("pauseForPageTranslation")
    val highlightWhilePlaying = booleanPreferencesKey("highlightWhilePlaying")
    val translateCurrentSentence = booleanPreferencesKey("translateCurrentSentence")
    val sentenceRepeatCount = intPreferencesKey("sentenceRepeatCount")
}
