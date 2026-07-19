package com.thuvstu.personalencyclopedia

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.thuvstu.personalencyclopedia.backup.BackupWorker
import com.thuvstu.personalencyclopedia.backup.PortableExportWorker
import com.thuvstu.personalencyclopedia.db.AppDatabase
import com.thuvstu.personalencyclopedia.db.SeedData
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PersonalEncyclopediaApp : Application(), Configuration.Provider {

    @Inject lateinit var database: AppDatabase
    @Inject lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Seed entry types
        appScope.launch {
            database.entryTypeDao().insertAll(SeedData.entryTypes)
        }

        // Schedule daily encrypted backup (§6.3)
        BackupWorker.schedule(this)

        // Schedule weekly portable export (§6.3 layer 2)
        PortableExportWorker.schedule(this)
    }
}