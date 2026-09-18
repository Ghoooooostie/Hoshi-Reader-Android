package moe.antimony.hoshi.features.advancedai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedAiTranslationGuardTest {
    private val source = "「何をしてるの？」と彼は聞いた。私は黙っていた。"

    @Test
    fun acceptsChineseTranslationOfJapaneseSource() {
        assertTrue(
            isChineseTargetTranslation(
                sourceText = source,
                translation = "他在问：“你在做什么？”我一直没有说话。",
            ),
        )
    }

    @Test
    fun rejectsTranslationEqualToSource() {
        assertFalse(
            isChineseTargetTranslation(
                sourceText = source,
                translation = source,
            ),
        )
    }

    @Test
    fun rejectsTranslationThatKeepsJapaneseSentence() {
        assertFalse(
            isChineseTargetTranslation(
                sourceText = source,
                translation = "他在问你在做什么。私は黙っていた。",
            ),
        )
    }

    @Test
    fun rejectsBlankTranslation() {
        assertFalse(
            isChineseTargetTranslation(
                sourceText = source,
                translation = "   ",
            ),
        )
    }

    @Test
    fun acceptsChineseSourceTranslatedIntoChinese() {
        assertTrue(
            isChineseTargetTranslation(
                sourceText = "他在屋子里走来走去。",
                translation = "他在房间里来回踱步。",
            ),
        )
    }
}
