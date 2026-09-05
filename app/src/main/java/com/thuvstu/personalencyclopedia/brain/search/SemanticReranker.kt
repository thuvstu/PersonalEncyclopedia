package com.thuvstu.personalencyclopedia.brain.search

import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ★rerank: 第二段階の精密並べ替え（LLM judge方式）。
 * RRFで集めた上位候補に対し、LLMがクエリとの関連度(0-100)を付け、
 * RRF順位と5:5でブレンドして並べ替える。
 * - API未設定・失敗時は何もせず元の順序を返す（graceful degradation）。
 * - 将来の端内モデル（Qwen3-Reranker等）への差し替え点。このクラスだけを置き換えればよい。
 * - 候補は最大10件・タイトル+要約のみを渡し、トークンと遅延を抑える。
 */
@Singleton
class SemanticReranker @Inject constructor(
    private val geminiClient: GeminiClient,
    private val entryDao: EntryDao
) {
    companion object {
        private const val MAX_CANDIDATES = 10
        private const val RRF_WEIGHT = 0.5
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun rerank(
        query: String,
        candidates: List<SearchResult>,
        limit: Int
    ): List<SearchResult> {
        if (candidates.size <= 1 || !geminiClient.isConfigured()) return candidates.take(limit)
        val top = candidates.take(MAX_CANDIDATES)
        val descs = top.mapIndexed { i, c ->
            val e = try { entryDao.getById(c.entryId) } catch (_: Exception) { null }
            val title = e?.title?.take(80) ?: c.entryId
            val summary = (e?.summary ?: e?.content)?.take(200) ?: ""
            "[${i}] $title: $summary"
        }
        val prompt = buildString {
            appendLine("以下の検索クエリに対する各候補の関連度を0-100の整数で評価し、JSONのみで出力せよ。")
            appendLine("クエリ: $query")
            descs.forEach { appendLine(it) }
            appendLine("出力例: {\"scores\": {\"0\": 90, \"1\": 40}}")
        }
        val scores: Map<Int, Int> = try {
            val raw = geminiClient.generate(prompt, jsonMode = true) ?: return candidates.take(limit)
            val obj = json.parseToJsonElement(raw.trim().substringAfter("{", "{").substringBeforeLast("}") + "}")
                .jsonObject
            val scoresObj = obj["scores"]?.jsonObject ?: return candidates.take(limit)
            scoresObj.mapNotNull { (k, v) ->
                val idx = k.toIntOrNull() ?: return@mapNotNull null
                val s = v.jsonPrimitive.intOrNull ?: return@mapNotNull null
                idx to s.coerceIn(0, 100)
            }.toMap()
        } catch (_: Exception) {
            return candidates.take(limit)
        }
        if (scores.isEmpty()) return candidates.take(limit)
        val maxRrf = top.maxOfOrNull { it.score }?.takeIf { it > 0 } ?: 1.0
        return (top.mapIndexed { i, c ->
            val llm = (scores[i] ?: 50) / 100.0
            c.copy(score = c.score / maxRrf * RRF_WEIGHT + llm * (1 - RRF_WEIGHT))
        }.sortedByDescending { it.score } + candidates.drop(MAX_CANDIDATES))
            .take(limit)
    }
}
