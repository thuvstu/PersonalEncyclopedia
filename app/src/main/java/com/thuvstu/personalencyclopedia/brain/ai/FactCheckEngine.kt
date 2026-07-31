package com.thuvstu.personalencyclopedia.brain.ai

import com.thuvstu.personalencyclopedia.db.dao.AiExplanationDao
import com.thuvstu.personalencyclopedia.db.entity.AiExplanationEntity
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FactCheckEngine @Inject constructor(
    private val geminiClient: GeminiClient,
    private val aiExplanationDao: AiExplanationDao
) {
    companion object {
        const val SOURCE_FACT_CHECK = "grounding_fact_check"
    }

    /**
     * ★H-6: 2段階ファクトチェック
     * ① Grounding付きで信頼性の高い調査を行う
     * ② その調査結果をコンテキストとして、JSON形式で構造化する
     */
    suspend fun checkFact(query: String, targetStatement: String): String? {
        val cacheKey = "$query|$targetStatement"
        val cached = aiExplanationDao.getCached(SOURCE_FACT_CHECK, cacheKey)
        if (cached != null) return cached.response

        if (!geminiClient.isConfigured()) return null

        // 段階1: Grounding付きで調査（JSONモードはオフ）
        val researchPrompt = """
            以下の主張について、信頼性の高い情報源に基づいて事実確認を行ってください。
            主張: "$targetStatement"
            関連クエリ: "$query"
            
            調査結果を簡潔にまとめてください。情報源のURLや信頼性についても言及してください。
        """.trimIndent()

        val researchResult = geminiClient.generate(researchPrompt, grounding = true)
            ?: return "調査に失敗しました。"

        // 段階2: 調査結果をコンテキストにしてJSON生成
        val jsonPrompt = """
            以下の調査結果に基づいて、事実確認の結果をJSON形式で出力してください。
            
            【調査結果】
            $researchResult
            
            【出力JSON構造】
            {
              "isAccurate": boolean,
              "correction": "間違いがある場合の修正文（正確ならnull）",
              "sources": ["ソースURLまたは情報源名1", "ソース2"],
              "confidence": "high/medium/low"
            }
        """.trimIndent()

        val jsonResult = geminiClient.generate(jsonPrompt, jsonMode = true, grounding = false)

        if (jsonResult != null) {
            aiExplanationDao.upsert(
                AiExplanationEntity(
                    sourceType = SOURCE_FACT_CHECK,
                    sourceId = cacheKey,
                    prompt = jsonPrompt,
                    response = jsonResult
                )
            )
        }
        return jsonResult
    }
}