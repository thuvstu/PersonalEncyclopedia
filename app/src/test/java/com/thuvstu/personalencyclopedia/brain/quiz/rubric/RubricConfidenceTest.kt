package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RubricConfidenceTest {

    private fun feature(
        kind: RubricKind = RubricKind.KEYWORD,
        score: Float,
        confidence: Float,
        signals: List<Signal> = emptyList(),
        weight: Float = 1.0f
    ) = RubricItemFeature(
        RubricItem("id-$kind-$score-$confidence", kind, "ラベル", "期待", weight),
        score, confidence, signals
    )

    @Test
    fun `high confidence features do not defer`() {
        val features = listOf(
            feature(score = 1.0f, confidence = 0.95f, signals = listOf(Signal.KeywordHit("x")))
        )
        val e = RubricConfidence.compute(features, "fake")
        assertEquals(0.95f, e.overallConfidence, 0.001f)
        assertFalse(e.deferToLlm)
    }

    @Test
    fun `contradiction always defers to llm`() {
        val features = listOf(
            feature(score = 1.0f, confidence = 0.95f, signals = listOf(Signal.ContradictionDetected("反転")))
        )
        val e = RubricConfidence.compute(features, "fake")
        assertTrue("矛盾シグナルがあれば defer すべき", e.deferToLlm)
    }

    @Test
    fun `undeterminable always defers to llm`() {
        val features = listOf(
            feature(score = 0.5f, confidence = 0.20f, signals = listOf(Signal.Undeterminable))
        )
        val e = RubricConfidence.compute(features, "fake")
        assertTrue("判定不能シグナルがあれば defer すべき", e.deferToLlm)
    }

    @Test
    fun `low weighted confidence defers to llm`() {
        // keyword欠落(高confidence)とembeddingのみ(低confidence)が混在 → 加重平均が閾値未満
        val features = listOf(
            feature(score = 0f, confidence = 0.90f, signals = listOf(Signal.KeywordMissing("a")), weight = 1.0f),
            feature(RubricKind.CONCEPT, score = 0.5f, confidence = 0.45f, signals = listOf(Signal.SemanticSimilar(0.6f)), weight = 1.0f)
        )
        val e = RubricConfidence.compute(features, "fake")
        assertEquals(0.675f, e.overallConfidence, 0.001f)
        assertTrue(e.deferToLlm)
    }

    @Test
    fun `weights are respected in average`() {
        val features = listOf(
            feature(score = 1.0f, confidence = 0.95f, signals = listOf(Signal.KeywordHit("a")), weight = 3.0f),
            feature(score = 0f, confidence = 0.50f, signals = emptyList(), weight = 1.0f)
        )
        val e = RubricConfidence.compute(features, "fake")
        assertEquals((3 * 0.95f + 1 * 0.5f) / 4f, e.overallConfidence, 0.001f)
    }
}
