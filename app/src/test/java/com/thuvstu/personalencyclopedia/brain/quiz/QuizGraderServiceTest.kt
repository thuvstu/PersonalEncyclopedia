package com.thuvstu.personalencyclopedia.brain.quiz

import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.brain.ai.OllamaClient
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.FakeEmbeddingProvider
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.FakeJudgerProvider
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.RubricGrader
import com.thuvstu.personalencyclopedia.db.dao.EraMasterDao
import com.thuvstu.personalencyclopedia.db.entity.EraMasterEntity
import com.thuvstu.personalencyclopedia.db.entity.QuizBankEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ★最適化R6: 採点共通サービス(QuizGraderService)のパイプライン検証。
 * 多段採点 → ルーブリック採点(試作) → 意味的採点(API未設定でスキップ) → スコア計算。
 * サーバー(QuizRoutes)とアプリ(QuizRepository)の両方が同じ結果になることを保証する。
 */
class QuizGraderServiceTest {

    private val eras = listOf(
        EraMasterEntity("慶長", 1596, 1615, 5),
        EraMasterEntity("令和", 2019, null, 45)
    )
    private val fakeDao = object : EraMasterDao {
        override suspend fun getAll(): List<EraMasterEntity> = eras
        override suspend fun getByName(name: String): EraMasterEntity? =
            eras.firstOrNull { it.name == name }
    }

    private fun quiz(
        type: String = "qa",
        answer: String,
        context: String = "{}"
    ): QuizBankEntity = QuizBankEntity(
        id = "q1",
        sourceEntryId = "e1",
        quizType = type,
        question = "「プレート境界」の定義を述べよ。",
        answer = answer,
        gradingContextJson = context,
        generationMethod = "rule_based"
    )

    private lateinit var service: QuizGraderService

    @Before
    fun setup() {
        val multiStage = MultiStageGrader(EraConverter(fakeDao))
        service = QuizGraderService(
            multiStage,
            RubricGrader(
                multiStage,
                FakeEmbeddingProvider(),
                FakeJudgerProvider(availableFlag = false)   // judge無効→ヒューリスティックフォールバック
            ),
            SemanticGrader(GeminiClient(OllamaClient()))    // API未設定→意味的採点はスキップ
        )
    }

    @Test
    fun `exact match is correct with speed bonus`() = runBlocking {
        // mcqはルーブリック対象外のため、純粋に多段採点+スコア計算を検証できる
        val r = service.grade(
            quiz(type = "mcq", answer = "日本はプレート境界に位置する"),
            userAnswer = "日本はプレート境界に位置する",
            answeredWithinMs = 5_000
        )
        assertEquals(true, r.isCorrect)
        assertEquals("exact", r.method)
        // base 1.0 + speed(1-0.5)*0.5 = 0.25
        assertEquals(1.25f, r.score, 0.001f)
        assertTrue("mcqはルーブリック対象外", !r.rubricUsed)
    }

    @Test
    fun `hint penalty reduces score`() = runBlocking {
        val r = service.grade(
            quiz(answer = "日本はプレート境界に位置する"),
            userAnswer = "日本はプレート境界に位置する",
            hintsRevealed = 2,
            hintPenalty = 0.3f
        )
        assertEquals(true, r.isCorrect)
        assertEquals(1.0f - 0.3f * 2, r.score, 0.001f)
    }

    @Test
    fun `wrong answer scores minus one`() = runBlocking {
        val r = service.grade(quiz(answer = "プレートの境界"), userAnswer = "わからない")
        assertEquals(false, r.isCorrect)
        assertEquals(-1.0f, r.score, 0.001f)
    }

    @Test
    fun `unlearned skip is recorded as null correctness and zero score`() = runBlocking {
        val r = service.grade(quiz(answer = "プレートの境界"), userAnswer = "__UNLEARNED__")
        assertNull(r.isCorrect)
        assertEquals(0f, r.score, 0.001f)
    }

    @Test
    fun `rubric upgrades incorrect exact answer to correct when keyword present`() = runBlocking {
        val context = """{"rubric":[{"kind":"keyword","label":"必須語","expected":"プレート境界","weight":1.0}]}"""
        val r = service.grade(
            quiz(answer = "プレート同士がぶつかる場所", context = context),
            userAnswer = "プレート境界が答えだと思います"
        )
        assertTrue("ルーブリック判定で正解に昇格", r.isCorrect == true)
        assertEquals("rubric", r.method)
        assertTrue(r.rubricUsed)
        assertTrue(!r.rubricRationale.isNullOrBlank())
    }

    @Test
    fun `mcq is not rubric graded`() = runBlocking {
        val context = """{"rubric":[{"kind":"keyword","label":"必須語","expected":"プレート境界","weight":1.0}]}"""
        val r = service.grade(
            quiz(type = "mcq", answer = "プレート境界", context = context),
            userAnswer = "プレート境界"
        )
        assertEquals(true, r.isCorrect)
        assertTrue(!r.rubricUsed)
    }

    @Test
    fun `semantic upgrade is skipped when api not configured`() = runBlocking {
        // 不正解のまま（意味的採点はAPI未設定でnull→昇格なし）
        val r = service.grade(quiz(answer = "プレート同士がぶつかる場所"), userAnswer = "あいうえおかきくけこ")
        assertEquals(false, r.isCorrect)
    }
}
