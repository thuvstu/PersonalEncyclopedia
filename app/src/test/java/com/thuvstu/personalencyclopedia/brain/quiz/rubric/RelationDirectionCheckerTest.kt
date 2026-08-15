package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 関係の反転検出テスト(新採点システム.txt「関係の反転」)。
 * A→B と B→A、AよりBが大きい と BよりAが大きい、AがBの原因 と BがAの原因 を区別する。
 */
class RelationDirectionCheckerTest {

    @Test
    fun `comparison extraction picks base and greater`() {
        val tuples = RelationDirectionChecker.extract("日本より中国が大きい")
        assertEquals(1, tuples.size)
        assertEquals("日本", tuples[0].termA)
        assertEquals("中国", tuples[0].termB)
        assertEquals("comparison:大きい", tuples[0].relation)
    }

    @Test
    fun `inverse comparison is reversed`() {
        val c = RelationDirectionChecker.compare(
            userText = "日本より中国が大きい",
            expectedText = "中国より日本が大きい"
        )
        assertFalse(c.matched)
        assertTrue("比較の向き反転は reversed", c.reversed)
    }

    @Test
    fun `same comparison matches`() {
        val c = RelationDirectionChecker.compare(
            userText = "日本より中国が大きい",
            expectedText = "日本より中国が大きい"
        )
        assertTrue(c.matched)
        assertFalse(c.reversed)
    }

    @Test
    fun `sequential arrow reversal is detected`() {
        val c = RelationDirectionChecker.compare(userText = "A→B", expectedText = "B→A")
        assertFalse(c.matched)
        assertTrue(c.reversed)
    }

    @Test
    fun `sequential arrow same direction matches`() {
        val c = RelationDirectionChecker.compare(userText = "A→B", expectedText = "A→B")
        assertTrue(c.matched)
    }

    @Test
    fun `causality reversal is detected`() {
        val c = RelationDirectionChecker.compare(
            userText = "環境汚染が工業化の原因",
            expectedText = "工業化が環境汚染の原因"
        )
        assertFalse(c.matched)
        assertTrue("因果の向き反転は reversed", c.reversed)
    }

    @Test
    fun `causality same direction matches`() {
        val c = RelationDirectionChecker.compare(
            userText = "工業化が環境汚染の原因",
            expectedText = "工業化が環境汚染の原因"
        )
        assertTrue(c.matched)
    }

    @Test
    fun `no structural relation in model answer is neutral`() {
        val c = RelationDirectionChecker.compare(
            userText = "日本はアジアにある。",
            expectedText = "日本は島国である。"
        )
        assertTrue(c.matched)
        assertFalse(c.reversed)
    }
}
