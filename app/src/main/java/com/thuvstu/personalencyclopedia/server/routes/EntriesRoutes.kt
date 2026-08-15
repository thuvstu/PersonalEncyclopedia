package com.thuvstu.personalencyclopedia.server.routes

import com.thuvstu.personalencyclopedia.server.ServerDependencies
import com.thuvstu.personalencyclopedia.server.dto.ErrorResponse
import com.thuvstu.personalencyclopedia.server.dto.toResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.first

fun Route.entriesRoutes(deps: ServerDependencies) {
    route("/entries") {
        get {
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
            val type = call.parameters["type"]

            val entries = if (type != null) {
                deps.entryDao.observeByType(type, limit, offset).first()
            } else {
                deps.entryDao.observeAll(limit, offset).first()
            }
            call.respond(entries.map { it.toResponse() })
        }

        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val entry = deps.entryDao.getById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Entry not found"))
            deps.entryDao.touch(id)
            call.respond(entry.toResponse())
        }

        delete("/{id}") {
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            deps.entryDao.softDelete(id)
            call.respond(HttpStatusCode.NoContent)
        }

        patch("/{id}/favorite") {
            val id = call.parameters["id"]
                ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val entry = deps.entryDao.getById(id)
                ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
            deps.entryDao.setFavorite(id, !entry.isFavorite)
            call.respond(mapOf("isFavorite" to !entry.isFavorite))
        }
    }
}
