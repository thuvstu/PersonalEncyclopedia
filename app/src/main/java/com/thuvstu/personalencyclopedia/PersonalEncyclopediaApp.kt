package com.thuvstu.personalencyclopedia

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.thuvstu.personalencyclopedia.backup.BackupWorker
import com.thuvstu.personalencyclopedia.backup.PortableExportWorker
import com.thuvstu.personalencyclopedia.task.TaskNotifyWorker
import com.thuvstu.personalencyclopedia.brain.ai.EmbeddingQueue
import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.brain.connection.ConnectionEngine
import com.thuvstu.personalencyclopedia.brain.search.InMemoryVectorIndex
import com.thuvstu.personalencyclopedia.db.AppDatabase
import com.thuvstu.personalencyclopedia.db.DemoData
import com.thuvstu.personalencyclopedia.db.SeedData
import com.thuvstu.personalencyclopedia.plugins.PluginEngine
import com.thuvstu.personalencyclopedia.repository.SettingsRepository
import com.thuvstu.personalencyclopedia.util.timed
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PersonalEncyclopediaApp : Application(), Configuration.Provider {
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var embeddingQueue: EmbeddingQueue
    @Inject lateinit var vectorIndex: InMemoryVectorIndex
    @Inject lateinit var connectionEngine: ConnectionEngine
    @Inject lateinit var pluginEngine: PluginEngine
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var geminiClient: GeminiClient

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // §3.4 起動シーケンスの段階的初期化。各フェーズの失敗を隔離し、
        // 1箇所の失敗(APIキー未設定等)がアプリ全体の起動を止めないようにする。
        appScope.launch {
            runStep("Phase A: DB初期化・シード") { initDatabase() }
            runStep("Phase B: Brain Layer初期化") { initBrainLayer() }
            runStep("Phase C: バックグラウンドサービス") { scheduleBackgroundWorkers() }
        }
    }

    /** §3.4: 各ステップを個別にtry-catch。失敗はAppLogger(Log.e)に記録し、UIは「一部機能が制限されています」の非致命的表示に留める。 */
    private suspend fun runStep(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e("App", "$name 初期化失敗", e)
        }
    }

    /** Phase A: DB初期化・シード。APIキー暗号化移行(§6.6)もここで行う。 */
    private suspend fun initDatabase() {
        // ★ C1: APIキー暗号化移行（初回起動時に平文→EncryptedSharedPreferencesへ1回だけ実行）
        settingsRepository.initApiKey()

        // 設定復元
        settingsRepository.geminiApiKey.first()?.takeIf { it.isNotBlank() }?.let {
            geminiClient.setApiKey(it)
        }
        connectionEngine.autoConnectEnabled = settingsRepository.autoConnectEnabled.first()
        connectionEngine.autoConnectThreshold = settingsRepository.autoConnectThreshold.first()

        connectionEngine.seedTypeDefs()
        pluginEngine.installBuiltinPlugins()

        DemoData.seed(
            entryDao = database.entryDao(),
            thoughtDao = database.entryThoughtDao(),
            definitionDao = database.entryDefinitionDao(),
            topicDao = database.topicDao(),
            quizDao = database.quizDao(),
            connectionDao = database.connectionDao(),
            whiteboardDao = database.whiteboardDao(),
            wikiDao = database.wikiArticleDao()
        )
        database.entryTypeDao().insertAll(SeedData.entryTypes)
    }

    /** Phase B: Brain Layer初期化。ベクトルインデックス・埋め込みキューの回復。 */
    private suspend fun initBrainLayer() {
        timed("App", "InMemoryVectorIndex.load") { vectorIndex.load() }
        embeddingQueue.recoverJobs()
        embeddingQueue.startWorker()
        embeddingQueue.rebuildAllSearchDocuments()
    }

    /** Phase C: バックグラウンドサービス(WorkManagerスケジュール)。 */
    private suspend fun scheduleBackgroundWorkers() {
        BackupWorker.schedule(this)
        PortableExportWorker.schedule(this)
        TaskNotifyWorker.scheduleSweep(this)  // ★通知系: ToDo期限チェック15分周期
    }
}