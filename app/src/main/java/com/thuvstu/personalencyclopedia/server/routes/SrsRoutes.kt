package com.thuvstu.personalencyclopedia.server.routes

import com.thuvstu.personalencyclopedia.brain.srs.Sm2Algorithm
import com.thuvstu.personalencyclopedia.db.entity.SrsCurrentView
import com.thuvstu.personalencyclopedia.server.ServerDependencies
import com.thuvstu.personalencyclopedia.server.dto.ErrorResponse
import com.thuvstu.personalencyclopedia.server.dto.SrsDueResponse
import com.thuvstu.personalencyclopedia.server.dto.SrsReviewRequest
import com.thuvstu.personalencyclopedia.server.dto.SrsReviewResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.first

fun Route.srsRoutes(deps: ServerDependencies) {
    /** §5.8.5: 前回の累積成功反復回数。v8移行前データは従来の間隔日数ベース推定にフォールバック。 */
    fun resolvePriorRepetitionCount(current: SrsCurrentView?): Int {
        if (current == null) return 0
        val recorded = current.repetitionCount
        if (recorded > 0) return recorded
        return if (current.grade >= 2) {
            when {
                current.intervalDays <= 1 -> 1
                current.intervalDays <= 6 -> 2
                else -> 3
            }
        } else 0
    }

    route("/srs") {
        get("/due") {
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 30
            val dueEntries = deps.srsReviewDao.getDueEntries(limit = limit)
            call.respond(
                dueEntries.map { entry ->
                    val def = deps.definitionDao.getByEntryId(entry.id)
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
            val count = deps.srsReviewDao.observeDueCount().first()
            call.respond(mapOf("dueCount" to count))
        }

        post("/review") {
            val body = call.receive<SrsReviewRequest>()
            if (body.entryId.isBlank() || body.grade !in 0..5) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid input"))
            }
            val current = deps.srsReviewDao.getCurrentState(body.entryId)
            // §5.8.5 (v8): 明示記録された反復回数をそのまま前回値として使う（移行前データは従来推定へフォールバック）
            val priorCount = resolvePriorRepetitionCount(current)
            val review = Sm2Algorithm.createReview(
                entryId = body.entryId,
                grade = body.grade,
                previousInterval = current?.intervalDays ?: 0,
                previousEase = current?.easeFactor ?: 2.5f,
                repetitionCount = priorCount
            )
            deps.srsReviewDao.insert(review)
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
