package com.thuvstu.personalencyclopedia.repository

import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.brain.quiz.LlmQuizGenerator
import com.thuvstu.personalencyclopedia.brain.quiz.QuizGraderService
import com.thuvstu.personalencyclopedia.brain.quiz.RuleBasedQuizGenerator
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.QuizDao
import com.thuvstu.personalencyclopedia.db.dao.TopicDao
import com.thuvstu.personalencyclopedia.db.entity.QuizAttemptEntity
import com.thuvstu.personalencyclopedia.db.entity.QuizBankEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuizRepository @Inject constructor(
    private val quizDao: QuizDao,
    private val definitionDao: EntryDefinitionDao,
    private val topicDao: TopicDao,
    private val entryDao: EntryDao,                    // ★追加
    private val llmQuizGenerator: LlmQuizGenerator,    // ★追加
    private val geminiClient: GeminiClient,            // ★追加
    private val graderService: QuizGraderService       // ★最適化R6: 採点は共通サービスへ統一
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateQuizzesFromDefinitions(topicId: String? = null): Int {
        val allDefs = definitionDao.search("", limit = 500).first()
        if (allDefs.isEmpty()) return 0
        val quizzes = RuleBasedQuizGenerator.generateBatch(allDefs, topicId)
        val newQuizzes = quizzes.filter { quizDao.countByQuestion(it.question) == 0 }
        quizDao.insertQuizzes(newQuizzes)
        return newQuizzes.size
    }

    /**
     * ★C: 特定エントリーからのクイズ生成。
     * definition → ルールベース4種（コスト0・§8.3第1段階）
     * その他    → LLM（Gemini設定時のみ・§8.3第4段階）
     */
    suspend fun generateFromEntry(entryId: String): Int {
        definitionDao.getByEntryId(entryId)?.let { def ->
            val allDefs = definitionDao.search("", limit = 500).first()
            val distractors = allDefs.filter { it.field == def.field && it.entryId != def.entryId }
            val quizzes = mutableListOf(
                RuleBasedQuizGenerator.generateQaFromDefinition(def, null),
                RuleBasedQuizGenerator.generateReverseQa(def, null)
            )
            if (distractors.size >= 3) {
                quizzes.add(RuleBasedQuizGenerator.generateMcq(def, distractors, null))
            }
            RuleBasedQuizGenerator.generateFillBlank(def, null)?.let { quizzes.add(it) }
            val newQuizzes = quizzes.filter { quizDao.countByQuestion(it.question) == 0 }
            quizDao.insertQuizzes(newQuizzes)
            return newQuizzes.size
        }
        if (geminiClient.isConfigured()) {
            val entry = entryDao.getById(entryId) ?: return 0
            return llmQuizGenerator.generateFromEntry(entry, count = 3)
        }
        return 0
    }

    /**
     * ★最適化R1: 出題プールを排他分類で構成する（topicId/難易度/形式プール対応）。
     * - wrong: 不正解履歴あり・正解履歴なし（苦手）
     * - new:   一度も回答していない（未習）
     * - random: 正解済みを除くアクティブ全件（残りを補填）
     * 分類間の重複・正解済みの混入を排除し、セッション内は distinctBy で重複しない。
     */
    suspend fun getNextQuizzes(
        topicId: String? = null,
        limit: Int = 10,
        difficultyMin: Int? = null,
        types: List<String> = listOf("qa", "mcq", "fill_blank")
    ): List<QuizBankEntity> {
        val slice = (limit / 3).coerceAtLeast(1)
        val wrong = quizDao.getWrongUnmasteredQuizzes(topicId, difficultyMin, slice)
        val unlearned = quizDao.getNeverAttemptedQuizzes(topicId, difficultyMin, slice)
        val random = quizDao.getRandomUnmasteredQuizzes(topicId, types, difficultyMin, limit)
        return (wrong + unlearned + random).distinctBy { it.id }.take(limit)
    }

    // §8.7.2 プレッシャーテスト(全列挙型)の出題セット
    data class EnumerateChallenge(
        val field: String,
        val answers: List<String>
    )

    /** 同一分野のentry群から正解集合を動的生成する。対象が少なすぎる分野はスキップ。 */
    suspend fun buildEnumerateChallenge(): EnumerateChallenge? {
        val fields = definitionDao.getDistinctFields()
        for (field in fields) {
            val defs = definitionDao.getByField(field)
            val terms = defs.mapNotNull { it.term.trim().takeIf { t -> t.isNotBlank() } }
                .distinct().take(12)
            if (terms.size >= 3) return EnumerateChallenge(field, terms)
        }
        return null
    }

    /** §8.7.2: 回答文字列が正解集合に含まれるかを正規化比較で判定（マッチ済みは除外）。 */
    fun matchEnumerateAnswer(
        answer: String,
        correctSet: List<String>,
        matched: List<String>
    ): String? {
        val norm = answer.trim().replace(Regex("\\s+"), "")
        if (norm.isBlank()) return null
        val already = matched.map { it.replace(Regex("\\s+"), "") }.toSet()
        return correctSet.firstOrNull { c ->
            val cNorm = c.replace(Regex("\\s+"), "")
            cNorm.isNotEmpty() && cNorm !in already && cNorm == norm
        }
    }

    /**
     * 採点結果(試作)に rubric の採点根拠を添えて返す。
     * 既存呼び出し(QuizViewModel)は attempt を、新採点システムは rationale/evidence を使う。
     */
    data class QuizGradingResult(
        val attempt: QuizAttemptEntity,
        val rubricRationale: String? = null,
        val rubricEvidenceJson: String? = null,
        val rubricUsed: Boolean = false
    )

    suspend fun gradeAndRecord(
        quiz: QuizBankEntity,
        userAnswer: String,
        hintsRevealed: Int = 0,
        answeredWithinMs: Long? = null,   // §8.7.3 (v8): 設問表示〜回答までの経過時間
        hintPenalty: Float = 0.3f         // ★最適化R2: ヒント減点率（設定で調整可能）
    ): QuizGradingResult {
        // ★最適化R6: 採点は共通サービス(QuizGraderService)へ委譲（アプリ/サーバーで同一ロジック）
        val graded = graderService.grade(
            quiz = quiz,
            userAnswer = userAnswer,
            hintsRevealed = hintsRevealed,
            answeredWithinMs = answeredWithinMs,
            hintPenalty = hintPenalty
        )
        val attempt = QuizAttemptEntity(
            id = UUID.randomUUID().toString(),
            quizId = quiz.id,
            userAnswer = userAnswer,
            isCorrect = graded.isCorrect,
            score = graded.score,
            gradingMethod = graded.method,
            hintsRevealed = hintsRevealed,
            answeredWithinMs = answeredWithinMs
        )
        quizDao.insertAttempt(attempt)
        return QuizGradingResult(
            attempt = attempt,
            rubricRationale = graded.rubricRationale,
            rubricEvidenceJson = graded.rubricEvidenceJson,
            rubricUsed = graded.rubricUsed
        )
    }

    fun parseChoices(choicesJson: String): List<String> = try {
        json.parseToJsonElement(choicesJson).jsonArray.map { it.jsonPrimitive.content }
    } catch (_: Exception) { emptyList() }

    fun parseHints(hintsJson: String): List<String> = try {
        json.parseToJsonElement(hintsJson).jsonArray.map { it.jsonPrimitive.content }
    } catch (_: Exception) { emptyList() }

    fun observeQuizCount(): Flow<Int> = quizDao.observeQuizCount()

    fun observeAttemptsToday(): Flow<Int> = quizDao.observeAttemptsTodayCount(getStartOfDay())
    fun observeCorrectToday(): Flow<Int> = quizDao.observeCorrectTodayCount(getStartOfDay())

    private fun getStartOfDay(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}