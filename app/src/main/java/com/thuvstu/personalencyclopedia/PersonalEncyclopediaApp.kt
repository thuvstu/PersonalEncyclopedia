package com.thuvstu.personalencyclopedia

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.thuvstu.personalencyclopedia.backup.BackupWorker
import com.thuvstu.personalencyclopedia.backup.PortableExportWorker
import com.thuvstu.personalencyclopedia.brain.ai.EmbeddingQueue
import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.brain.connection.ConnectionEngine
import com.thuvstu.personalencyclopedia.brain.search.InMemoryVectorIndex
import com.thuvstu.personalencyclopedia.db.AppDatabase
import com.thuvstu.personalencyclopedia.db.DemoData
import com.thuvstu.personalencyclopedia.db.SeedData
import com.thuvstu.personalencyclopedia.plugins.PluginEngine
import com.thuvstu.personalencyclopedia.repository.SettingsRepository
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
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
                    connectionDao = database.connectionDao()
                )
                database.entryTypeDao().insertAll(SeedData.entryTypes)

                vectorIndex.load()
                embeddingQueue.recoverJobs()
                embeddingQueue.startWorker()
                embeddingQueue.rebuildAllSearchDocuments()
            } catch (e: Exception) {
                Log.e("App", "Init failed", e)
            }
        }
        try {
            BackupWorker.schedule(this)
            PortableExportWorker.schedule(this)
        } catch (e: Exception) {
            Log.e("App", "WorkManager schedule failed", e)
        }
    }
}