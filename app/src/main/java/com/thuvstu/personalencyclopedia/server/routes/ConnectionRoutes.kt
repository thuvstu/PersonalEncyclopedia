package com.thuvstu.personalencyclopedia.server.routes

import com.thuvstu.personalencyclopedia.server.ServerDependencies
import com.thuvstu.personalencyclopedia.server.dto.CandidateResponse
import com.thuvstu.personalencyclopedia.server.dto.ConnectionResponse
import com.thuvstu.personalencyclopedia.server.dto.CreateConnectionRequest
import com.thuvstu.personalencyclopedia.server.dto.ErrorResponse
import com.thuvstu.personalencyclopedia.db.entity.ConnectionEntity
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.first

fun Route.connectionRoutes(deps: ServerDependencies) {
    route("/connections") {
        get {
            val entryId = call.parameters["entryId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("entryId required"))
            val connections = deps.connectionDao.observeConnectionsForEntry(entryId).first()
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
            val typeDef = deps.connectionDao.getTypeDef(body.relationType)
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
            deps.connectionDao.insert(connection)
            call.respond(HttpStatusCode.Created, mapOf("id" to connection.id))
        }

        delete("/{id}") {
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            deps.connectionDao.delete(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    route("/connection-candidates") {
        get {
            val candidates = deps.connectionDao.observePendingCandidates().first()
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
            deps.connectionDao.updateCandidateStatus(id, "approved")
            call.respond(mapOf("status" to "approved"))
        }

        post("/{id}/reject") {
            val id = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            deps.connectionDao.updateCandidateStatus(id, "rejected")
            call.respond(mapOf("status" to "rejected"))
        }
    }
}
