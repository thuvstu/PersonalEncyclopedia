package com.thuvstu.personalencyclopedia.server

import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryThoughtDao
import com.thuvstu.personalencyclopedia.db.dao.QuizDao
import com.thuvstu.personalencyclopedia.db.dao.SrsReviewDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.QuizAttemptEntity
import com.thuvstu.personalencyclopedia.db.entity.QuizBankEntity
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalServer @Inject constructor(
    private val tokenManager: TokenManager,
    private val entryDao: EntryDao,
    private val thoughtDao: EntryThoughtDao,
    private val definitionDao: EntryDefinitionDao,
    private val srsReviewDao: SrsReviewDao,
    private val quizDao: QuizDao
) {
    private var server: EmbeddedServer<*, *>? = null

    var isRunning = false
        private set

    fun start(port: Int = 8080) {
        if (isRunning) return

        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }

            install(Authentication) {
                bearer("token-auth") {
                    authenticate { credential ->
                        val expected = tokenManager.getOrCreateToken()
                        if (credential.token == expected) {
                            UserIdPrincipal("owner")
                        } else {
                            null
                        }
                    }
                }
            }

            routing {
                // ── Health check (no auth) ──
                get("/health") {
                    call.respond(
                        mapOf(
                            "status" to "ok",
                            "version" to "0.2.0",
                            "phase" to "1"
                        )
                    )
                }

                // ── Authenticated API routes ──
                authenticate("token-auth") {
                    route("/api") {
                        entriesRoutes()
                        searchRoutes()
                        srsRoutes()
                        quizRoutes()
                    }
                }
            }
        }.also {
            it.start(wait = false)
            isRunning = true
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        isRunning = false
    }

    // ─────────────────────────────────────────────
    // /api/entries
    // ─────────────────────────────────────────────
    private fun Route.entriesRoutes() {
        route("/entries") {
            // GET /api/entries?limit=50&offset=0&type=thought
            get {
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
                val type = call.parameters["type"]

                val entries = if (type != null) {
                    entryDao.observeByType(type, limit, offset).first()
                } else {
                    entryDao.observeAll(limit, offset).first()
                }
                call.respond(entries.map { it.toResponse() })
            }

            // GET /api/entries/{id}
            get("/{id}") {
                val id = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing 'id' parameter")
                    )
                val entry = entryDao.getById(id)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("Entry not found: $id")
                    )
                entryDao.touch(id)
                call.respond(entry.toResponse())
            }

            // DELETE /api/entries/{id} (soft delete)
            delete("/{id}") {
                val id = call.parameters["id"]
                    ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing 'id' parameter")
                    )
                entryDao.softDelete(id)
                call.respond(HttpStatusCode.NoContent)
            }

            // PATCH /api/entries/{id}/favorite
            patch("/{id}/favorite") {
                val id = call.parameters["id"]
                    ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing 'id' parameter")
                    )
                val entry = entryDao.getById(id)
                    ?: return@patch call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("Entry not found: $id")
                    )
                entryDao.setFavorite(id, !entry.isFavorite)
                call.respond(mapOf("isFavorite" to !entry.isFavorite))
            }
        }
    }

    // ─────────────────────────────────────────────
    // /api/search
    // ─────────────────────────────────────────────
    private fun Route.searchRoutes() {
        get("/search") {
            val q = call.parameters["q"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Query parameter 'q' is required")
                )
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
            val type = call.parameters["type"]

            var results = entryDao.search(q, limit).first()
            if (type != null) {
                results = results.filter { it.type == type }
            }
            call.respond(results.map { it.toResponse() })
        }
    }

    // ─────────────────────────────────────────────
    // /api/srs
    // ─────────────────────────────────────────────
    private fun Route.srsRoutes() {
        route("/srs") {
            // GET /api/srs/due?limit=30
            get("/due") {
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 30
                val dueEntries = srsReviewDao.getDueEntries(limit = limit)
                call.respond(
                    dueEntries.map { entry ->
                        val def = definitionDao.getByEntryId(entry.id)
                        SrsDueResponse(
                            entryId = entry.id,
                            title = entry.title,
                            term = def?.term ?: entry.title,
                            definition = def?.definition ?: "",
                            reading = def?.reading,
                            field = def?.field
                        )
                    }
                )
            }

            // GET /api/srs/count
            get("/count") {
                val count = srsReviewDao.observeDueCount().first()
                call.respond(mapOf("dueCount" to count))
            }

            // POST /api/srs/review  { "entryId": "...", "grade": 4 }
            post("/review") {
                val body = call.receive<SrsReviewRequest>()
                if (body.entryId.isBlank() || body.grade !in 0..5) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("entryId required, grade must be 0-5")
                    )
                }
                // Delegate to SM-2 (simplified inline; full logic in SrsRepository)
                val current = srsReviewDao.getCurrentState(body.entryId)
                val review = com.thuvstu.personalencyclopedia.brain.srs.Sm2Algorithm.createReview(
                    entryId = body.entryId,
                    grade = body.grade,
                    previousInterval = current?.intervalDays ?: 0,
                    previousEase = current?.easeFactor ?: 2.5f,
                    repetitionCount = if (current != null && current.grade >= 2) {
                        when {
                            current.intervalDays <= 1 -> 1
                            current.intervalDays <= 6 -> 2
                            else -> 3
                        }
                    } else 0
                )
                srsReviewDao.insert(review)
                call.respond(
                    SrsReviewResponse(
                        entryId = body.entryId,
                        grade = body.grade,
                        intervalDays = review.intervalDays,
                        easeFactor = review.easeFactor,
                        nextReviewAt = review.nextReviewAt
                    )
                )
            }
        }
    }

    // ─────────────────────────────────────────────
    // /api/quiz
    // ─────────────────────────────────────────────
    private fun Route.quizRoutes() {
        route("/quiz") {
            // GET /api/quiz?limit=10&type=qa,mcq
            get {
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 10
                val types = call.parameters["type"]
                    ?.split(",")
                    ?: listOf("qa", "mcq", "fill_blank")

                val quizzes = quizDao.getRandomQuizzes(types, limit)
                call.respond(quizzes.map { it.toResponse() })
            }

            // GET /api/quiz/{id}
            get("/{id}") {
                val id = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing 'id'")
                    )
                val quiz = quizDao.getQuizById(id)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("Quiz not found: $id")
                    )
                call.respond(quiz.toResponse())
            }

            // POST /api/quiz/{id}/attempt  { "userAnswer": "..." }
            post("/{id}/attempt") {
                val id = call.parameters["id"]
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing 'id'")
                    )
                val quiz = quizDao.getQuizById(id)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("Quiz not found: $id")
                    )
                val body = call.receive<QuizAttemptRequest>()

                val gradeResult = com.thuvstu.personalencyclopedia.brain.quiz.MultiStageGrader.grade(
                    userAnswer = body.userAnswer,
                    correctAnswer = quiz.answer
                )

                val score = when {
                    gradeResult.isCorrect -> 1.0f
                    body.userAnswer == "__UNLEARNED__" -> 0f
                    else -> -1.0f
                }

                val attempt = QuizAttemptEntity(
                    quizId = id,
                    userAnswer = body.userAnswer,
                    isCorrect = if (body.userAnswer == "__UNLEARNED__") null
                    else gradeResult.isCorrect,
                    score = score,
                    gradingMethod = gradeResult.method,
                    hintsRevealed = body.hintsRevealed
                )
                quizDao.insertAttempt(attempt)

                call.respond(
                    QuizAttemptResponse(
                        quizId = id,
                        isCorrect = attempt.isCorrect,
                        score = score,
                        gradingMethod = gradeResult.method,
                        correctAnswer = quiz.answer,
                        explanation = quiz.explanation
                    )
                )
            }

            // GET /api/quiz/count
            get("/count") {
                val count = quizDao.observeQuizCount().first()
                call.respond(mapOf("quizCount" to count))
            }
        }
    }
}

