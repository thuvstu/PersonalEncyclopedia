package com.thuvstu.personalencyclopedia.server.dto

import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.QuizBankEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── 共通 ──

@Serializable
data class ErrorResponse(val message: String, val code: String = "ERROR")

// ── /api/entries ──

@Serializable
data class EntryResponse(
    val id: String,
    val type: String,
    val title: String,
    val content: String?,
    val summary: String?,
    val sourceUrl: String?,
    val isFavorite: Boolean,
    val isMuted: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

// ── /api/srs ──

@Serializable
data class SrsDueResponse(
    val entryId: String,
    val title: String,
    val term: String,
    val definition: String,
    val reading: String?,
    val field: String?
)

@Serializable
data class SrsReviewRequest(val entryId: String, val grade: Int)

@Serializable
data class SrsReviewResponse(
    val entryId: String,
    val grade: Int,
    val intervalDays: Int,
    val easeFactor: Float,
    val nextReviewAt: Long
)

// ── /api/quiz ──

@Serializable
data class QuizResponse(
    val id: String,
    val quizType: String,
    val question: String,
    val choices: List<String>,
    val hints: List<String>,
    val difficulty: Int,
    val generationMethod: String
)

@Serializable
data class QuizAttemptRequest(val userAnswer: String, val hintsRevealed: Int = 0)

@Serializable
data class QuizAttemptResponse(
    val quizId: String,
    val isCorrect: Boolean?,
    val score: Float,
    val gradingMethod: String,
    val correctAnswer: String,
    val explanation: String?
)

// ── /api/connections ──

@Serializable
data class ConnectionResponse(
    val connectionId: String,
    val relationType: String,
    val strength: Float,
    val note: String?,
    val isDirected: Boolean,
    val otherEntryId: String,
    val otherEntryTitle: String,
    val otherEntryType: String
)

@Serializable
data class CreateConnectionRequest(
    val entryAId: String,
    val entryBId: String,
    val relationType: String,
    val note: String? = null
)

@Serializable
data class CandidateResponse(
    val id: String,
    val entryAId: String,
    val entryBId: String,
    val similarity: Float,
    val suggestedType: String,
    val status: String
)

// ── /api/graph ──

@Serializable
data class GraphNodeResponse(
    val src: String,
    val dst: String,
    val relationType: String,
    val strength: Float,
    val depth: Int
)

// ── /api/progress ──

@Serializable
data class HeatmapResponse(val day: String, val count: Int)

// ── /api/plugins ──

@Serializable
data class PluginResponse(
    val id: String,
    val name: String,
    val version: String,
    val isActive: Boolean
)

// ── Mappers ──

private val json = Json { ignoreUnknownKeys = true }

fun EntryEntity.toResponse() = EntryResponse(
    id = id,
    type = type,
    title = title,
    content = content,
    summary = summary,
    sourceUrl = sourceUrl,
    isFavorite = isFavorite,
    isMuted = isMuted,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun QuizBankEntity.toQuizResponse(): QuizResponse {
    val choices = try {
        val arr = json.parseToJsonElement(choicesJson) as? kotlinx.serialization.json.JsonArray
        arr?.map { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    val hints = try {
        val arr = json.parseToJsonElement(hintsJson) as? kotlinx.serialization.json.JsonArray
        arr?.map { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    return QuizResponse(
        id = id,
        quizType = quizType,
        question = question,
        choices = choices,
        hints = hints,
        difficulty = difficulty,
        generationMethod = generationMethod
    )
}
