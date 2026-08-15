package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import com.thuvstu.personalencyclopedia.brain.quiz.rubric.provider.IJudgerProvider
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.provider.JudgeOutput

/**
 * 最終LLM judge(新採点システム.txt「最終LLM judge」)。
 *
 * 構造化evidence(RubricEvidence.toJudgeJson)をプロンプトに埋め込み、Gemini等に最終判定させる。
 * LLM が利用できない場合、または出力が無効な場合は決定論的フォールバック(heuristic)で
 * rubric重み付きスコアから判定する。
 */
class RubricJudge(
    private val judgerProvider: IJudgerProvider
) {

    data class JudgeOutcome(val output: JudgeOutput, val source: String) {
        val isCorrect: Boolean get() = output.isCorrect
        val score: Float get() = output.score
        val rationale: String get() = output.rationale
    }

    /** ヒューリスティック判定の正解スコア閾値 */
    val correctThreshold: Float = 0.6f

    suspend fun judge(
        question: String,
        userAnswer: String,
        correctAnswer: String,
        evidence: RubricEvidence,
        bundle: RubricParser.RubricBundle
    ): JudgeOutcome {
        if (judgerProvider.available) {
            val prompt = buildPrompt(question, userAnswer, correctAnswer, evidence, bundle)
            val output = judgerProvider.judge(prompt)
            if (output != null) return JudgeOutcome(output, "llm")
        }
        return JudgeOutcome(heuristic(evidence), "heuristic")
    }

    fun heuristic(evidence: RubricEvidence): JudgeOutput {
        val totalWeight = evidence.rubricItems.sumOf { it.item.weight.toDouble() }.toFloat().coerceAtLeast(0.001f)
        val score = evidence.rubricItems.sumOf { (it.item.weight * it.score).toDouble() }.toFloat() / totalWeight
        val isCorrect = score >= correctThreshold
        return JudgeOutput(
            isCorrect = isCorrect,
            score = score.coerceIn(0f, 1f),
            rationale = buildRationale(evidence),
            confidence = evidence.overallConfidence
        )
    }

    private fun buildPrompt(
        question: String,
        userAnswer: String,
        correctAnswer: String,
        evidence: RubricEvidence,
        bundle: RubricParser.RubricBundle
    ): String = buildString {
        appendLine("あなたは厳格な採点AIです。設問に対する学習者の回答を、ルーブリック採点結果を根拠に最終判定してください。")
        appendLine()
        appendLine("## 設問")
        appendLine(question)
        appendLine()
        appendLine("## 模範解答")
        bundle.modelAnswers.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## 学習者の回答")
        appendLine(userAnswer)
        appendLine()
        appendLine("## ルーブリック採点結果(evidence)")
        appendLine(evidence.toJudgeJson().toString())
        appendLine()
        appendLine("## 判定方針")
        appendLine("- 「極性反転」「関係反転」シグナルがあれば回答内容が正しくても不正解寄りに")
        appendLine("- 「判定不能」シグナルがあれば、シグナルに頼らず内容そのものを判断する")
        appendLine("- 部分点を 0.0〜1.0 のスコアで。理由(rationale)は日本語で簡潔に")
        appendLine("- confidence は 0.0〜1.0 でこの判定自体の確信度")
        appendLine()
        appendLine("JSON形式でだけ返答してください: {\"isCorrect\": true/false, \"score\": 0.0〜1.0, \"rationale\": \"...\", \"confidence\": 0.0〜1.0}")
    }

    private fun buildRationale(evidence: RubricEvidence): String = buildString {
        evidence.rubricItems.forEach { f ->
            val line = when (val s = f.signals.firstOrNull()) {
                is Signal.KeywordHit -> "✓ キーワード「${s.keyword}」含む"
                is Signal.KeywordNearMiss -> "△ キーワード「${s.keyword}」近似一致(${(s.similarity * 100).toInt()}%)"
                is Signal.KeywordMissing -> "✗ キーワード「${s.keyword}」欠落"
                is Signal.ConceptDetected -> "✓ 概念「${s.concept}」含む"
                is Signal.ConceptMissing -> "✗ 概念「${s.concept}」不足"
                is Signal.SemanticSimilar -> "• 意味類似度 ${(s.similarity * 100).toInt()}%"
                is Signal.PolarityMatched -> "✓ 極性一致(否定/肯定の向き)"
                is Signal.PolarityReversed -> "✗ 極性反転"
                is Signal.NumericEquivalent -> "✓ 数値・単位一致(${s.expected} = ${s.converted})"
                is Signal.NumericMismatch -> "✗ 数値・単位不一致"
                is Signal.RelationMatched -> "✓ 関係の向き一致"
                is Signal.RelationReversed -> "✗ 関係反転(${s.expected})"
                is Signal.ContradictionDetected -> "!! 矛盾検出: ${s.detail}"
                Signal.Undeterminable -> "△ 判定不能(この項目は低confidence)"
                null -> null
            }
            if (line != null) appendLine("${f.item.label}: $line")
        }
        append("総合スコア ${(evidence.rubricItems.sumOf { (it.item.weight * it.score).toDouble() }.toFloat() / evidence.rubricItems.sumOf { it.item.weight.toDouble() }.toFloat().coerceAtLeast(0.001f) * 100).toInt()}%")
    }
}
