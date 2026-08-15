package com.thuvstu.personalencyclopedia.brain.quiz.rubric.provider

import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.util.AppLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Geminiベースの採点用プロバイダー(現行実装)。
 *
 * - Embedding: 既存 GeminiClient.embed をそのまま利用(複数模範解答とのmax比較は抽出側で行う)
 * - Entailment / CrossEncoder: 現在は実装しない(available=false)。否定・極性・関係反転の
 *   決定論解析がその役割を担う。将来端末内NLI / Rerankerに差し替える際はこのクラスを
 *   LocalGradingProviders に置き換えるだけでよい
 * - Judge: GeminiClient.generate(jsonMode=true) で最終判定。出力JSONを JudgeOutput へ変換
 */
@Singleton
class GeminiGradingProviders @Inject constructor(
    private val geminiClient: GeminiClient
) : IEmbeddingProvider, IEntailmentProvider, ICrossEncoderProvider, IJudgerProvider {

    companion object {
        private const val TAG = "GeminiGradingProviders"
        /** ユーザーに表示するプロバイダー名(evidence に記録) */
        const val PROVIDER_NAME = "gemini"
    }

    override val available: Boolean get() = geminiClient.isConfigured()

    override suspend fun embed(text: String): FloatArray? = geminiClient.embed(text)

    // Entailment / CrossEncoder は現在実装しない。
    // 契約どおり null を返し、パイプラインは null を「実装なし」と解釈する(available は
    // プロバイダーセット全体が設定済みかどうかを示し、各能力の有無は戻り値で判定する)。
    override suspend fun entailment(premise: String, hypothesis: String): EntailmentResult? = null

    override suspend fun score(pair: Pair<String, String>): Float? = null

    override suspend fun judge(prompt: String): JudgeOutput? {
        if (!geminiClient.isConfigured()) return null
        val raw = geminiClient.generate(prompt, jsonMode = true) ?: return null
        return parseJudgeOutput(raw)
    }

    /** LLM出力(JSON)を JudgeOutput へ変換。パース失敗は null(=judge不可)として扱う */
    private fun parseJudgeOutput(raw: String): JudgeOutput? {
        val obj = try {
            Json.parseToJsonElement(extractJsonObject(raw) ?: raw).jsonObject
        } catch (e: Exception) {
            AppLogger.w(TAG, "judge JSON parse failed: ${e.message}")
            return null
        }
        val isCorrect = obj["isCorrect"]?.jsonPrimitive?.booleanOrNull
            ?: obj["correct"]?.jsonPrimitive?.booleanOrNull ?: return null
        val score = obj["score"]?.jsonPrimitive?.floatOrNull
            ?: if (isCorrect) 1.0f else 0.0f
        val rationale = obj["rationale"]?.jsonPrimitive?.contentOrNull
            ?: obj["reason"]?.jsonPrimitive?.contentOrNull ?: ""
        val confidence = obj["confidence"]?.jsonPrimitive?.floatOrNull ?: 0.8f
        return JudgeOutput(
            isCorrect = isCorrect,
            score = score.coerceIn(0f, 1f),
            rationale = rationale,
            confidence = confidence.coerceIn(0f, 1f)
        )
    }

    /** LLMが ```json ... ``` で囲んで返すケースに対応するため、最初の JSON オブジェクトを抽出 */
    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
