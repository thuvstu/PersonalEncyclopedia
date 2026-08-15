package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import com.thuvstu.personalencyclopedia.brain.quiz.rubric.provider.JudgeOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RubricJudgeTest {

    private fun evidenceOf(features: List<RubricItemFeature>): RubricEvidence =
        RubricConfidence.compute(features, "fake")

    private fun kwHit() = RubricItemFeature(
        RubricItem("k1", RubricKind.KEYWORD, "必須語", "プレート境界"),
        1.0f, 0.95f, listOf(Signal.KeywordHit("プレート境界"))
    )

    private fun kwMissing() = RubricItemFeature(
        RubricItem("k1", RubricKind.KEYWORD, "必須語", "プレート境界"),
        0f, 0.90f, listOf(Signal.KeywordMissing("プレート境界"))
    )

    private val bundle = RubricParser.RubricBundle(emptyList(), listOf("模範"))

    @Test
    fun `heuristic accepts when score is high`() {
        val judge = RubricJudge(FakeJudgerProvider(availableFlag = false))
        val out = runTest(judge, kwHit())
        assertEquals("heuristic", out.source)
        assertTrue(out.isCorrect)
        assertTrue(out.score >= 0.6f)
    }

    @Test
    fun `heuristic rejects when score is low`() {
        val judge = RubricJudge(FakeJudgerProvider(availableFlag = false))
        val out = runTest(judge, kwMissing())
        assertEquals("heuristic", out.source)
        assertFalse(out.isCorrect)
    }

    @Test
    fun `heuristic rationale mentions missing keyword`() {
        val judge = RubricJudge(FakeJudgerProvider(availableFlag = false))
        val out = runTest(judge, kwMissing())
        assertTrue(out.rationale.contains("プレート境界"))
        assertTrue(out.rationale.contains("欠落"))
    }

    @Test
    fun `llm judge is used when available`() {
        val fake = FakeJudgerProvider(availableFlag = true, output = JudgeOutput(true, 0.9f, "LLMが正解と判定", 0.85f))
        val judge = RubricJudge(fake)
        val out = runTest(judge, kwHit())
        assertEquals("llm", out.source)
        assertTrue(out.isCorrect)
        assertNotNull("プロンプトにevidence JSONが埋め込まれる", fake.lastPrompt?.contains("\"rubric\""))
    }

    private fun runTest(judge: RubricJudge, vararg features: RubricItemFeature) =
        kotlinx.coroutines.runBlocking {
            judge.judge("設問", "回答", "模範", evidenceOf(features.toList()), bundle)
        }
}
