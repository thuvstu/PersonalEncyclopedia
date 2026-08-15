package com.thuvstu.personalencyclopedia.server.routes

import com.thuvstu.personalencyclopedia.server.ServerDependencies
import com.thuvstu.personalencyclopedia.server.dto.ErrorResponse
import com.thuvstu.personalencyclopedia.server.dto.toResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.flow.first

fun Route.searchRoutes(deps: ServerDependencies) {
    get("/search") {
        val q = call.parameters["q"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("q is required"))
        val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
        val type = call.parameters["type"]

        var results = deps.entryDao.search(q, limit).first()
        if (type != null) {
            results = results.filter { it.type == type }
        }
        call.respond(results.map { it.toResponse() })
    }
}
