package com.thuvstu.personalencyclopedia.server.routes

import com.thuvstu.personalencyclopedia.server.ServerDependencies
import com.thuvstu.personalencyclopedia.server.dto.PluginResponse
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.flow.first

fun Route.pluginRoutes(deps: ServerDependencies) {
    get("/plugins") {
        val plugins = deps.pluginDao.observeAll().first()
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
