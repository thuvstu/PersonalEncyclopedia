package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import com.thuvstu.personalencyclopedia.brain.ai.cosineSimilarity
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.provider.IEmbeddingProvider

/**
 * feature抽出(新採点システム.txt「Rubric分解 → feature抽出 → 最終LLM judge」の中間層)。
 * 各 RubricItem を判定軸ごとに評価し、RubricItemFeature(score/confidence/signals)を生成する。
 *
 * 判定軸ごとの役割分担(新採点システム.txt):
 * - KEYWORD: 明示的語の一致(決定論・高confidence)
 * - CONCEPT: Embedding類似度の最大値(複数模範解答対応)。Embedding無し時は bigram/キーワードで代替
 * - NUMERIC_UNIT: 数値・単位の決定論的検証(最高confidence)
 * - POLARITY: 否定・極性の反転検出(Embeddingが苦手な軸を独立評価)
 * - RELATION: 関係・因果の向き(A→B vs B→A)
 * - EXPLANATION: 説明量のヒューリスティック(本格的評価はLLM judgeへ)
 */
class RubricFeatureExtractor(
    private val embeddingProvider: IEmbeddingProvider,
    private val numericVerifier: NumericUnitVerifier
) {

    suspend fun extract(userAnswer: String, bundle: RubricParser.RubricBundle): List<RubricItemFeature> {
        return bundle.items.map { item ->
            when (item.kind) {
                RubricKind.KEYWORD -> keywordFeature(userAnswer, item)
                RubricKind.NUMERIC_UNIT -> numericFeature(userAnswer, item)
                RubricKind.POLARITY -> polarityFeature(userAnswer, item)
                RubricKind.RELATION -> relationFeature(userAnswer, item)
                RubricKind.EXPLANATION -> explanationFeature(userAnswer, item)
                RubricKind.CONCEPT -> conceptFeature(userAnswer, item, bundle.modelAnswers)
            }
        }
    }

    private fun keywordFeature(userAnswer: String, item: RubricItem): RubricItemFeature {
        val m = KeywordMatcher.match(userAnswer, item.expected)
        return when {
            m >= 0.85f -> RubricItemFeature(item, m, 0.95f, listOf(Signal.KeywordHit(item.expected)))
            m >= 0.5f -> RubricItemFeature(item, m, 0.70f, listOf(Signal.KeywordNearMiss(item.expected, m)))
            else -> RubricItemFeature(item, 0f, 0.90f, listOf(Signal.KeywordMissing(item.expected)))
        }
    }

    private suspend fun conceptFeature(
        userAnswer: String,
        item: RubricItem,
        modelAnswers: List<String>
    ): RubricItemFeature {
        val targets = (listOf(item.expected) + modelAnswers).distinct()
        if (embeddingProvider.available) {
            val userVec = embeddingProvider.embed(userAnswer) ?: return conceptFallback(userAnswer, item)
            val bestSim = targets.maxOfOrNull { t ->
                val targetVec = embeddingProvider.embed(t) ?: return@maxOfOrNull 0f
                cosineSimilarity(userVec, targetVec)
            } ?: 0f
            val hit = bestSim >= 0.75f
            val signals = mutableListOf<Signal>()
            signals += if (hit) Signal.ConceptDetected(item.expected) else Signal.ConceptMissing(item.expected)
            signals += Signal.SemanticSimilar(bestSim)
            return RubricItemFeature(item, bestSim, if (hit) 0.70f else 0.55f, signals, "embedding")
        }
        return conceptFallback(userAnswer, item)
    }

    /** Embedding無し時の代替判定(confidence低・defer対象) */
    private fun conceptFallback(userAnswer: String, item: RubricItem): RubricItemFeature {
        val overlap = TextNorm.bigramOverlap(userAnswer, item.expected)
        if (overlap >= 0.6f) {
            return RubricItemFeature(item, overlap, 0.55f, listOf(Signal.ConceptDetected(item.expected)), "bigram fallback")
        }
        val kwScore = KeywordMatcher.match(userAnswer, item.expected)
        return if (kwScore >= 0.85f) {
            RubricItemFeature(item, 0.7f, 0.60f, listOf(Signal.ConceptDetected(item.expected)), "keyword fallback")
        } else {
            RubricItemFeature(item, 0f, 0.55f, listOf(Signal.ConceptMissing(item.expected)), "embedding unavailable")
        }
    }

    private suspend fun numericFeature(userAnswer: String, item: RubricItem): RubricItemFeature {
        val r = numericVerifier.verify(userAnswer, item.expected)
        return when (r.matched) {
            true -> RubricItemFeature(
                item, 1.0f, 0.98f,
                listOf(Signal.NumericEquivalent(item.expected, r.userValue.orEmpty(), r.converted.orEmpty())),
                r.detail
            )
            false -> RubricItemFeature(
                item, 0f, 0.98f,
                listOf(Signal.NumericMismatch(item.expected, r.userValue.orEmpty())),
                r.detail
            )
            null -> RubricItemFeature(item, 0.5f, 0.20f, listOf(Signal.Undeterminable), r.detail)
        }
    }

    private fun polarityFeature(userAnswer: String, item: RubricItem): RubricItemFeature {
        val expectedNegated = when (item.expected.trim().uppercase()) {
            "POSITIVE" -> false
            "NEGATIVE" -> true
            else -> PolarityAnalyzer.polarityOf(item.expected) == PolarityAnalyzer.PolarityLabel.NEGATIVE
        }
        val userNegated = PolarityAnalyzer.polarityOf(userAnswer) == PolarityAnalyzer.PolarityLabel.NEGATIVE
        if (userNegated == expectedNegated) {
            return RubricItemFeature(item, 1.0f, 0.90f, listOf(Signal.PolarityMatched(item.expected)), "極性一致")
        }
        val c = PolarityAnalyzer.compare(userAnswer, item.expected)
        // expected が「POSITIVE/NEGATIVE」ラベルの場合、参照文が無いためスコープ比較はできない。
        // 極性不一致自体を矛盾シグナルとする(自動分解されたPOLARITY項目はこの経路になる)。
        val labelSpecified = item.expected.trim().uppercase() in setOf("POSITIVE", "NEGATIVE")
        val contradiction = labelSpecified || c.reversed
        val signals = mutableListOf<Signal>()
        if (contradiction) signals += Signal.ContradictionDetected(
            if (labelSpecified) "極性不一致(期待: ${item.expected})" else "極性反転: ${c.detail}"
        )
        signals += Signal.PolarityReversed(item.expected)
        return RubricItemFeature(item, 0f, 0.90f, signals, c.detail)
    }

    private fun relationFeature(userAnswer: String, item: RubricItem): RubricItemFeature {
        val c = RelationDirectionChecker.compare(userAnswer, item.expected)
        return when {
            c.reversed -> RubricItemFeature(
                item, 0f, 0.90f,
                listOf(
                    Signal.RelationReversed(item.expected, c.detail),
                    Signal.ContradictionDetected("関係反転: ${c.detail}")
                ),
                c.detail
            )
            c.matched -> RubricItemFeature(
                item, 1.0f, 0.85f,
                listOf(Signal.RelationMatched(item.expected)), "関係の向き一致"
            )
            else -> RubricItemFeature(item, 0.5f, 0.40f, listOf(Signal.Undeterminable), "模範の構造関係が見つからない")
        }
    }

    private fun explanationFeature(userAnswer: String, item: RubricItem): RubricItemFeature {
        val userLen = TextNorm.normalize(userAnswer).length
        val expLen = TextNorm.normalize(item.expected).length
        if (expLen == 0) return RubricItemFeature(item, 0.5f, 0.40f, listOf(Signal.Undeterminable))
        val ratio = (userLen.toFloat() / expLen).coerceAtMost(1f)
        val score = when {
            ratio >= 0.7f -> 0.9f
            ratio >= 0.4f -> 0.6f
            else -> 0.2f
        }
        return RubricItemFeature(item, score, 0.50f, notes = "説明量ヒューリスティック(ratio=$ratio)")
    }
}
