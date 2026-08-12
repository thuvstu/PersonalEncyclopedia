package com.thuvstu.personalencyclopedia.repository

import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.brain.quiz.LlmQuizGenerator
import com.thuvstu.personalencyclopedia.brain.quiz.MultiStageGrader
import com.thuvstu.personalencyclopedia.brain.quiz.RuleBasedQuizGenerator
import com.thuvstu.personalencyclopedia.brain.quiz.SemanticGrader
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
    private val semanticGrader: SemanticGrader,        // ★追加（G）
    private val geminiClient: GeminiClient             // ★追加
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

    suspend fun getNextQuizzes(topicId: String? = null, limit: Int = 10): List<QuizBankEntity> {
        val wrong = quizDao.getWrongQuizzesByTopic(topicId, limit / 3)
        val unmastered = quizDao.getUnmasteredQuizzes(limit / 3)
        val random = quizDao.getRandomQuizzes(
            types = listOf("qa", "mcq", "fill_blank"), limit = limit
        )
        return (wrong + unmastered + random).distinctBy { it.id }.take(limit)
    }

    suspend fun gradeAndRecord(
        quiz: QuizBankEntity,
        userAnswer: String,
        hintsRevealed: Int = 0
    ): QuizAttemptEntity {
        var gradeResult = MultiStageGrader.grade(userAnswer, quiz.answer)

        // ★G: 記述式で通常採点が不正解かつAPI利用可能な場合、意味的採点に昇格（§8.4第6段階）
        if (!gradeResult.isCorrect &&
            userAnswer != "__UNLEARNED__" &&
            userAnswer.length >= 5 &&
            quiz.quizType in listOf("qa", "essay")
        ) {
            semanticGrader.grade(userAnswer, quiz.answer)?.let { sem ->
                if (sem.isCorrect) gradeResult = sem
            }
        }

        val score = when {
            gradeResult.isCorrect -> maxOf(0f, 1.0f - 0.3f * hintsRevealed)
            userAnswer == "__UNLEARNED__" -> 0f
            else -> -1.0f
        }
        val attempt = QuizAttemptEntity(
            id = UUID.randomUUID().toString(),
            quizId = quiz.id,
            userAnswer = userAnswer,
            isCorrect = if (userAnswer == "__UNLEARNED__") null else gradeResult.isCorrect,
            score = score,
            gradingMethod = gradeResult.method,
            hintsRevealed = hintsRevealed
        )
        quizDao.insertAttempt(attempt)
        return attempt
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