package com.thuvstu.personalencyclopedia.server

import com.thuvstu.personalencyclopedia.server.routes.connectionRoutes
import com.thuvstu.personalencyclopedia.server.routes.entriesRoutes
import com.thuvstu.personalencyclopedia.server.routes.graphRoutes
import com.thuvstu.personalencyclopedia.server.routes.pluginRoutes
import com.thuvstu.personalencyclopedia.server.routes.progressRoutes
import com.thuvstu.personalencyclopedia.server.routes.quizRoutes
import com.thuvstu.personalencyclopedia.server.routes.searchRoutes
import com.thuvstu.personalencyclopedia.server.routes.srsRoutes
import com.thuvstu.personalencyclopedia.server.routes.stickyNoteRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ktor APIサーバー。ルーティングの登録のみを行う薄いエントリポイント(設計書§10.1)。
 * 各エンドポイントの実装は server/routes/ 配下に分割されている。
 */
@Singleton
class LocalServer @Inject constructor(
    private val tokenManager: TokenManager,
    private val deps: ServerDependencies
) {
    private var server: EmbeddedServer<*, *>? = null

    private val _isRunning = MutableStateFlow(false)

    /**
     * ★寿命分離: 稼働状態の唯一のソース。
     * 画面スコープの ViewModel が独自の Boolean を持つと、画面遷移だけで状態がズレる。
     * UI はこの Flow を購読するだけにする(SettingsScreen → ServerViewModel.isRunning)。
     */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun start(port: Int = 8080) {
        if (_isRunning.value) return

        server = embeddedServer(Netty, port = port) {
            // ★#S2: CORS導入。LAN内ブラウザ（Vite dev等）からの Authorization 付きfetchを通す。
            // 個人利用のため host 全許可＋credentials 方式ではなく Bearer ヘッダ方式を維持する。
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Patch)
                allowMethod(HttpMethod.Delete)
            }

            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }

            install(Authentication) {
                bearer("token-auth") {
                    authenticate { credential ->
                        val expected = tokenManager.getOrCreateToken()
                        if (credential.token == expected) {
                            UserIdPrincipal("owner")
                        } else {
                            null
                        }
                    }
                }
            }

            routing {
                get("/health") {
                    call.respond(
                        mapOf(
                            "status" to "ok",
                            "version" to "0.3.0",
                            "phase" to "3"
                        )
                    )
                }

                authenticate("token-auth") {
                    route("/api") {
                        entriesRoutes(deps)
                        searchRoutes(deps)
                        srsRoutes(deps)
                        quizRoutes(deps)
                        connectionRoutes(deps)
                        graphRoutes(deps)
                        progressRoutes(deps)
                        pluginRoutes(deps)
                        stickyNoteRoutes(deps)   // ★wt56
                    }
                }
            }
        }.also {
            it.start(wait = false)
            _isRunning.value = true
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        _isRunning.value = false
    }
}
