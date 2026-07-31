package com.thuvstu.personalencyclopedia.brain.ai

import android.util.Log
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

@Singleton
class GeminiClient @Inject constructor() {

    companion object {
        private const val TAG = "GeminiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val EMBEDDING_MODEL = "gemini-embedding-2-preview"
        private const val RPM_LIMIT = 90
        private val INTERVAL_MS = 60_000L / RPM_LIMIT
    }

    private var apiKey: String? = null
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private var lastCallAt = 0L

    fun setApiKey(key: String) { apiKey = key }
    fun isConfigured(): Boolean = !apiKey.isNullOrBlank()

    private suspend fun rateLimit() {
        val wait = INTERVAL_MS - (System.currentTimeMillis() - lastCallAt)
        if (wait > 0) delay(wait)
        lastCallAt = System.currentTimeMillis()
    }

    suspend fun embed(text: String): FloatArray? {
        val key = apiKey ?: return null
        rateLimit()

        // ★ 修正: outputDimensionality はトップレベルの整数パラメータ
        val requestBody = buildJsonObject {
            putJsonObject("content") {
                putJsonArray("parts") {
                    addJsonObject { put("text", text.take(2000)) }
                }
            }
            put("outputDimensionality", 768)
        }

        val url = "$BASE_URL/models/$EMBEDDING_MODEL:embedContent?key=$key"
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val body = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().body?.string()
            } ?: return null

            val jsonObj = Json.parseToJsonElement(body).jsonObject
            jsonObj["embedding"]?.jsonObject?.get("values")?.jsonArray?.let { arr ->
                FloatArray(arr.size) { i -> arr[i].jsonPrimitive.float }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Embedding failed", e)
            null
        }
    }

    suspend fun generate(
        prompt: String,
        jsonMode: Boolean = false,
        grounding: Boolean = false // ★追加
    ): String? {
        val key = apiKey ?: return null
        rateLimit()
        val models = listOf("gemini-2.5-flash", "gemini-3-flash-preview")

        for (model in models) {
            try {
                val requestBody = buildJsonObject {
                    putJsonArray("contents") {
                        addJsonObject {
                            putJsonArray("parts") {
                                addJsonObject { put("text", prompt) }
                            }
                        }
                    }
                    // ★ Groundingが有効な場合、toolsにgoogle_searchを追加
                    if (grounding) {
                        putJsonArray("tools") {
                            addJsonObject {
                                putJsonObject("google_search") {}
                            }
                        }
                    }

                    // GroundingとresponseMimeTypeは同時不可のため、grounding時はJSONモードをオフにする（または2段階で回避）
                    if (jsonMode && !grounding) {
                        putJsonObject("generationConfig") {
                            put("responseMimeType", "application/json")
                        }
                    }
                }

                val url = "$BASE_URL/models/$model:generateContent?key=$key"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val body = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().body?.string()
                } ?: continue

                val jsonObj = Json.parseToJsonElement(body).jsonObject
                val text = jsonObj["candidates"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content

                if (text != null) return text
            } catch (e: Exception) {
                Log.w(TAG, "LLM $model failed: ${e.message}")
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
    for (i in a.indices) {
        dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]
    }
    val denom = kotlin.math.sqrt(na) * kotlin.math.sqrt(nb)
    return if (denom < 1e-8f) 0f else dot / denom
}