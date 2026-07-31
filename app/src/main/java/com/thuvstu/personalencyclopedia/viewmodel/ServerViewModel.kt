package com.thuvstu.personalencyclopedia.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.thuvstu.personalencyclopedia.backup.BackupWorker
import com.thuvstu.personalencyclopedia.backup.PortableExportWorker
import com.thuvstu.personalencyclopedia.brain.ai.EmbeddingQueue
import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.brain.connection.ConnectionEngine
import com.thuvstu.personalencyclopedia.repository.SettingsRepository
import com.thuvstu.personalencyclopedia.server.LocalServer
import com.thuvstu.personalencyclopedia.server.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localServer: LocalServer,
    private val tokenManager: TokenManager,
    private val geminiClient: GeminiClient,
    private val settingsRepo: SettingsRepository,
    private val connectionEngine: ConnectionEngine,
    private val embeddingQueue: EmbeddingQueue
) : ViewModel() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    val token: StateFlow<String?> = tokenManager.tokenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val apiKey: StateFlow<String?> = settingsRepo.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val autoConnectEnabled: StateFlow<Boolean> = settingsRepo.autoConnectEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoConnectThreshold: StateFlow<Float> = settingsRepo.autoConnectThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.88f)

    private val _actionMessage = MutableSharedFlow<String>()
    val actionMessage: SharedFlow<String> = _actionMessage

    init {
        viewModelScope.launch { tokenManager.getOrCreateToken() }
    }

    fun toggleServer() {
        viewModelScope.launch {
            if (_isRunning.value) {
                localServer.stop(); _isRunning.value = false
            } else {
                localServer.start(port = settingsRepo.serverPort.first())
                _isRunning.value = true
            }
        }
    }

    fun regenerateToken() {
        viewModelScope.launch { tokenManager.regenerateToken() }
    }

    /** ★ APIキーを永続化 + 即時反映（旧版は永続化されていなかった不具合の修正） */
    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsRepo.setGeminiApiKey(key)
            geminiClient.setApiKey(key)
            _actionMessage.emit("✅ APIキーを保存しました")
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setAutoConnectEnabled(enabled)
            connectionEngine.autoConnectEnabled = enabled
            _actionMessage.emit(if (enabled) "自動接続候補をONにしました（§5.5.3）"
            else "自動接続候補をOFFにしました")
        }
    }

    fun setThreshold(v: Float) {
        viewModelScope.launch {
            settingsRepo.setAutoConnectThreshold(v)
            connectionEngine.autoConnectThreshold = v
        }
    }

    fun rebuildSearchIndex() {
        viewModelScope.launch(Dispatchers.IO) {
            embeddingQueue.rebuildAllSearchDocuments()
            _actionMessage.emit("✅ 検索インデックスを再構築しました")
        }
    }

    fun backupNow() {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<BackupWorker>().build())
        viewModelScope.launch { _actionMessage.emit("バックアップを開始しました") }
    }

    fun exportNow() {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<PortableExportWorker>().build())
        viewModelScope.launch { _actionMessage.emit("可搬エクスポートを開始しました") }
    }

    override fun onCleared() {
        super.onCleared()
        localServer.stop()
    }
}