package com.thuvstu.personalencyclopedia.server.routes

import com.thuvstu.personalencyclopedia.db.entity.EntryDefinitionEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryThoughtEntity
import com.thuvstu.personalencyclopedia.server.ServerDependencies
import com.thuvstu.personalencyclopedia.server.dto.CreateEntryRequest
import com.thuvstu.personalencyclopedia.server.dto.ErrorResponse
import com.thuvstu.personalencyclopedia.server.dto.toResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.first
import java.util.UUID

private val ENTRY_TYPES = setOf(
    "thought", "definition", "webpage", "book", "video", "document",
    "media", "person", "org", "place", "event", "liked", "ai_conv"
)

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

        // ★PC入力: エントリー作成（thought/definitionは拡張まで作成、その他は共通のみ）
        post {
            val body = call.receive<CreateEntryRequest>()
            val type = body.type.ifBlank { "thought" }
            if (type !in ENTRY_TYPES) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Unknown type"))
            }
            if (body.title.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Title required"))
            }
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val entry = EntryEntity(
                id = id,
                type = type,
                title = body.title.trim(),
                content = body.content?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
                accessedAt = now
            )
            deps.entryDao.insert(entry)
            if (type == "thought") {
                deps.thoughtDao.insert(EntryThoughtEntity(entryId = id))
            } else if (type == "definition") {
                deps.definitionDao.insert(
                    EntryDefinitionEntity(
                        entryId = id,
                        term = body.title.trim(),
                        definition = body.content ?: ""
                    )
                )
            }
            call.respond(HttpStatusCode.Created, entry.toResponse())
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
