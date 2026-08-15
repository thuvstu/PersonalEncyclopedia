package com.thuvstu.personalencyclopedia.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.thuvstu.personalencyclopedia.db.AppDatabase
import android.net.Uri
import com.thuvstu.personalencyclopedia.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase,
    private val settingsRepo: SettingsRepository,
    private val backupExporter: BackupExporter
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "BackupWorker"
        const val WORK_NAME = "daily_backup"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.UNMETERED)  // Wi-Fi only
                .build()

            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                repeatInterval = 1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            performBackup()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            runCatching {
                settingsRepo.setLastBackupResult(System.currentTimeMillis(), "FAILED: ${e.message}")
            }
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }

    private suspend fun performBackup() {
        val context = applicationContext

        // 1. Checkpoint WAL to ensure all data is in the main DB file
        database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")

        // 2. Locate the DB file
        val dbFile = context.getDatabasePath("encyclopedia.db")
        if (!dbFile.exists()) {
            Log.w(TAG, "DB file not found")
            return
        }

        // 3. Create backup directory
        val backupDir = File(context.filesDir, "backups/db-snapshots")
        backupDir.mkdirs()

        // 4. Copy DB file
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val plainCopy = File(backupDir, "encyclopedia_$timestamp.db")
        dbFile.copyTo(plainCopy, overwrite = true)

        // Also copy WAL and SHM if they exist
        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")
        if (walFile.exists()) walFile.copyTo(File(backupDir, "encyclopedia_$timestamp.db-wal"), true)
        if (shmFile.exists()) shmFile.copyTo(File(backupDir, "encyclopedia_$timestamp.db-shm"), true)

        // 5. Encrypt
        val encryptedFile = File(backupDir, "encyclopedia_$timestamp.db.enc")
        BackupEncryptor.encrypt(plainCopy, encryptedFile)

        // 6. Delete plaintext copy
        plainCopy.delete()
        File(backupDir, "encyclopedia_$timestamp.db-wal").delete()
        File(backupDir, "encyclopedia_$timestamp.db-shm").delete()

        // 7. Prune old backups (keep 30 generations)
        val backups = backupDir.listFiles { f -> f.extension == "enc" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        if (backups.size > 30) {
            backups.drop(30).forEach { it.delete() }
        }

        Log.i(TAG, "Backup complete: ${encryptedFile.name} (${encryptedFile.length() / 1024}KB)")

        // 8. SAF Remote Backup (B2 / B3)
        val safUriStr = settingsRepo.backupSafUri.first()
        if (!safUriStr.isNullOrBlank()) {
            try {
                val safUri = Uri.parse(safUriStr)
                val safResult = backupExporter.writeEncryptedFileToTree(safUri, encryptedFile)
                if (safResult.isSuccess) {
                    Log.i(TAG, "SAF backup succeeded: ${safResult.getOrNull()}")
                    settingsRepo.setLastBackupResult(System.currentTimeMillis(), "SUCCESS_SAF")
                } else {
                    val msg = safResult.exceptionOrNull()?.message ?: "Unknown SAF error"
                    Log.w(TAG, "SAF backup failed: $msg")
                    settingsRepo.setLastBackupResult(System.currentTimeMillis(), "ERROR_SAF: $msg")
                }
            } catch (e: Exception) {
                Log.w(TAG, "SAF export exception", e)
                settingsRepo.setLastBackupResult(System.currentTimeMillis(), "ERROR_SAF: ${e.message}")
            }
        } else {
            Log.i(TAG, "SAF backup URI not configured. Local backup kept.")
            settingsRepo.setLastBackupResult(System.currentTimeMillis(), "LOCAL_ONLY")
        }
    }
}