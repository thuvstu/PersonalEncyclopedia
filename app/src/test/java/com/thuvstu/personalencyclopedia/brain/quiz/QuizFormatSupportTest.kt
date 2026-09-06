package com.thuvstu.personalencyclopedia.brain.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizFormatSupportTest {

    private val supported = QuizFormats.IDS

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
    fun `resolveEnabledTypes upgrades old full sets including 5`() {
        assertEquals(supported, QuizFormatSupport.resolveEnabledTypes(null, supported))
        assertEquals(supported, QuizFormatSupport.resolveEnabledTypes("qa,mcq,fill_blank", supported))
        assertEquals(supported, QuizFormatSupport.resolveEnabledTypes("qa, fill_blank, mcq, sort", supported))
        assertEquals(supported, QuizFormatSupport.resolveEnabledTypes("qa,mcq,fill_blank,sort,cloze", supported))
        assertEquals(setOf("mcq"), QuizFormatSupport.resolveEnabledTypes("mcq", supported))
        assertEquals(setOf("qa", "cloze"), QuizFormatSupport.resolveEnabledTypes("qa,cloze,custom", supported))
    }

    @Test
    fun `parseMatchPairs splits on first pipe`() {
        val pairs = QuizFormatSupport.parseMatchPairs(
            listOf("正弦定理|a/sin A=2R", " 楕円 | eは1未満 ", "壊れた", "|右", "左|")
        )
        assertEquals(listOf("正弦定理" to "a/sin A=2R", "楕円" to "eは1未満"), pairs)
    }

    @Test
    fun `splitMatchSequence keeps inequality in right hand`() {
        val parts = QuizFormatSupport.splitMatchSequence("楕円=e>1なし>放物線=e=1")
        assertEquals(listOf("楕円=e>1なし", "放物線=e=1"), parts)
    }

    @Test
    fun `canonicalizeTf folds synonyms`() {
        assertEquals("正しい", QuizFormatSupport.canonicalizeTf("true"))
        assertEquals("正しい", QuizFormatSupport.canonicalizeTf("○"))
        assertEquals("誤り", QuizFormatSupport.canonicalizeTf("false"))
        assertEquals("誤り", QuizFormatSupport.canonicalizeTf("×"))
        assertNull(QuizFormatSupport.canonicalizeTf("たぶん"))
    }

    @Test
    fun `catalog ids are the supported set`() {
        assertEquals(
            setOf("qa", "mcq", "fill_blank", "sort", "cloze", "tf", "multi", "match"),
            QuizFormats.IDS
        )
    }
}
