package com.thuvstu.personalencyclopedia.server.routes

import com.thuvstu.personalencyclopedia.server.ServerDependencies
import com.thuvstu.personalencyclopedia.server.dto.ErrorResponse
import com.thuvstu.personalencyclopedia.server.dto.GraphNodeResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.graphRoutes(deps: ServerDependencies) {
    get("/graph") {
        val entryId = call.parameters["entryId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("entryId required"))
        val depth = call.parameters["depth"]?.toIntOrNull() ?: 3
        val nodes = deps.connectionDao.traverseGraph(entryId, depth)
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
