package com.thuvstu.personalencyclopedia.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.thuvstu.personalencyclopedia.brain.ai.AiModels
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val GEMINI_API_KEY = stringPreferencesKey("GEMINI_API_KEY")
        val AUTO_CONNECT_ENABLED = booleanPreferencesKey("AUTO_CONNECT_ENABLED")
        val AUTO_CONNECT_THRESHOLD = floatPreferencesKey("AUTO_CONNECT_THRESHOLD")
        val SERVER_PORT = intPreferencesKey("SERVER_PORT")
        // ★v12.0 追加
        val SRS_ALGORITHM = stringPreferencesKey("SRS_ALGORITHM")
        val AI_PROVIDER = stringPreferencesKey("AI_PROVIDER")
        val GEMINI_MODEL = stringPreferencesKey("GEMINI_MODEL")
        val OLLAMA_HOST = stringPreferencesKey("OLLAMA_HOST")
        val OLLAMA_CHAT_MODEL = stringPreferencesKey("OLLAMA_CHAT_MODEL")
        val OLLAMA_EMBED_MODEL = stringPreferencesKey("OLLAMA_EMBED_MODEL")
        val INDEX_BUILT = booleanPreferencesKey("INDEX_BUILT")
    }

    val geminiApiKey: Flow<String?> =
        context.settingsDataStore.data.map { it[Keys.GEMINI_API_KEY] }
    val autoConnectEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.AUTO_CONNECT_ENABLED] ?: false }
    val autoConnectThreshold: Flow<Float> =
        context.settingsDataStore.data.map { it[Keys.AUTO_CONNECT_THRESHOLD] ?: 0.88f }
    val serverPort: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.SERVER_PORT] ?: 8080 }
    // ★v12.0 追加
    val srsAlgorithm: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.SRS_ALGORITHM] ?: "SM2" }
    val aiProvider: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.AI_PROVIDER] ?: "gemini" }
    val geminiModel: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.GEMINI_MODEL] ?: AiModels.GEMINI_CHAT_MODELS.first().id }
    val ollamaHost: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.OLLAMA_HOST] ?: "" }
    val ollamaChatModel: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.OLLAMA_CHAT_MODEL] ?: "" }
    val ollamaEmbedModel: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.OLLAMA_EMBED_MODEL] ?: "" }
    val indexBuilt: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.INDEX_BUILT] ?: false }

    suspend fun setGeminiApiKey(key: String) =
        context.settingsDataStore.edit { it[Keys.GEMINI_API_KEY] = key.trim() }
    suspend fun setAutoConnectEnabled(v: Boolean) =
        context.settingsDataStore.edit { it[Keys.AUTO_CONNECT_ENABLED] = v }
    suspend fun setAutoConnectThreshold(v: Float) =
        context.settingsDataStore.edit { it[Keys.AUTO_CONNECT_THRESHOLD] = v }
    suspend fun setServerPort(v: Int) =
        context.settingsDataStore.edit { it[Keys.SERVER_PORT] = v }
    // ★v12.0 追加
    suspend fun setSrsAlgorithm(algorithm: String) {
        require(algorithm in listOf("SM2", "FSRS"))
        context.settingsDataStore.edit { it[Keys.SRS_ALGORITHM] = algorithm }
    }
    suspend fun setAiProvider(v: String) =
        context.settingsDataStore.edit { it[Keys.AI_PROVIDER] = v }
    suspend fun setGeminiModel(v: String) =
        context.settingsDataStore.edit { it[Keys.GEMINI_MODEL] = v }
    suspend fun setOllamaHost(v: String) =
        context.settingsDataStore.edit { it[Keys.OLLAMA_HOST] = v }
    suspend fun setOllamaChatModel(v: String) =
        context.settingsDataStore.edit { it[Keys.OLLAMA_CHAT_MODEL] = v }
    suspend fun setOllamaEmbedModel(v: String) =
        context.settingsDataStore.edit { it[Keys.OLLAMA_EMBED_MODEL] = v }
    suspend fun setIndexBuilt(v: Boolean) =
        context.settingsDataStore.edit { it[Keys.INDEX_BUILT] = v }
}