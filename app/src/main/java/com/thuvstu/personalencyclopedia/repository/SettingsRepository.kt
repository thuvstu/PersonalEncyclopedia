package com.thuvstu.personalencyclopedia.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.thuvstu.personalencyclopedia.brain.ai.AiModels
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

private const val ENCRYPTED_PREFS_FILE = "secure_settings"
private const val KEY_GEMINI_API = "GEMINI_API_KEY"
private const val TAG = "SettingsRepository"

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ★ C1: APIキーは EncryptedSharedPreferences に保存（GAP-3）
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _geminiApiKey = MutableStateFlow<String?>(null)

    /**
     * ★ C1: APIキーを初期化し、DataStoreに残る平文キーがあれば暗号化ストアへ移行する。
     * 起動時に一度呼ぶこと（PersonalEncyclopediaApp.onCreate）。
     */
    suspend fun initApiKey() = withContext(Dispatchers.IO) {
        try {
            // ワンタイム移行: DataStore に平文 API キーが残っていれば暗号化保存して削除
            val plainKey = context.settingsDataStore.data.map { it[Keys.GEMINI_API_KEY] }.first()
            if (!plainKey.isNullOrBlank()) {
                Log.i(TAG, "Migrating plain-text API key → EncryptedSharedPreferences")
                encryptedPrefs.edit().putString(KEY_GEMINI_API, plainKey.trim()).apply()
                context.settingsDataStore.edit { it.remove(Keys.GEMINI_API_KEY) }
            }
            // 暗号化ストアから読み込み
            _geminiApiKey.value = encryptedPrefs.getString(KEY_GEMINI_API, null)
        } catch (e: Exception) {
            Log.e(TAG, "initApiKey failed", e)
        }
    }

    val geminiApiKey: Flow<String?> = _geminiApiKey.asStateFlow()

    private object Keys {
        // GEMINI_API_KEY はマイグレーション後の削除用にキーを保持（読み取り専用）
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
        val BACKUP_SAF_URI = stringPreferencesKey("BACKUP_SAF_URI")
        val LAST_BACKUP_TIME = longPreferencesKey("LAST_BACKUP_TIME")
        val LAST_BACKUP_STATUS = stringPreferencesKey("LAST_BACKUP_STATUS")
    }

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
    val backupSafUri: Flow<String?> =
        context.settingsDataStore.data.map { it[Keys.BACKUP_SAF_URI] }
    val lastBackupTime: Flow<Long?> =
        context.settingsDataStore.data.map { it[Keys.LAST_BACKUP_TIME] }
    val lastBackupStatus: Flow<String?> =
        context.settingsDataStore.data.map { it[Keys.LAST_BACKUP_STATUS] }

    /** ★ C1: APIキーを暗号化ストアへ保存し、インメモリFlowを更新 */
    suspend fun setGeminiApiKey(key: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putString(KEY_GEMINI_API, key.trim()).apply()
        _geminiApiKey.value = key.trim()
    }
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
    suspend fun setBackupSafUri(uri: String?) =
        context.settingsDataStore.edit {
            if (uri.isNullOrBlank()) it.remove(Keys.BACKUP_SAF_URI)
            else it[Keys.BACKUP_SAF_URI] = uri
        }
    suspend fun setLastBackupResult(time: Long, status: String) =
        context.settingsDataStore.edit {
            it[Keys.LAST_BACKUP_TIME] = time
            it[Keys.LAST_BACKUP_STATUS] = status
        }
}