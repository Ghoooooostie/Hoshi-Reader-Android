package moe.antimony.hoshi.features.advancedai

/** 译文里允许的日语假名占比上限，超过即认为模型没有真正译成中文。 */
private const val MAX_SOURCE_KANA_RATIO = 0.15f

/** 判定为回显原句所需的最小连续原文长度。 */
private const val MIN_ECHOED_SOURCE_LENGTH = 8

/** 用来把原文拆成近似句子的标点。 */
private val SOURCE_FRAGMENT_SEPARATORS = Regex("[。．.！!？?…、，,；;：:\\n\\r」』》〉】）)\"“”‘’']+")

/** 判断 AI 返回值是否真的译成了中文，避免把日语原文当成译文写进阅读页。 */
internal fun isChineseTargetTranslation(
    sourceText: String,
    translation: String,
): Boolean {
    val candidate = compactForTranslationComparison(translation)
    if (candidate.isEmpty()) return false
    val source = compactForTranslationComparison(sourceText)
    if (source.isEmpty()) return true
    if (source == candidate) return false
    if (japaneseKanaCount(candidate).toFloat() / candidate.length >= MAX_SOURCE_KANA_RATIO) return false
    return !echoesJapaneseSourceFragment(source, candidate)
}

/** 检查译文是否把含假名的原句整句搬了过来。 */
private fun echoesJapaneseSourceFragment(source: String, candidate: String): Boolean =
    SOURCE_FRAGMENT_SEPARATORS.split(source).any { fragment ->
        val compacted = compactForTranslationComparison(fragment)
        compacted.length >= MIN_ECHOED_SOURCE_LENGTH &&
            japaneseKanaCount(compacted) > 0 &&
            candidate.contains(compacted)
    }

/** 统计日语假名数量，汉字不属于假名，避免中文译文被误判。 */
private fun japaneseKanaCount(text: String): Int =
    text.count(::isJapaneseKana)

private fun isJapaneseKana(char: Char): Boolean =
    char in '\u3040'..'\u309F' || char in '\u30A0'..'\u30FF' || char == '\u30FC'

/** 去掉空白后比较，避免换行和缩进差异影响判断。 */
private fun compactForTranslationComparison(text: String): String =
    text.filter { !it.isWhitespace() }
