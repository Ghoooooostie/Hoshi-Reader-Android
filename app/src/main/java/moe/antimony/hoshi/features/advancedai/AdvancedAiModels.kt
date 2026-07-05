package moe.antimony.hoshi.features.advancedai

/** 高级 AI 的持久化设置。 */
internal data class AdvancedAiSettings(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val wordPrompt: String,
    val sentenceTranslationPrompt: String,
    val pageParagraphTranslationPrompt: String,
    val sentencePrompt: String,
)

/** 高级 AI 缺失的必要配置。 */
internal enum class AdvancedAiMissingField {
    BaseUrl,
    ApiKey,
    Model,
    WordPrompt,
    SentenceTranslationPrompt,
    PageParagraphTranslationPrompt,
    SentencePrompt,
}

/** 高级 AI 当前是否可发起请求。 */
internal sealed interface AdvancedAiAvailability {
    data object Disabled : AdvancedAiAvailability
    data class MissingConfiguration(val field: AdvancedAiMissingField) : AdvancedAiAvailability
    data class Ready(val settings: AdvancedAiSettings) : AdvancedAiAvailability
}

/** 判断词语分析是否可用。 */
internal fun AdvancedAiSettings.wordAvailability(): AdvancedAiAvailability =
    availability(prompt = wordPrompt, missingField = AdvancedAiMissingField.WordPrompt)

/** 判断整句翻译是否可用。 */
internal fun AdvancedAiSettings.sentenceTranslationAvailability(): AdvancedAiAvailability =
    availability(
        prompt = sentenceTranslationPrompt,
        missingField = AdvancedAiMissingField.SentenceTranslationPrompt,
    )

/** 判断段落翻译是否可用。 */
internal fun AdvancedAiSettings.pageParagraphTranslationAvailability(): AdvancedAiAvailability =
    availability(
        prompt = pageParagraphTranslationPrompt,
        missingField = AdvancedAiMissingField.PageParagraphTranslationPrompt,
    )

/** 判断长难句分析是否可用。 */
internal fun AdvancedAiSettings.sentenceAvailability(): AdvancedAiAvailability =
    availability(prompt = sentencePrompt, missingField = AdvancedAiMissingField.SentencePrompt)

/** 统一计算当前配置的可用性。 */
private fun AdvancedAiSettings.availability(
    prompt: String,
    missingField: AdvancedAiMissingField,
): AdvancedAiAvailability {
    if (!enabled) return AdvancedAiAvailability.Disabled
    if (baseUrl.isBlank()) return AdvancedAiAvailability.MissingConfiguration(AdvancedAiMissingField.BaseUrl)
    if (apiKey.isBlank()) return AdvancedAiAvailability.MissingConfiguration(AdvancedAiMissingField.ApiKey)
    if (model.isBlank()) return AdvancedAiAvailability.MissingConfiguration(AdvancedAiMissingField.Model)
    if (prompt.isBlank()) return AdvancedAiAvailability.MissingConfiguration(missingField)
    return AdvancedAiAvailability.Ready(copy(baseUrl = baseUrl.trimEnd('/')))
}
