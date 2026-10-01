package moe.antimony.hoshi.features.translation

/**
 * 阅读器翻译源。`Ai` 走高级 AI（OpenAI 兼容）后端；其余为免 key 的 web 翻译接口，
 * 行为对齐 LunaTranslator 的同名免费翻译器（目标语言固定简体中文，源语言自动检测）。
 */
internal enum class TranslationProvider(val id: String) {
    Ai("ai"),
    Microsoft("microsoft"),
    Bing("bing"),
    Google("google"),
    ModernMt("modernmt"),
    QqTransmart("qqTransSmart"),
    Ali("ali"),
    Youdao("youdao"),
    Caiyun("caiyun"),
    TranslateCom("translateCom"),
    Yandex("yandex"),
    QqImt("qqimt"),
    Huoshan("huoshan"),
    Papago("papago"),
    ;

    val isFreeWeb: Boolean get() = this != Ai

    companion object {
        const val DEFAULT_ID = "ai"

        fun fromId(id: String?): TranslationProvider =
            entries.firstOrNull { it.id == id } ?: Ai
    }
}
