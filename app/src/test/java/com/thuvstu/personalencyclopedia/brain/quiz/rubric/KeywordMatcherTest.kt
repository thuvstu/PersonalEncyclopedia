package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordMatcherTest {

    @Test
    fun `exact substring hits`() {
        assertTrue(KeywordMatcher.contains("日本はプレート境界に位置する", "プレート境界"))
        assertEquals(1.0f, KeywordMatcher.match("日本はプレート境界に位置する", "プレート境界"), 0.001f)
    }

    @Test
    fun `normalized hit ignores fullwidth punctuation`() {
        assertTrue(KeywordMatcher.contains("日本はプレート境界に位置する。", "プレート境界"))
        assertEquals(1.0f, KeywordMatcher.match("プレート境界。", "プレート境界"), 0.001f)
    }

    @Test
    fun `near miss accepts small typo`() {
        val score = KeywordMatcher.match("プレートの境界", "プレート境界")
        assertTrue("類似度 $score は0.85以上であるべき", score >= 0.85f)
    }

    @Test
    fun `unrelated text scores zero`() {
        assertEquals(0.0f, KeywordMatcher.match("これは関係ない内容だ", "プレート境界"), 0.001f)
        assertFalse(KeywordMatcher.contains("これは関係ない内容だ", "プレート境界"))
    }

    @Test
    fun `blank keyword never matches`() {
        assertEquals(0.0f, KeywordMatcher.match("なにか", ""), 0.001f)
        assertFalse(KeywordMatcher.contains("なにか", ""))
    }

    @Test
    fun `missing keywords are reported`() {
        val missing = KeywordMatcher.missingKeywords(
            "日本はプレート境界に位置する",
            listOf("プレート境界", "沈み込み", "地震")
        )
        assertEquals(listOf("沈み込み", "地震"), missing)
    }

    @Test
    fun `all contained requires every keyword`() {
        assertTrue(KeywordMatcher.allContained("プレート境界と沈み込み", listOf("プレート境界", "沈み込み")))
        assertFalse(KeywordMatcher.allContained("プレート境界のみ", listOf("プレート境界", "沈み込み")))
    }
}
