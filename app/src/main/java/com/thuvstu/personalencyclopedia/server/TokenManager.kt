package com.thuvstu.personalencyclopedia.server

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "server_prefs")

/**
 * ★#S3: サーバートークンを EncryptedSharedPreferences (`secure_settings`) で管理する。
 * 旧実装の平文 DataStore (`server_prefs`) に残っているトークンは初回に移行して削除する。
 * SettingsRepository の暗号化ストアと同一ファイル・同一MasterKey方式のため、鍵は共有される。
 */
class TokenManager(private val context: Context) {

    private val tokenKey = stringPreferencesKey("access_token")
    private val encryptedTokenKey = "SERVER_ACCESS_TOKEN"

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _tokenState = MutableStateFlow<String?>(null)
    val tokenFlow: StateFlow<String?> = _tokenState

    /** 旧平文ストアからのワンタイム移行。 */
    private suspend fun migrateIfNeeded() {
        try {
            if (encryptedPrefs.contains(encryptedTokenKey)) return
            val plain = context.dataStore.data.first()[tokenKey]
            if (!plain.isNullOrBlank()) {
                Log.i("TokenManager", "Migrating plain-text token → EncryptedSharedPreferences")
                encryptedPrefs.edit().putString(encryptedTokenKey, plain).apply()
                context.dataStore.edit { it.remove(tokenKey) }
            }
        } catch (e: Exception) {
            Log.e("TokenManager", "token migration failed", e)
        }
    }

    suspend fun getOrCreateToken(): String {
        _tokenState.value?.let { return it }
        migrateIfNeeded()
        encryptedPrefs.getString(encryptedTokenKey, null)?.let {
            _tokenState.value = it
            return it
        }
        val newToken = UUID.randomUUID().toString()
        encryptedPrefs.edit().putString(encryptedTokenKey, newToken).apply()
        _tokenState.value = newToken
        return newToken
    }

    suspend fun regenerateToken(): String {
        migrateIfNeeded()
        val newToken = UUID.randomUUID().toString()
        encryptedPrefs.edit().putString(encryptedTokenKey, newToken).apply()
        _tokenState.value = newToken
        return newToken
    }
}
