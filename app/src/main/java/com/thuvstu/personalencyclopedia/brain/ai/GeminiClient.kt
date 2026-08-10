package com.thuvstu.personalencyclopedia.brain.ai

import com.thuvstu.personalencyclopedia.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AIプロバイダのファサード（§7.6）。
 * provider 設定で Gemini / Ollama にルーティング。
 */
@Singleton
class GeminiClient @Inject constructor(
    private val ollama: OllamaClient
) {
    companion object {
        private const val TAG = "GeminiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val RPM_LIMIT = 90
        private val INTERVAL_MS = 60_000L / RPM_LIMIT
    }

    var provider: String = "gemini"   // "gemini" | "ollama"
    var geminiModel: String = AiModels.GEMINI_CHAT_MODELS.first().id
    private var apiKey: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private var lastCallAt = 0L

    fun setApiKey(key: String) { apiKey = key }
    fun setOllama(host: String, chatModel: String, embedModel: String) {
        ollama.host = host; ollama.chatModel = chatModel; ollama.embedModel = embedModel
    }
    fun isConfigured(): Boolean =
        if (provider == "ollama") ollama.isConfigured() else !apiKey.isNullOrBlank()

    /** 設定画面の「接続テスト」用 */
    suspend fun healthCheck(): Boolean =
        if (provider == "ollama") ollama.healthCheck() else !apiKey.isNullOrBlank()

    private suspend fun rateLimit() {
        val wait = INTERVAL_MS - (System.currentTimeMillis() - lastCallAt)
        if (wait > 0) delay(wait)
        lastCallAt = System.currentTimeMillis()
    }

    suspend fun embed(text: String): FloatArray? {
        if (provider == "ollama") return ollama.embed(text)
        val key = apiKey ?: return null
        rateLimit()
        val requestBody = buildJsonObject {
            putJsonObject("content") {
                putJsonArray("parts") { addJsonObject { put("text", text.take(2000)) } }
            }
            put("outputDimensionality", 768)
        }
        val url = "$BASE_URL/models/${AiModels.GEMINI_EMBEDDING}:embedContent?key=$key"
        val request = Request.Builder().url(url)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType())).build()
        return try {
            val body = withContext(Dispatchers.IO) { httpClient.newCall(request).execute().body?.string() }
                ?: return null
            Json.parseToJsonElement(body).jsonObject["embedding"]?.jsonObject
                ?.get("values")?.jsonArray
                ?.let { arr -> FloatArray(arr.size) { i -> arr[i].jsonPrimitive.float } }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Embedding failed", e)
            null
        }
    }

    suspend fun generate(prompt: String, jsonMode: Boolean = false, grounding: Boolean = false): String? {
        if (provider == "ollama") return ollama.generate(prompt, jsonMode)
        val key = apiKey ?: return null
        rateLimit()
        val def = AiModels.chatById(geminiModel)
        val useGrounding = grounding && def.supportsGrounding
        val useJson = jsonMode && def.supportsJson && !useGrounding
        // 選択モデル → 他モデルへフォールバック
        val candidates = listOf(def.id) +
                AiModels.GEMINI_CHAT_MODELS.map { it.id }.filter { it != def.id }
        for (model in candidates) {
            try {
                val requestBody = buildJsonObject {
                    putJsonArray("contents") {
                        addJsonObject { putJsonArray("parts") { addJsonObject { put("text", prompt) } } }
                    }
                    if (useGrounding) putJsonArray("tools") { addJsonObject { putJsonObject("google_search") {} } }
                    if (useJson) putJsonObject("generationConfig") { put("responseMimeType", "application/json") }
                }
                val url = "$BASE_URL/models/$model:generateContent?key=$key"
                val request = Request.Builder().url(url)
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType())).build()
                val body = withContext(Dispatchers.IO) { httpClient.newCall(request).execute().body?.string() }
                    ?: continue
                val text = Json.parseToJsonElement(body).jsonObject["candidates"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content
                if (text != null) return text
            } catch (e: Exception) {
                AppLogger.w(TAG, "LLM $model failed: ${e.message}")
            }
        }
        return null
    }
}

// ── Vector utilities ──
fun FloatArray.toBlob(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    forEach { buffer.putFloat(it) }
    return buffer.array()
}

fun ByteArray.toFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(buffer.remaining() / 4) { buffer.getFloat() }
}

fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size) return 0f
    var dot = 0f; var na = 0f; var nb = 0f
    for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
    val denom = kotlin.math.sqrt(na) * kotlin.math.sqrt(nb)
    return if (denom < 1e-8f) 0f else dot / denom
}