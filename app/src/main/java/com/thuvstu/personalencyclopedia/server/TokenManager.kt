package com.thuvstu.personalencyclopedia.server

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "server_prefs")

class TokenManager(private val context: Context) {

    private val tokenKey = stringPreferencesKey("access_token")

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[tokenKey] }

    suspend fun getOrCreateToken(): String {
        val existing = context.dataStore.data.first()[tokenKey]
        if (existing != null) return existing
        val newToken = UUID.randomUUID().toString()
        context.dataStore.edit { it[tokenKey] = newToken }
        return newToken
    }

    suspend fun regenerateToken(): String {
        val newToken = UUID.randomUUID().toString()
        context.dataStore.edit { it[tokenKey] = newToken }
        return newToken
    }
}