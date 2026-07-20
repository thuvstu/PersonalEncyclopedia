package com.thuvstu.personalencyclopedia.repository

import com.thuvstu.personalencyclopedia.brain.quiz.MultiStageGrader
import com.thuvstu.personalencyclopedia.brain.quiz.RuleBasedQuizGenerator
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
    private val topicDao: TopicDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateQuizzesFromDefinitions(topicId: String? = null): Int {
        val allDefs = definitionDao.search("", limit = 500).first()
        if (allDefs.isEmpty()) return 0

        val quizzes = RuleBasedQuizGenerator.generateBatch(allDefs, topicId)
        quizDao.insertQuizzes(quizzes)
        return quizzes.size
    }

    suspend fun getNextQuizzes(
        topicId: String? = null,
        limit: Int = 10
    ): List<QuizBankEntity> {
        val wrong = quizDao.getWrongQuizzes(limit / 3)
        val unmastered = quizDao.getUnmasteredQuizzes(limit / 3)
        val random = quizDao.getRandomQuizzes(
            types = listOf("qa", "mcq", "fill_blank"),
            limit = limit
        )
        return (wrong + unmastered + random)
            .distinctBy { it.id }
            .take(limit)
    }

    suspend fun gradeAndRecord(
        quiz: QuizBankEntity,
        userAnswer: String,
        hintsRevealed: Int = 0
    ): QuizAttemptEntity {
        val gradeResult = MultiStageGrader.grade(userAnswer, quiz.answer)

        val score = when {
            gradeResult.isCorrect -> {
                val base = 1.0f - 0.3f * hintsRevealed
                maxOf(0f, base)
            }
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

    fun parseChoices(choicesJson: String): List<String> {
        return try {
            json.parseToJsonElement(choicesJson).jsonArray.map { it.jsonPrimitive.content }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseHints(hintsJson: String): List<String> {
        return try {
            json.parseToJsonElement(hintsJson).jsonArray.map { it.jsonPrimitive.content }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun observeQuizCount(): Flow<Int> = quizDao.observeQuizCount()

    fun observeAttemptsToday(): Flow<Int> {
        val startOfDay = getStartOfDay()
        return quizDao.observeAttemptsTodayCount(startOfDay)
    }

    fun observeCorrectToday(): Flow<Int> {
        val startOfDay = getStartOfDay()
        return quizDao.observeCorrectTodayCount(startOfDay)
    }

    private fun getStartOfDay(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}