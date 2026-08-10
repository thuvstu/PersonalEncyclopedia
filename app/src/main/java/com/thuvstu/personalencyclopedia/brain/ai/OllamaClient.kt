package com.thuvstu.personalencyclopedia.brain.ai

import com.thuvstu.personalencyclopedia.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PCのOllama(LAN)クライアント（§4.1）。
 * PC側: OLLAMA_ORIGINS="*" で ollama serve、スマホと同じWi-Fi。
 */
@Singleton
class OllamaClient @Inject constructor() {
    companion object { private const val TAG = "Ollama" }

    var host: String = ""
    var chatModel: String = ""
    var embedModel: String = ""

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)   // ローカルLLMは遅い
        .build()

    fun isConfigured(): Boolean = host.isNotBlank() && chatModel.isNotBlank()
    private fun base(): String = "http://${host.removePrefix("http://").removeSuffix("/")}"

    /** 疎通確認（設定画面の「接続テスト」用） */
    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext false
        try {
            val req = Request.Builder().url("${base()}/api/tags").get().build()
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            AppLogger.w(TAG, "healthCheck failed: ${e.message}")
            false
        }
    }

    suspend fun generate(prompt: String, jsonMode: Boolean = false): String? {
        if (!isConfigured()) return null
        val body = buildJsonObject {
            put("model", chatModel); put("prompt", prompt); put("stream", false)
            if (jsonMode) put("format", "json")
        }
        return postWithRetry("${base()}/api/generate", body) { resp ->
            Json.parseToJsonElement(resp).jsonObject["response"]?.jsonPrimitive?.content
        }
    }

    suspend fun embed(text: String): FloatArray? {
        if (host.isBlank() || embedModel.isBlank()) return null
        val body = buildJsonObject { put("model", embedModel); put("input", text.take(2000)) }
        return postWithRetry("${base()}/api/embed", body) { resp ->
            Json.parseToJsonElement(resp).jsonObject["embeddings"]?.jsonArray?.firstOrNull()
                ?.jsonArray?.map { it.jsonPrimitive.float }?.toFloatArray()
        }
    }

    /** 簡易リトライ（2回まで） */
    private suspend fun <T> postWithRetry(
        url: String, body: JsonObject, parse: (String) -> T?
    ): T? = withContext(Dispatchers.IO) {
        val reqBody = body.toString().toRequestBody("application/json".toMediaType())
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                val req = Request.Builder().url(url).post(reqBody).build()
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string() ?: return@use
                    if (resp.isSuccessful) return@withContext parse(text)
                    AppLogger.w(TAG, "HTTP ${resp.code} (attempt ${attempt + 1})")
                }
            } catch (e: Exception) {
                lastError = e
                AppLogger.w(TAG, "request failed (attempt ${attempt + 1}): ${e.message}")
            }
        }
        AppLogger.e(TAG, "all retries failed", lastError)
        null
    }
}