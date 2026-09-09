package com.thuvstu.personalencyclopedia.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ★wt58: 高校シードの純粋関数と、シードデータ自体の整合性（DB不要） */
class InitialDataHighSchoolPureTest {

    @Test
    fun mentionedTitles_excludesSelfContainmentAndShortTitles_respectsCap() {
        val titles = listOf("評論文の読み方", "パラグラフリーディング", "小論文の型", "評論", "要約の技術", "論理的誤謬", "批判的思考")
            .sortedByDescending { it.length }
        val def = "評論は主張と根拠を分けて読む。パラグラフリーディングと同じ。小論文の型へ。要約の技術、論理的誤謬、批判的思考も。"
        val got = InitialDataHighSchool.mentionedTitles("評論文の読み方", def, titles, cap = 3)
        assertEquals(3, got.size)
        assertFalse("自分自身を含まない", got.contains("評論文の読み方"))
        assertFalse("自分に包含されるタイトルは除外", got.contains("評論"))
        assertTrue(got.contains("パラグラフリーディング"))
    }

    @Test
    fun bestTitleForQuiz_prefersLongestMatch_andReturnsNullWhenNone() {
        val titles = listOf("係り結びの法則", "係り結び", "助動詞「べし」").sortedByDescending { it.length }
        assertEquals("係り結びの法則", InitialDataHighSchool.bestTitleForQuiz("係り結びの法則で已然形になるのは？", "こそ", null, titles))
        assertEquals("係り結び", InitialDataHighSchool.bestTitleForQuiz("係り結びとは", "x", "解説", titles))
        assertNull(InitialDataHighSchool.bestTitleForQuiz("無関係", "x", null, titles))
    }

    @Test
    fun seeds_haveUniqueTitles_noSelfConnections_andSentinelExists() {
        val subjects = InitialDataHighSchool.allSubjects
        val allTitles = subjects.flatMap { s -> s.definitions.map { it.term } + s.thoughts.map { it.title } }
        val dup = allTitles.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        assertTrue("重複タイトル: $dup", dup.isEmpty())
        assertTrue(allTitles.contains(InitialDataHighSchool.SENTINEL))
        for (s in subjects) {
            val selfLoops = s.connections.filter { it.a == it.b }
            assertTrue("${s.key} 自己接続: $selfLoops", selfLoops.isEmpty())
            for (q in s.quizzes) {
                if (q.type == "mcq") assertTrue("${s.key} 正答が選択肢にない: ${q.q}", q.choices.contains(q.a))
            }
            for ((sTitle, spokes, _) in s.boardHubs) {
                assertTrue("${s.key} 白板 $sTitle は最大4件", spokes.size <= 4)
            }
        }
        assertTrue(HsStickies.bridges.none { it.a == it.b })
    }

    @Test
    fun stickies_pointToSeededOrKnownCards_andUseValidColors() {
        val known = InitialDataHighSchool.allSubjects.flatMap { s -> s.definitions.map { it.term } }.toSet() + HsStickies.extraLinkTitles
        val colors = setOf("yellow", "pink", "blue", "green")
        val unknown = HsStickies.common.map { it.card }.filter { it !in known }.distinct()
        assertTrue("種付箋の貼り先が未知: $unknown", unknown.isEmpty())
        assertTrue(HsStickies.common.all { it.color in colors })
        assertTrue(HsStickies.common.all { it.text.isNotBlank() && it.text.length <= 400 })
    }
}
