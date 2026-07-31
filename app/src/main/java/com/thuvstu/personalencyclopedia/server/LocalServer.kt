package com.thuvstu.personalencyclopedia.server

import com.thuvstu.personalencyclopedia.db.dao.ConnectionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryThoughtDao
import com.thuvstu.personalencyclopedia.db.dao.PluginDao
import com.thuvstu.personalencyclopedia.db.dao.ProgressEventDao
import com.thuvstu.personalencyclopedia.db.dao.QuizDao
import com.thuvstu.personalencyclopedia.db.dao.SrsReviewDao
import com.thuvstu.personalencyclopedia.db.entity.ConnectionEntity
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
    private val quizDao: QuizDao,
    private val connectionDao: ConnectionDao,
    private val progressEventDao: ProgressEventDao,
    private val pluginDao: PluginDao
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
                get("/health") {
                    call.respond(
                        mapOf(
                            "status" to "ok",
                            "version" to "0.3.0",
                            "phase" to "3"
                        )
                    )
                }

                authenticate("token-auth") {
                    route("/api") {
                        entriesRoutes()
                        searchRoutes()
                        srsRoutes()
                        quizRoutes()
                        connectionRoutes()
                        graphRoutes()
                        progressRoutes()
                        pluginRoutes()
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

    // ── /api/entries ──

    private fun Route.entriesRoutes() {
        route("/entries") {
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

            get("/{id}") {
                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                val entry = entryDao.getById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Entry not found"))
                entryDao.touch(id)
                call.respond(entry.toResponse())
            }

            delete("/{id}") {
                val id = call.parameters["id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                entryDao.softDelete(id)
                call.respond(HttpStatusCode.NoContent)
            }

            patch("/{id}/favorite") {
                val id = call.parameters["id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                val entry = entryDao.getById(id)
                    ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
                entryDao.setFavorite(id, !entry.isFavorite)
                call.respond(mapOf("isFavorite" to !entry.isFavorite))
            }
        }
    }

    // ── /api/search ──

    private fun Route.searchRoutes() {
        get("/search") {
            val q = call.parameters["q"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("q is required"))
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
            val type = call.parameters["type"]

            var results = entryDao.search(q, limit).first()
            if (type != null) {
                results = results.filter { it.type == type }
            }
            call.respond(results.map { it.toResponse() })
        }
    }

    // ── /api/srs ──

    private fun Route.srsRoutes() {
        route("/srs") {
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

            get("/count") {
                val count = srsReviewDao.observeDueCount().first()
                call.respond(mapOf("dueCount" to count))
            }

            post("/review") {
                val body = call.receive<SrsReviewRequest>()
                if (body.entryId.isBlank() || body.grade !in 0..5) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid input"))
                }
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

    // ── /api/quiz ──

    private fun Route.quizRoutes() {
        route("/quiz") {
            get {
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 10
                val types = call.parameters["type"]?.split(",") ?: listOf("qa", "mcq", "fill_blank")
                val quizzes = quizDao.getRandomQuizzes(types, limit)
                call.respond(quizzes.map { it.toQuizResponse() })
            }

            get("/{id}") {
                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                val quiz = quizDao.getQuizById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
                call.respond(quiz.toQuizResponse())
            }

            post("/{id}/attempt") {
                val id = call.parameters["id"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                val quiz = quizDao.getQuizById(id)
                    ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
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
                    isCorrect = if (body.userAnswer == "__UNLEARNED__") null else gradeResult.isCorrect,
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

            get("/count") {
                val count = quizDao.observeQuizCount().first()
                call.respond(mapOf("quizCount" to count))
            }
        }
    }

    // ── /api/connections ──

    private fun Route.connectionRoutes() {
        route("/connections") {
            get {
                val entryId = call.parameters["entryId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("entryId required"))
                val connections = connectionDao.observeConnectionsForEntry(entryId).first()
                call.respond(connections.map { c ->
                    ConnectionResponse(
                        connectionId = c.connectionId,
                        relationType = c.relationType,
                        strength = c.strength,
                        note = c.note,
                        isDirected = c.isDirected,
                        otherEntryId = c.otherEntryId,
                        otherEntryTitle = c.otherEntryTitle,
                        otherEntryType = c.otherEntryType
                    )
                })
            }

            post {
                val body = call.receive<CreateConnectionRequest>()
                val typeDef = connectionDao.getTypeDef(body.relationType)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Unknown relationType"))

                val canonicalA = if (body.entryAId < body.entryBId) body.entryAId else body.entryBId
                val canonicalB = if (body.entryAId < body.entryBId) body.entryBId else body.entryAId

                val connection = ConnectionEntity(
                    entryAId = body.entryAId,
                    entryBId = body.entryBId,
                    relationType = body.relationType,
                    note = body.note,
                    isDirected = typeDef.isDirected,
                    canonicalA = canonicalA,
                    canonicalB = canonicalB
                )
                connectionDao.insert(connection)
                call.respond(HttpStatusCode.Created, mapOf("id" to connection.id))
            }

            delete("/{id}") {
                val id = call.parameters["id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                connectionDao.delete(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        route("/connection-candidates") {
            get {
                val candidates = connectionDao.observePendingCandidates().first()
                call.respond(candidates.map { c ->
                    CandidateResponse(
                        id = c.id,
                        entryAId = c.entryAId,
                        entryBId = c.entryBId,
                        similarity = c.similarity,
                        suggestedType = c.suggestedType,
                        status = c.status
                    )
                })
            }

            post("/{id}/approve") {
                val id = call.parameters["id"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                connectionDao.updateCandidateStatus(id, "approved")
                call.respond(mapOf("status" to "approved"))
            }

            post("/{id}/reject") {
                val id = call.parameters["id"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                connectionDao.updateCandidateStatus(id, "rejected")
                call.respond(mapOf("status" to "rejected"))
            }
        }
    }

    // ── /api/graph ──

    private fun Route.graphRoutes() {
        get("/graph") {
            val entryId = call.parameters["entryId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("entryId required"))
            val depth = call.parameters["depth"]?.toIntOrNull() ?: 3
            val nodes = connectionDao.traverseGraph(entryId, depth)
            call.respond(nodes.map { n ->
                GraphNodeResponse(
                    src = n.src,
                    dst = n.dst,
                    relationType = n.relationType,
                    strength = n.strength,
                    depth = n.depth
                )
            })
        }
    }

    // ── /api/progress ──

    private fun Route.progressRoutes() {
        get("/progress/heatmap") {
            val days = call.parameters["days"]?.toIntOrNull() ?: 90
            val since = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
            val data = progressEventDao.getActivityByDay(since)
            call.respond(data.map { d ->
                HeatmapResponse(day = d.day, count = d.count)
            })
        }
    }

    // ── /api/plugins ──

    private fun Route.pluginRoutes() {
        get("/plugins") {
            val plugins = pluginDao.observeAll().first()
            call.respond(plugins.map { p ->
                PluginResponse(
                    id = p.id,
                    name = p.name,
                    version = p.version,
                    isActive = p.isActive
                )
            })
        }
    }
}

// ── DTOs ──

@Serializable
data class ErrorResponse(val message: String, val code: String = "ERROR")

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
data class SrsReviewRequest(val entryId: String, val grade: Int)

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
data class CandidateResponse(
    val id: String,
    val entryAId: String,
    val entryBId: String,
    val similarity: Float,
    val suggestedType: String,
    val status: String
)

@Serializable
data class GraphNodeResponse(
    val src: String,
    val dst: String,
    val relationType: String,
    val strength: Float,
    val depth: Int
)

@Serializable
data class HeatmapResponse(val day: String, val count: Int)

@Serializable
data class PluginResponse(
    val id: String,
    val name: String,
    val version: String,
    val isActive: Boolean
)

@Serializable
data class CreateConnectionRequest(
    val entryAId: String,
    val entryBId: String,
    val relationType: String,
    val note: String? = null
)

// ── Mappers ──

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

private fun QuizBankEntity.toQuizResponse(): QuizResponse {
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