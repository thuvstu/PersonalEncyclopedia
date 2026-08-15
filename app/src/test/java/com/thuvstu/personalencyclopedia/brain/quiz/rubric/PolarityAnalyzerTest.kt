package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import com.thuvstu.personalencyclopedia.brain.quiz.rubric.PolarityAnalyzer.PolarityLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 否定・極性解析の核心ケース(新採点システム.txt「特に重要：否定・極性」)。
 * 「日本はプレート境界に位置する。」vs「位置しない。」を区別できること。
 */
class PolarityAnalyzerTest {

    @Test
    fun `positive clause is positive`() {
        assertEquals(PolarityLabel.POSITIVE, PolarityAnalyzer.polarityOf("日本はプレート境界に位置する。"))
    }

    @Test
    fun `negative clause is negative`() {
        assertEquals(PolarityLabel.NEGATIVE, PolarityAnalyzer.polarityOf("日本はプレート境界に位置しない。"))
    }

    @Test
    fun `scope reconstructs affirmative form`() {
        val result = PolarityAnalyzer.analyze("日本はプレート境界に位置しない。").first()
        assertEquals("日本はプレート境界に位置する", result.scopeText)
        assertEquals("ない", result.pattern)
    }

    @Test
    fun `否定 vs 肯定 is detected as reversed`() {
        val c = PolarityAnalyzer.compare(
            userText = "日本はプレート境界に位置しない。",
            expectedText = "日本はプレート境界に位置する。"
        )
        assertFalse(c.matched)
        assertTrue("極性反転は reversed になるべき", c.reversed)
    }

    @Test
    fun `肯定 vs 肯定 matches`() {
        val c = PolarityAnalyzer.compare(
            userText = "日本はプレート境界に位置する。",
            expectedText = "日本はプレート境界に位置する。"
        )
        assertTrue(c.matched)
        assertFalse(c.reversed)
    }

    @Test
    fun `なければならない is not a negation`() {
        assertEquals(PolarityLabel.POSITIVE, PolarityAnalyzer.polarityOf("環境対策をしなければならない。"))
    }

    @Test
    fun `ではない is negation`() {
        assertEquals(PolarityLabel.NEGATIVE, PolarityAnalyzer.polarityOf("地球は平らではない。"))
    }

    @Test
    fun `contrast extraction captures alternative`() {
        val result = PolarityAnalyzer.analyze("カトリックではなくプロテスタント").first()
        assertTrue(result.negated)
        assertEquals("プロテスタント", result.alternative)
    }

    @Test
    fun `mixed polarity text is overall negative`() {
        // 肯定節と否定節が混在 → 否定が1つでもあれば NEGATIVE(スコープは節単位で判定)
        assertEquals(
            PolarityLabel.NEGATIVE,
            PolarityAnalyzer.polarityOf("日本はプレート境界に位置する。ただし現在は位置しない。")
        )
    }

    @Test
    fun `negation is per-clause not per-word`() {
        val results = PolarityAnalyzer.analyze("日本はプレート境界に位置する。これは基本だ。")
        assertTrue("否定を含まない文では空", results.isEmpty())
        assertNotNull("「しない」節では検出される", PolarityAnalyzer.analyze("日本はプレート境界に位置しない").firstOrNull())
    }

    @Test
    fun `ず negation detected`() {
        assertEquals(PolarityLabel.NEGATIVE, PolarityAnalyzer.polarityOf("混ざり合わず別々の層をなす。"))
    }
}
