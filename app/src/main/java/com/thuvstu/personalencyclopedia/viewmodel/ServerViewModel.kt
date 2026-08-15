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
    private val embeddingQueue: EmbeddingQueue,
    private val backupExporter: com.thuvstu.personalencyclopedia.backup.BackupExporter
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

    val backupSafUri: StateFlow<String?> = settingsRepo.backupSafUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastBackupTime: StateFlow<Long?> = settingsRepo.lastBackupTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastBackupStatus: StateFlow<String?> = settingsRepo.lastBackupStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // §8.6 SRSアルゴリズム切替（"SM2" / "FSRS"）
    val srsAlgorithm: StateFlow<String> = settingsRepo.srsAlgorithm
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SM2")

    // §4.4 AI設定（プロバイダ・モデル）
    val aiProvider: StateFlow<String> = settingsRepo.aiProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "gemini")
    val geminiModel: StateFlow<String> = settingsRepo.geminiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val ollamaHost: StateFlow<String> = settingsRepo.ollamaHost
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val ollamaChatModel: StateFlow<String> = settingsRepo.ollamaChatModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val ollamaEmbedModel: StateFlow<String> = settingsRepo.ollamaEmbedModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _actionMessage = MutableSharedFlow<String>()
    val actionMessage: SharedFlow<String> = _actionMessage

    fun setBackupSafUri(uri: String?) {
        viewModelScope.launch {
            settingsRepo.setBackupSafUri(uri)
            _actionMessage.emit(if (uri.isNullOrBlank()) "バックアップ先フォルダの設定を解除しました" else "バックアップ先フォルダを設定しました")
        }
    }

    fun restoreFromBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            _actionMessage.emit("復元処理を開始中...")
            val result = backupExporter.restoreFromEncryptedUri(uri)
            if (result.isSuccess) {
                _actionMessage.emit("✅ 復元が完了しました。アプリを再読み込みしてください。")
            } else {
                _actionMessage.emit("❌ 復元失敗: ${result.exceptionOrNull()?.message}")
            }
        }
    }

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

    // §8.6: SRSアルゴリズム切替（"SM2" / "FSRS"）。次回レビューから反映。
    fun setSrsAlgorithm(algorithm: String) {
        viewModelScope.launch {
            settingsRepo.setSrsAlgorithm(algorithm)
            _actionMessage.emit(
                if (algorithm == "FSRS") "✅ SRSをFSRS-4.5に切り替えました（次回レビューから適用）"
                else "✅ SRSをSM-2に切り替えました（次回レビューから適用）"
            )
        }
    }

    // §4.4: AIプロバイダ切替（gemini / ollama）
    fun setAiProvider(provider: String) {
        viewModelScope.launch {
            settingsRepo.setAiProvider(provider)
            _actionMessage.emit(
                if (provider == "ollama") "AIプロバイダをOllama(LAN)に切り替えました"
                else "AIプロバイダをGeminiに切り替えました"
            )
        }
    }

    fun setGeminiModel(model: String) {
        viewModelScope.launch {
            settingsRepo.setGeminiModel(model)
            geminiClient.geminiModel = model   // 即時反映
            _actionMessage.emit("Geminiモデルを切り替えました")
        }
    }

    private fun syncOllamaClient() {
        viewModelScope.launch {
            val host = settingsRepo.ollamaHost.first()
            val chat = settingsRepo.ollamaChatModel.first()
            val embed = settingsRepo.ollamaEmbedModel.first()
            if (host.isNotBlank()) geminiClient.setOllama(host, chat, embed)
        }
    }

    fun setOllamaHost(v: String) {
        viewModelScope.launch {
            settingsRepo.setOllamaHost(v); syncOllamaClient()
        }
    }

    fun setOllamaChatModel(v: String) {
        viewModelScope.launch {
            settingsRepo.setOllamaChatModel(v); syncOllamaClient()
        }
    }

    fun setOllamaEmbedModel(v: String) {
        viewModelScope.launch {
            settingsRepo.setOllamaEmbedModel(v); syncOllamaClient()
            _actionMessage.emit("Ollama設定を保存しました")
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