package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ルーブリック分解のテスト。gradingContextJson 契約と自動分解(autoDecompose)を検証する。
 */
class RubricParserTest {

    @Test
    fun `null json falls back to auto decomposition`() {
        val bundle = RubricParser.parse(null, "日本はプレート境界に位置する。")
        assertTrue(bundle.items.isNotEmpty())
        assertEquals(listOf("日本はプレート境界に位置する。"), bundle.modelAnswers)
    }

    @Test
    fun `explicit rubric json is parsed`() {
        val json = """
            {
              "rubric": [
                {"kind":"keyword","label":"必須語","expected":"プレート境界","weight":0.5},
                {"kind":"numeric_unit","label":"単位変換","expected":"72 km/h","weight":0.3},
                {"kind":"polarity","expected":"POSITIVE","weight":0.2}
              ],
              "modelAnswers": ["模範解答1", "模範解答2"]
            }
        """.trimIndent()
        val bundle = RubricParser.parse(json, "模範")
        assertEquals(3, bundle.items.size)
        assertEquals(RubricKind.KEYWORD, bundle.items[0].kind)
        assertEquals(0.5f, bundle.items[0].weight, 0.001f)
        assertEquals(RubricKind.NUMERIC_UNIT, bundle.items[1].kind)
        assertEquals(RubricKind.POLARITY, bundle.items[2].kind)
        assertEquals(listOf("模範解答1", "模範解答2"), bundle.modelAnswers)
    }

    @Test
    fun `invalid json falls back to auto decomposition`() {
        val bundle = RubricParser.parse("{ broken json", "模範")
        assertTrue(bundle.items.isNotEmpty())
    }

    @Test
    fun `auto decompose splits clauses into concepts`() {
        val bundle = RubricParser.autoDecompose("日本はプレート境界に位置する。これは地球科学の基本だ。")
        assertEquals(2, bundle.items.size)
        assertEquals(RubricKind.CONCEPT, bundle.items[0].kind)
        assertEquals(0.5f, bundle.items[0].weight, 0.001f)
    }

    @Test
    fun `auto decompose extracts polarity from negated clause`() {
        val bundle = RubricParser.autoDecompose("日本はプレート境界に位置しない。")
        val kinds = bundle.items.map { it.kind }
        assertTrue("POLARITY項目が含まれるべき", kinds.contains(RubricKind.POLARITY))
        assertEquals("NEGATIVE", bundle.items.first { it.kind == RubricKind.POLARITY }.expected)
        // 否定スコープの概念(肯定復元形)も含まれる
        assertTrue(kinds.contains(RubricKind.CONCEPT))
    }

    @Test
    fun `auto decompose extracts numeric unit`() {
        val bundle = RubricParser.autoDecompose("日本の人口は約1億2000万人だ。")
        assertTrue(
            bundle.items.any { it.kind == RubricKind.NUMERIC_UNIT }
        )
    }

    @Test
    fun `map kind accepts japanese labels`() {
        assertEquals(RubricKind.KEYWORD, RubricParser.mapKind("キーワード"))
        assertEquals(RubricKind.NUMERIC_UNIT, RubricParser.mapKind("数値・単位"))
        assertEquals(RubricKind.RELATION, RubricParser.mapKind("因果"))
        assertEquals(RubricKind.POLARITY, RubricParser.mapKind("極性"))
        assertEquals(RubricKind.CONCEPT, RubricParser.mapKind("concept"))
        assertEquals(null, RubricParser.mapKind("unknown"))
    }
}
