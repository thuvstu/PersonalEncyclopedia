package com.thuvstu.personalencyclopedia.server.routes

import com.thuvstu.personalencyclopedia.repository.StickyNoteRepository
import com.thuvstu.personalencyclopedia.server.ServerDependencies
import com.thuvstu.personalencyclopedia.server.dto.CreateStickyNoteRequest
import com.thuvstu.personalencyclopedia.server.dto.ErrorResponse
import com.thuvstu.personalencyclopedia.server.dto.StickyConnectRequest
import com.thuvstu.personalencyclopedia.server.dto.StickyGrowResponse
import com.thuvstu.personalencyclopedia.server.dto.StickyLinkRequest
import com.thuvstu.personalencyclopedia.server.dto.StickyLinkResponse
import com.thuvstu.personalencyclopedia.server.dto.UpdateStickyNoteRequest
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

private val STICKY_COLORS = setOf("yellow", "pink", "blue", "green")

/**
 * ★wt56 付箋 API。
 *  GET    /api/entries/{id}/sticky-notes          一覧
 *  POST   /api/entries/{id}/sticky-notes          追加 {text,color}
 *  PATCH  /api/sticky-notes/{noteId}              本文/色/ピン/解決の部分更新
 *  DELETE /api/sticky-notes/{noteId}              削除
 *  GET    /api/sticky-notes/recent?limit=          未解決の新しい順（PCのフィード用）
 *  ★wt58 増殖:
 *  GET    /api/sticky-notes/{noteId}/links                  [[リンク]]解決 [{title, entryId?}]
 *  POST   /api/sticky-notes/{noteId}/promote/definition     付箋→定義カード(+extends接続)
 *  POST   /api/sticky-notes/{noteId}/create-from-link       {title, relationType?} 未作成リンクからスタブ作成+接続
 *  POST   /api/sticky-notes/{noteId}/connect                {targetEntryId?, relationType?} 直接接続
 */
fun Route.stickyNoteRoutes(deps: ServerDependencies) {
    route("/entries/{id}/sticky-notes") {
        get {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            call.respond(deps.stickyNoteRepo.getForEntry(id).map { it.toResponse() })
        }
        post {
            val id = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            deps.entryDao.getById(id)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Entry not found"))
            val body = call.receive<CreateStickyNoteRequest>()
            if (body.text.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Text required"))
            }
            val color = body.color.takeIf { it in STICKY_COLORS } ?: "yellow"
            val note = deps.stickyNoteRepo.add(id, body.text, color, StickyNoteRepository.SOURCE_API)
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Text required"))
            call.respond(HttpStatusCode.Created, note.toResponse())
        }
    }

    route("/sticky-notes") {
        get("/recent") {
            val limit = call.parameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            call.respond(deps.stickyNoteRepo.observeRecentUnresolved(limit).first().map { it.toResponse() })
        }
        patch("/{noteId}") {
            val noteId = call.parameters["noteId"]
                ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing noteId"))
            val note = deps.stickyNoteRepo.getById(noteId)
                ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Sticky note not found"))
            val body = call.receive<UpdateStickyNoteRequest>()
            if (body.text != null || body.color != null) {
                val color = body.color?.takeIf { it in STICKY_COLORS } ?: note.color
                deps.stickyNoteRepo.updateText(note, body.text ?: note.text, color)
            }
            body.isPinned?.let { deps.stickyNoteRepo.setPinned(note, it) }
            body.isResolved?.let { deps.stickyNoteRepo.setResolved(note, it) }
            val updated = deps.stickyNoteRepo.getById(noteId)
                ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Sticky note not found"))
            call.respond(updated.toResponse())
        }
        // ★wt58 増殖 API
        get("/{noteId}/links") {
            val noteId = call.parameters["noteId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing noteId"))
            val note = deps.stickyNoteRepo.getById(noteId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Sticky note not found"))
            call.respond(deps.stickyNoteRepo.resolveLinks(note).map { StickyLinkResponse(it.title, it.entry?.id) })
        }
        post("/{noteId}/promote/definition") {
            val noteId = call.parameters["noteId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing noteId"))
            val note = deps.stickyNoteRepo.getById(noteId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Sticky note not found"))
            val id = deps.stickyNoteRepo.promoteToDefinition(note)
            call.respond(HttpStatusCode.Created, StickyGrowResponse(entryId = id))
        }
        post("/{noteId}/create-from-link") {
            val noteId = call.parameters["noteId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing noteId"))
            val note = deps.stickyNoteRepo.getById(noteId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Sticky note not found"))
            val body = call.receive<StickyLinkRequest>()
            if (body.title.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("title required"))
            val id = deps.stickyNoteRepo.createCardFromLink(note, body.title, body.relationType ?: "related")
            call.respond(HttpStatusCode.Created, StickyGrowResponse(entryId = id))
        }
        post("/{noteId}/connect") {
            val noteId = call.parameters["noteId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing noteId"))
            val note = deps.stickyNoteRepo.getById(noteId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Sticky note not found"))
            val body = call.receive<StickyConnectRequest>()
            val id = deps.stickyNoteRepo.connectNow(note, body.targetEntryId, body.relationType ?: "related")
                ?: return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("接続先が見つからないか既に接続済み"))
            call.respond(HttpStatusCode.Created, StickyGrowResponse(connectionId = id))
        }
        delete("/{noteId}") {
            val noteId = call.parameters["noteId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing noteId"))
            val note = deps.stickyNoteRepo.getById(noteId)
                ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Sticky note not found"))
            deps.stickyNoteRepo.delete(note)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
