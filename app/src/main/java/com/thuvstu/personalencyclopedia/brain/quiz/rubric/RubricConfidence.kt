package com.thuvstu.personalencyclopedia.brain.quiz.rubric

/**
 * confidence 集計と LLM judge への defer 判定(新採点システム.txt「Confidence」)。
 *
 * - overallConfidence = rubric 重み付きの item confidence 平均
 * - deferToLlm = 矛盾シグナル / 判定不能シグナル / overallConfidence < 0.7
 *   (低confidenceのまま自動判定すると誤答を量産するため、確実性が低いときは LLM に委譲する)
 */
object RubricConfidence {

    const val DEFER_THRESHOLD = 0.7f

    fun compute(features: List<RubricItemFeature>, providerName: String): RubricEvidence {
        val totalWeight = features.sumOf { it.item.weight.toDouble() }.toFloat().coerceAtLeast(0.001f)
        val overallConfidence =
            features.sumOf { (it.item.weight * it.confidence).toDouble() }.toFloat() / totalWeight
        val anyContradiction = features.any { f -> f.signals.any { it is Signal.ContradictionDetected } }
        val anyUndeterminable = features.any { f -> f.signals.any { it == Signal.Undeterminable } }
        val deferToLlm = anyContradiction || anyUndeterminable || overallConfidence < DEFER_THRESHOLD
        return RubricEvidence(
            rubricItems = features,
            overallConfidence = overallConfidence.coerceIn(0f, 1f),
            deferToLlm = deferToLlm,
            providerName = providerName,
            entailmentUsed = false,
            crossEncoderUsed = false
        )
    }
}
