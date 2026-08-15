package com.thuvstu.personalencyclopedia.server.routes

import com.thuvstu.personalencyclopedia.server.ServerDependencies
import com.thuvstu.personalencyclopedia.server.dto.ErrorResponse
import com.thuvstu.personalencyclopedia.server.dto.QuizAttemptRequest
import com.thuvstu.personalencyclopedia.server.dto.QuizAttemptResponse
import com.thuvstu.personalencyclopedia.server.dto.toQuizResponse
import com.thuvstu.personalencyclopedia.db.entity.QuizAttemptEntity
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.first

fun Route.quizRoutes(deps: ServerDependencies) {
    route("/quiz") {
        get {
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 10
            val types = call.parameters["type"]?.split(",") ?: listOf("qa", "mcq", "fill_blank")
            val quizzes = deps.quizDao.getRandomQuizzes(types, limit)
            call.respond(quizzes.map { it.toQuizResponse() })
        }

        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val quiz = deps.quizDao.getQuizById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
            call.respond(quiz.toQuizResponse())
        }

        post("/{id}/attempt") {
            val id = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val quiz = deps.quizDao.getQuizById(id)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
            val body = call.receive<QuizAttemptRequest>()

            val gradeResult = deps.multiStageGrader.grade(
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
                hintsRevealed = body.hintsRevealed,
                answeredWithinMs = body.answeredWithinMs
            )
            deps.quizDao.insertAttempt(attempt)

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
            val count = deps.quizDao.observeQuizCount().first()
            call.respond(mapOf("quizCount" to count))
        }
    }
}
