package com.thuvstu.personalencyclopedia.brain.quiz

import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.brain.ai.cosineSimilarity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 意味的採点（§8.4 第6段階）。
 * 既存のEmbedding基盤を再利用（新規インフラ不要）。
 * 閾値0.85以上で正解、0.70以上で部分点（★#Q2: KDoc通りに実装。
 * 部分点は method="semantic-partial" で返し、QuizGraderServiceが減点緩和する）。
 */
@Singleton
class SemanticGrader @Inject constructor(
    private val geminiClient: GeminiClient
) {
    suspend fun grade(
        userAnswer: String,
        correctAnswer: String,
        threshold: Float = 0.85f
    ): MultiStageGrader.GradeResult? {
        if (!geminiClient.isConfigured()) return null
        val userVec = geminiClient.embed(userAnswer.take(500)) ?: return null
        val correctVec = geminiClient.embed(correctAnswer.take(500)) ?: return null
        val sim = cosineSimilarity(userVec, correctVec)
        return when {
            sim >= threshold -> MultiStageGrader.GradeResult(true, sim, "semantic")
            sim >= 0.70f -> MultiStageGrader.GradeResult(false, sim, "semantic-partial")
            else -> MultiStageGrader.GradeResult(false, 0f, "semantic")
        }
    }
}