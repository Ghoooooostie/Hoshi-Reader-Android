package moe.antimony.hoshi.features.advancedai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.antimony.hoshi.R

private val Context.advancedAiDataStore by preferencesDataStore(name = AdvancedAiSettingsRepository.DataStoreName)

/** 创建高级 AI 设置仓库。 */
internal fun Context.advancedAiSettingsRepository(): AdvancedAiSettingsRepository =
    AdvancedAiSettingsRepository(
        dataStore = advancedAiDataStore,
        defaultWordPrompt = getString(R.string.advanced_ai_default_word_prompt),
        defaultSentenceTranslationPrompt = getString(R.string.advanced_ai_default_sentence_translation_prompt),
        defaultSentencePrompt = getString(R.string.advanced_ai_default_sentence_prompt),
    )

/** 高级 AI 设置的 DataStore 仓库。 */
internal class AdvancedAiSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val defaultWordPrompt: String,
    private val defaultSentenceTranslationPrompt: String,
    private val defaultSentencePrompt: String,
    private val legacyWordPrompts: Set<String> = LEGACY_WORD_PROMPTS,
) {
    val settings: Flow<AdvancedAiSettings> = dataStore.data
        .map { preferences -> preferences.toAdvancedAiSettings() }

    /** 更新高级 AI 设置。 */
    suspend fun update(transform: (AdvancedAiSettings) -> AdvancedAiSettings) {
        dataStore.edit { preferences ->
            val next = transform(preferences.toAdvancedAiSettings())
            preferences[KEY_ENABLED] = next.enabled
            preferences[KEY_BASE_URL] = next.baseUrl
            preferences[KEY_API_KEY] = next.apiKey
            preferences[KEY_MODEL] = next.model
            preferences[KEY_WORD_PROMPT] = next.wordPrompt
            preferences[KEY_SENTENCE_TRANSLATION_PROMPT] = next.sentenceTranslationPrompt
            preferences[KEY_SENTENCE_PROMPT] = next.sentencePrompt
        }
    }

    /** 把偏好映射成高级 AI 设置。 */
    private fun Preferences.toAdvancedAiSettings(): AdvancedAiSettings =
        AdvancedAiSettings(
            enabled = this[KEY_ENABLED] ?: false,
            baseUrl = this[KEY_BASE_URL].orEmpty(),
            apiKey = this[KEY_API_KEY].orEmpty(),
            model = this[KEY_MODEL].orEmpty(),
            wordPrompt = migrateLegacyPrompt(this[KEY_WORD_PROMPT], defaultWordPrompt, legacyWordPrompts),
            sentenceTranslationPrompt = this[KEY_SENTENCE_TRANSLATION_PROMPT] ?: defaultSentenceTranslationPrompt,
            sentencePrompt = this[KEY_SENTENCE_PROMPT] ?: defaultSentencePrompt,
        )

    /** 仅在仍是历史默认文案时替换成当前默认值，保留用户自定义内容。 */
    private fun migrateLegacyPrompt(
        storedPrompt: String?,
        defaultPrompt: String,
        legacyPrompts: Set<String>,
    ): String = when {
        storedPrompt == null -> defaultPrompt
        storedPrompt in legacyPrompts -> defaultPrompt
        else -> storedPrompt
    }

    companion object {
        const val DataStoreName = "advanced-ai-settings"

        private val LEGACY_WORD_PROMPTS = setOf(
            "请用简洁中文分析所选词在句中的作用。只输出纯文本，不要 Markdown、星号、编号或引号。固定三行：词性：... 作用：... 补充：...",
            "Explain the selected word's role inside the sentence in concise Chinese. Output plain Chinese text only with exactly three lines: 词性：... 作用：... 补充：... Do not use Markdown, bullets, numbering, or quotes.",
        )

        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_BASE_URL = stringPreferencesKey("baseUrl")
        private val KEY_API_KEY = stringPreferencesKey("apiKey")
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_WORD_PROMPT = stringPreferencesKey("wordPrompt")
        private val KEY_SENTENCE_TRANSLATION_PROMPT = stringPreferencesKey("sentenceTranslationPrompt")
        private val KEY_SENTENCE_PROMPT = stringPreferencesKey("sentencePrompt")
    }
}
