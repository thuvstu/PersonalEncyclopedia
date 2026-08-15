package com.thuvstu.personalencyclopedia.brain.quiz.rubric.provider

/**
 * 新採点システムのモデルプロバイダー層(差し替えポイント)。
 *
 * 方針: 全プロバイダーは「利用不可なら null を返す」契約に統一する。
 * null 時は採点パイプライン側で confidence を下げ、最終LLM judge への defer、
 * あるいは決定論的フォールバックへ安全に流す。プロバイダーの切り替えは
 * GradingProviderModule(Hilt)のバインディング差し替えだけで完了し、
 * RubricFeatureExtractor / RubricJudge は Interface しか参照しない。
 *
 * 現状実装: GeminiGradingProviders(既存 GeminiClient アダプタ)
 * 将来実装: LocalGradingProviders(Qwen3-Embedding-4B / NLI / Qwen3-Reranker / ローカルLLM)
 * を同じ Interface で追加し、設定で切り替える。
 */
interface IEmbeddingProvider {
    val available: Boolean
    /** テキストの埋め込みベクトル。利用不可時は null */
    suspend fun embed(text: String): FloatArray?
}

data class EntailmentResult(
    /** premise(模範)が hypothesis(回答)を含意するか */
    val entails: Boolean,
    /** premise と hypothesis が矛盾するか */
    val contradicts: Boolean,
    val score: Float
)

/** NLI(含意・矛盾判定)。将来 Qwen3-NLI / Gemma-NLI 等の端末内モデルで実装 */
interface IEntailmentProvider {
    val available: Boolean
    /** null = 実装なし(confidence減算の対象) */
    suspend fun entailment(premise: String, hypothesis: String): EntailmentResult?
}

/** Cross-Encoder / Reranker 相当の精密文比較。将来 Qwen3-Reranker-4B 等で実装 */
interface ICrossEncoderProvider {
    val available: Boolean
    /** 2文の精密な関連スコア(0..1)。null = 実装なし */
    suspend fun score(pair: Pair<String, String>): Float?
}

/** 最終LLM judge の出力 */
data class JudgeOutput(
    val isCorrect: Boolean,
    val score: Float,
    val rationale: String,
    val confidence: Float
)

/** 最終LLM judge。Gemini / Ollama / ローカルLLM を切替可能にする */
interface IJudgerProvider {
    val available: Boolean
    /** null = judge不可(決定論的フォールバックへ) */
    suspend fun judge(prompt: String): JudgeOutput?
}
