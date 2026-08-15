package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.*
import java.util.UUID

@Entity(
    tableName = "quiz_bank",
    indices = [Index("topicId"), Index("quizType"), Index("isActive"), Index("sourceEntryId")]
)
data class QuizBankEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceEntryId: String? = null,
    val topicId: String? = null,
    val pluginId: String? = null,
    val quizType: String,           // qa/mcq/fill_blank/sort/essay/cloze/custom
    val question: String,
    val choicesJson: String = "[]",
    val answer: String,
    val gradingContextJson: String = "{}",
    val hintsJson: String = "[]",
    val explanation: String? = null,
    val imagesJson: String = "{}",
    val generationMethod: String,   // rule_based/cloud_ai/local_ai/manual
    val numericVariantConfigJson: String? = null,
    val difficulty: Int = 3,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "quiz_attempts",
    indices = [Index("quizId"), Index("attemptedAt")]
)
data class QuizAttemptEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val quizId: String,
    val userAnswer: String,
    val isCorrect: Boolean?,
    val score: Float,
    val gradingMethod: String,      // exact/fuzzy/semantic/llm
    val hintsRevealed: Int = 0,
    val attemptedAt: Long = System.currentTimeMillis(),
    val answeredWithinMs: Long? = null   // §8.7.3 (v8): 設問表示〜回答までの経過時間(早押しスコア係数用)
)

@DatabaseView(
    "SELECT quizId, MAX(score) AS masteryScore FROM quiz_attempts GROUP BY quizId"
)
data class QuizMasteryView(
    val quizId: String,
    val masteryScore: Float
)