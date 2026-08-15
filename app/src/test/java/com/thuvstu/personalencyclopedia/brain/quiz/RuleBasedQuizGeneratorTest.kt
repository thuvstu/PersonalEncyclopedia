package com.thuvstu.personalencyclopedia.brain.quiz

import com.thuvstu.personalencyclopedia.brain.quiz.rubric.RubricKind
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.RubricParser
import com.thuvstu.personalencyclopedia.db.entity.EntryDefinitionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ★最適化R5: 生成器が書き出す gradingContextJson(ルーブリック採点コンテキスト)の検証。
 * 生成されたクイズがルーブリック採点で評価可能な状態(契約に適合)であることを保証する。
 */
class RuleBasedQuizGeneratorTest {

    private val def = EntryDefinitionEntity(
        entryId = "e1",
        term = "プレート境界",
        definition = "プレート境界とはプレート同士がぶつかる場所",
        field = "地学"
    )

    @Test
    fun `QA quiz embeds keyword and concept rubric`() {
        val q = RuleBasedQuizGenerator.generateQaFromDefinition(def, null)
        val bundle = RubricParser.parse(q.gradingContextJson, q.answer)
        assertEquals("modelAnswersには定義を設定", listOf(def.definition), bundle.modelAnswers)
        assertTrue(bundle.items.any { it.kind == RubricKind.KEYWORD && it.expected == "プレート境界" })
        assertTrue(bundle.items.any { it.kind == RubricKind.CONCEPT && it.expected == def.definition })
        assertEquals(2, bundle.items.size)
    }

    @Test
    fun `reverse QA embeds keyword rubric with term as model answer`() {
        val q = RuleBasedQuizGenerator.generateReverseQa(def, null)
        val bundle = RubricParser.parse(q.gradingContextJson, q.answer)
        assertEquals(listOf("プレート境界"), bundle.modelAnswers)
        assertTrue(bundle.items.any { it.kind == RubricKind.KEYWORD && it.expected == "プレート境界" })
    }

    @Test
    fun `fill blank embeds keyword rubric`() {
        val q = RuleBasedQuizGenerator.generateFillBlank(def, null) ?: error("生成失敗")
        val bundle = RubricParser.parse(q.gradingContextJson, q.answer)
        assertEquals(listOf("プレート境界"), bundle.modelAnswers)
        assertTrue(bundle.items.all { it.kind == RubricKind.KEYWORD })
    }

    @Test
    fun `MCQ keeps default empty context and 4 choices`() {
        val distractors = listOf(
            EntryDefinitionEntity("d1", "断層", "断層の定義", "地学"),
            EntryDefinitionEntity("d2", "火山", "火山の定義", "地学"),
            EntryDefinitionEntity("d3", "地震", "地震の定義", "地学")
        )
        val q = RuleBasedQuizGenerator.generateMcq(def, distractors, null)
        assertEquals("{}", q.gradingContextJson)
        val choices = q.choicesJson.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().trim('"') }
        assertEquals(4, choices.size)
        assertTrue(choices.contains(def.term))
    }
}
