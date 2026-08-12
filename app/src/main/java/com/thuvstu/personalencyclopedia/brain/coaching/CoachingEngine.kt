package com.thuvstu.personalencyclopedia.brain.coaching

import android.util.Log
import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.db.dao.AiExplanationDao
import com.thuvstu.personalencyclopedia.db.dao.QuizDao
import com.thuvstu.personalencyclopedia.db.entity.AiExplanationEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coaching Engine (§8.5).
 * explainMistake: AI explains a specific wrong answer.
 * analyzeWeakPoints: AI analyzes weakness patterns for a topic.
 * All results cached in ai_explanations to avoid re-calling the API.
 */
@Singleton
class CoachingEngine @Inject constructor(
    private val aiExplanationDao: AiExplanationDao,
    private val quizDao: QuizDao,
    private val geminiClient: GeminiClient
) {
    companion object {
        private const val TAG = "CoachingEngine"
        const val SOURCE_QUIZ_MISTAKE = "quiz_mistake"
        const val SOURCE_WEAK_POINT = "weak_point_analysis"
    }

    /**
     * Explain why a specific quiz attempt was wrong.
     * Returns cached explanation if available.
     */
    suspend fun explainMistake(quizId: String, userAnswer: String): String {
        // Check cache first
        val cached = aiExplanationDao.getCached(SOURCE_QUIZ_MISTAKE, quizId)
        if (cached != null) return cached.response

        val quiz = quizDao.getQuizById(quizId)
            ?: return "問題が見つかりませんでした。"

        if (!geminiClient.isConfigured()) {
            return buildFallbackExplanation(quiz.question, quiz.answer, userAnswer)
        }

        val prompt = """
            あなたは学習コーチです。以下の問題で学習者が誤答しました。
            なぜその答えが間違いなのか、正解に至る考え方を簡潔に解説してください。
            200文字以内で。

            【問題】${quiz.question}
            【正解】${quiz.answer}
            【学習者の答え】$userAnswer
            ${quiz.explanation?.let { "【補足】$it" } ?: ""}
        """.trimIndent()

        val response = geminiClient.generate(prompt)
            ?: buildFallbackExplanation(quiz.question, quiz.answer, userAnswer)

        // Cache the result
        aiExplanationDao.upsert(
            AiExplanationEntity(
                sourceType = SOURCE_QUIZ_MISTAKE,
                sourceId = quizId,
                prompt = prompt,
                response = response
            )
        )

        return response
    }

    /**
     * Analyze weak points for a topic based on wrong answers.
     */
    suspend fun analyzeWeakPoints(topicId: String): String {
        val cacheKey = "topic_$topicId"
        val cached = aiExplanationDao.getCached(SOURCE_WEAK_POINT, cacheKey)
        if (cached != null) return cached.response

        // Gather recent wrong attempts for the specified topic
        val wrongQuizzes = quizDao.getWrongQuizzesByTopic(topicId = topicId, limit = 20)
        if (wrongQuizzes.isEmpty()) {
            return "まだ誤答の記録がありません。クイズを解いて弱点を発見しましょう。"
        }

        if (!geminiClient.isConfigured()) {
            return buildFallbackWeakPointAnalysis(wrongQuizzes.size)
        }

        val quizSummary = wrongQuizzes.joinToString("\n") { q ->
            "・${q.question.take(60)}（正解: ${q.answer.take(30)}）"
        }

        val prompt = """
            あなたは学習コーチです。以下の誤答した問題リストから、
            学習者の弱点パターンを分析し、改善アドバイスを150文字以内で述べてください。

            【誤答リスト】
            $quizSummary
        """.trimIndent()

        val response = geminiClient.generate(prompt)
            ?: buildFallbackWeakPointAnalysis(wrongQuizzes.size)

        aiExplanationDao.upsert(
            AiExplanationEntity(
                sourceType = SOURCE_WEAK_POINT,
                sourceId = cacheKey,
                prompt = prompt,
                response = response
            )
        )

        return response
    }

    /**
     * Invalidate cache (e.g., when quiz content changes).
     */
    suspend fun invalidateQuizExplanation(quizId: String) {
        aiExplanationDao.invalidate(SOURCE_QUIZ_MISTAKE, quizId)
    }

    private fun buildFallbackExplanation(question: String, answer: String, userAnswer: String): String {
        return "正解は「$answer」です。\n" +
                "あなたの答え「$userAnswer」と見比べて、どこが違ったか確認しましょう。\n" +
                "（Gemini APIキーを設定すると、AIによる詳しい解説が利用できます）"
    }

    private fun buildFallbackWeakPointAnalysis(wrongCount: Int): String {
        return "最近 $wrongCount 問の誤答があります。\n" +
                "誤答した問題は自動的に再出題されるので、繰り返し挑戦しましょう。\n" +
                "（Gemini APIキーを設定すると、AIによる弱点分析が利用できます）"
    }
}