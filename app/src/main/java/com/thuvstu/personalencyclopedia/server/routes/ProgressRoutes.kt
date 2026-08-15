package com.thuvstu.personalencyclopedia.server.routes

import com.thuvstu.personalencyclopedia.server.ServerDependencies
import com.thuvstu.personalencyclopedia.server.dto.HeatmapResponse
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.progressRoutes(deps: ServerDependencies) {
    get("/progress/heatmap") {
        val days = call.parameters["days"]?.toIntOrNull() ?: 90
        val since = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
        val data = deps.progressEventDao.getActivityByDay(since)
        call.respond(data.map { d ->
            HeatmapResponse(day = d.day, count = d.count)
        })
    }
}