// ─────────────────────────────────────────────
// DTOs (Request / Response)
// ─────────────────────────────────────────────

@Serializable
data class ErrorResponse(
    val message: String,
    val code: String = "ERROR"
)

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
data class SrsReviewRequest(
    val entryId: String,
    val grade: Int
)

@Serializable
data class SrsReviewResponse(
    val entryId: String,
    val grade: Int,
    val intervalDays: Int,
    val easeFactor: Float,
    val nextReviewAt: Long
)

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
data class QuizAttemptRequest(
    val userAnswer: String,
    val hintsRevealed: Int = 0
)

@Serializable
data class QuizAttemptResponse(
    val quizId: String,
    val isCorrect: Boolean?,
    val score: Float,
    val gradingMethod: String,
    val correctAnswer: String,
    val explanation: String?
)

// ─────────────────────────────────────────────
// Mappers
// ─────────────────────────────────────────────

private fun EntryEntity.toResponse() = EntryResponse(
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

private val json = Json { ignoreUnknownKeys = true }

private fun QuizBankEntity.toResponse(): QuizResponse {
    val choices = try {
        json.parseToJsonElement(choicesJson)
            .let { el ->
                kotlinx.serialization.json.JsonArray.serializer()
                el as? kotlinx.serialization.json.JsonArray
            }
            ?.map { (it as kotlinx.serialization.json.JsonPrimitive).content }
            ?: emptyList()
    } catch (_: Exception) { emptyList() }

    val hints = try {
        json.parseToJsonElement(hintsJson)
            .let { it as? kotlinx.serialization.json.JsonArray }
            ?.map { (it as kotlinx.serialization.json.JsonPrimitive).content }
            ?: emptyList()
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