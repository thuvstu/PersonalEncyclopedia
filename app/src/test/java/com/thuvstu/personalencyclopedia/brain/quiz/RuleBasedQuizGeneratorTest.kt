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
    fun `cloze uses two blanks and sequence answer`() {
        val q = RuleBasedQuizGenerator.generateCloze(def, null) ?: error("生成失敗")
        assertEquals("cloze", q.quizType)
        assertTrue(q.question.contains("＿＿＿"))
        assertTrue("空欄が2つ以上", QuizFormatSupport.blankCount(q.question) >= 2)
        val parts = QuizFormatSupport.splitSequence(q.answer)
        assertTrue(parts.contains("プレート境界"))
        assertEquals(QuizFormatSupport.blankCount(q.question), parts.size)
    }

    @Test
    fun `sort orders by reading and keeps greater-than answer`() {
        val members = listOf(
            def,
            EntryDefinitionEntity(entryId = "d1", term = "断層", definition = "断層の定義", field = "地学", reading = "だんそう"),
            EntryDefinitionEntity(entryId = "d2", term = "火山", definition = "火山の定義", field = "地学", reading = "かざん")
        )
        val q = RuleBasedQuizGenerator.generateSort(members, null) ?: error("生成失敗")
        assertEquals("sort", q.quizType)
        assertEquals("火山>断層>プレート境界", q.answer)
        assertTrue(q.choicesJson.contains("断層"))
    }

    @Test
    fun `tf true statement uses field`() {
        val q = RuleBasedQuizGenerator.generateTf(def, emptyList(), null)
        assertEquals("tf", q.quizType)
        assertEquals("正しい", q.answer)
        assertTrue(q.question.contains("プレート境界"))
    }

    @Test
    fun `multi picks same-field terms and other-field distractors`() {
        val same = listOf(
            EntryDefinitionEntity(entryId = "d1", term = "断層", definition = "断層の定義", field = "地学"),
        )
        val others = listOf(
            EntryDefinitionEntity(entryId = "o1", term = "乗法公式", definition = "展開", field = "数学I"),
            EntryDefinitionEntity(entryId = "o2", term = "正弦定理", definition = "辺と角", field = "数学I"),
        )
        val q = RuleBasedQuizGenerator.generateMulti(def, same, others, null) ?: error("生成失敗")
        assertEquals("multi", q.quizType)
        val parts = QuizFormatSupport.splitSequence(q.answer)
        assertTrue(parts.contains("プレート境界"))
        assertTrue(parts.contains("断層"))
        assertEquals(2, parts.size)
    }

    @Test
    fun `match joins left-pipe-right choices`() {
        val members = listOf(
            def,
            EntryDefinitionEntity(entryId = "d1", term = "断層", definition = "ずれ面", field = "地学"),
            EntryDefinitionEntity(entryId = "d2", term = "火山", definition = "噴火口", field = "地学"),
        )
        val q = RuleBasedQuizGenerator.generateMatch(members, null) ?: error("生成失敗")
        assertEquals("match", q.quizType)
        assertTrue(q.choicesJson.contains("プレート境界|"))
        assertTrue(q.answer.contains("プレート境界="))
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
