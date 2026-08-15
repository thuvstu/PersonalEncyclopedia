package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import com.thuvstu.personalencyclopedia.brain.quiz.EraConverter
import com.thuvstu.personalencyclopedia.brain.quiz.MultiStageGrader
import com.thuvstu.personalencyclopedia.db.dao.EraMasterDao
import com.thuvstu.personalencyclopedia.db.entity.EraMasterEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ルーブリック採点のエンドツーエンドテスト。
 * judgeが利用できない場合(heuristicフォールバック)の正誤判定を検証する。
 */
class RubricGraderTest {

    private val eras = listOf(
        EraMasterEntity("慶長", 1596, 1615, 5),
        EraMasterEntity("令和", 2019, null, 45)
    )
    private val fakeDao = object : EraMasterDao {
        override suspend fun getAll(): List<EraMasterEntity> = eras
        override suspend fun getByName(name: String): EraMasterEntity? =
            eras.firstOrNull { it.name == name }
    }

    private lateinit var grader: RubricGrader

    @Before
    fun setup() {
        grader = RubricGrader(
            MultiStageGrader(EraConverter(fakeDao)),
            FakeEmbeddingProvider(),
            FakeJudgerProvider(availableFlag = false)
        )
    }

    @Test
    fun `applicable requires essay style and length`() {
        assertTrue(grader.applicable("qa", "日本はプレート境界に位置する"))
        assertTrue(grader.applicable("essay", "十分な長さの回答です。"))
        assertFalse("mcqは対象外", grader.applicable("mcq", "日本はプレート境界に位置する"))
        assertFalse("未習は対象外", grader.applicable("qa", "__UNLEARNED__"))
        assertFalse("短文は対象外", grader.applicable("qa", "はい"))
    }

    @Test
    fun `keyword matching answer is correct`() = runBlocking {
        val json = """{"rubric":[{"kind":"keyword","label":"必須語","expected":"プレート境界","weight":1.0}]}"""
        val r = grader.grade(
            question = "日本はどこに位置する？",
            userAnswer = "日本はプレート境界に位置する",
            correctAnswer = "日本はプレート境界に位置する",
            gradingContextJson = json
        )
        assertTrue(r.isCorrect)
        assertEquals("rubric", r.method)
        assertEquals("heuristic", r.judgeSource)
        assertTrue(r.rationale.isNotBlank())
    }

    @Test
    fun `polarity reversed answer is incorrect`() = runBlocking {
        val json = """{"rubric":[{"kind":"polarity","label":"肯定/否定","expected":"POSITIVE","weight":1.0}]}"""
        val r = grader.grade(
            question = "日本はプレート境界に位置する？",
            userAnswer = "日本はプレート境界に位置しない。",
            correctAnswer = "日本はプレート境界に位置する。",
            gradingContextJson = json
        )
        assertFalse("極性反転は不正解", r.isCorrect)
        assertTrue("矛盾がevidenceに記録される", r.evidence.deferToLlm)
        assertTrue(r.evidence.rubricItems.first().signals.any { it is Signal.ContradictionDetected })
    }

    @Test
    fun `numeric unit equivalence is correct`() = runBlocking {
        val json = """{"rubric":[{"kind":"numeric_unit","label":"単位変換","expected":"72 km/h","weight":1.0}]}"""
        val r = grader.grade(
            question = "72km/hは何m/s？",
            userAnswer = "20 m/s",
            correctAnswer = "72 km/h",
            gradingContextJson = json
        )
        assertTrue(r.isCorrect)
    }

    @Test
    fun `missing rubric falls back to auto decomposition`() = runBlocking {
        val r = grader.grade(
            question = "日本はどこに位置する？",
            userAnswer = "日本はプレート境界に位置する",
            correctAnswer = "日本はプレート境界に位置する",
            gradingContextJson = null
        )
        assertTrue(r.evidence.rubricItems.isNotEmpty())
        assertTrue(r.rationale.isNotBlank())
    }
}
