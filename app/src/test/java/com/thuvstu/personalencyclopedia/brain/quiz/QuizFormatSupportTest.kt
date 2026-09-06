package com.thuvstu.personalencyclopedia.brain.quiz

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizFormatSupportTest {

    private val supported = setOf("qa", "mcq", "fill_blank", "sort", "cloze")

    @Test
    fun `blankCount counts fullwidth markers`() {
        assertEquals(0, QuizFormatSupport.blankCount("穴なし"))
        assertEquals(1, QuizFormatSupport.blankCount("空欄を埋めよ:\n「＿＿＿」"))
        assertEquals(2, QuizFormatSupport.blankCount("a/sin A = ＿＿＿ = ＿＿＿"))
    }

    @Test
    fun `splitSequence accepts several separators`() {
        assertEquals(listOf("平均", "分散", "標準偏差"), QuizFormatSupport.splitSequence("平均>分散>標準偏差"))
        assertEquals(listOf("平均", "分散", "標準偏差"), QuizFormatSupport.splitSequence("平均 ＞ 分散、標準偏差"))
        assertEquals(listOf("f'g", "fg'"), QuizFormatSupport.splitSequence("f'g>fg'"))
    }

    @Test
    fun `joinSequence uses ascii greater-than`() {
        assertEquals("a>b>c", QuizFormatSupport.joinSequence(listOf("a", "b", "c")))
    }

    @Test
    fun `resolveEnabledTypes upgrades old full sets`() {
        assertEquals(supported, QuizFormatSupport.resolveEnabledTypes(null, supported))
        assertEquals(supported, QuizFormatSupport.resolveEnabledTypes("qa,mcq,fill_blank", supported))
        assertEquals(supported, QuizFormatSupport.resolveEnabledTypes("qa, fill_blank, mcq, sort", supported))
        assertEquals(setOf("mcq"), QuizFormatSupport.resolveEnabledTypes("mcq", supported))
        assertEquals(setOf("qa", "cloze"), QuizFormatSupport.resolveEnabledTypes("qa,cloze,custom", supported))
    }
}
