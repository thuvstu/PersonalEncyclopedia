package com.thuvstu.personalencyclopedia.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.backup.BackupExporter
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.ConnectionDao
import com.thuvstu.personalencyclopedia.db.dao.QuizDao
import com.thuvstu.personalencyclopedia.db.dao.SrsReviewDao
import com.thuvstu.personalencyclopedia.db.dao.TagDao
import com.thuvstu.personalencyclopedia.db.dao.TopicDao
import com.thuvstu.personalencyclopedia.db.dao.WikiArticleDao
import com.thuvstu.personalencyclopedia.db.dao.WhiteboardDao
import com.thuvstu.personalencyclopedia.db.dao.TypeCount
import com.thuvstu.personalencyclopedia.backup.EntryExporter
import com.thuvstu.personalencyclopedia.backup.ExportFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class StorageInfo(
    val dbBytes: Long,
    val walBytes: Long,
    val backupBytes: Long,
    val dbPath: String
) {
    val totalBytes: Long get() = dbBytes + walBytes
}

@HiltViewModel
class DatabaseManagementViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryDao: EntryDao,
    private val connectionDao: ConnectionDao,
    private val quizDao: QuizDao,
    private val srsReviewDao: SrsReviewDao,
    private val tagDao: TagDao,
    private val topicDao: TopicDao,
    private val wikiDao: WikiArticleDao,
    private val whiteboardDao: WhiteboardDao,
    private val exporter: EntryExporter,
    private val backupExporter: com.thuvstu.personalencyclopedia.backup.BackupExporter
) : ViewModel() {

    val typeCounts: StateFlow<List<TypeCount>> =
        entryDao.observeCountsByType()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalEntries: StateFlow<Int> =
        entryDao.observeCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val connectionCount: StateFlow<Int> =
        connectionDao.observeCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val quizCount: StateFlow<Int> =
        quizDao.observeQuizCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val reviewCount: StateFlow<Int> =
        srsReviewDao.observeTotalReviewCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 透明性: 追加テーブル数は今後DAOにobserveCountを追加して拡張予定

    private val _storage = MutableStateFlow<StorageInfo?>(null)
    val storage: StateFlow<StorageInfo?> = _storage

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    private val _exportMessage = MutableSharedFlow<String>()
    val exportMessage: SharedFlow<String> = _exportMessage

    init { refreshStorage() }

    fun refreshStorage() {
        viewModelScope.launch {
            _storage.value = withContext(Dispatchers.IO) {
                val dbFile = context.getDatabasePath("encyclopedia.db")
                val wal = File(dbFile.path + "-wal")
                val backups = File(context.filesDir, "backups")
                StorageInfo(
                    dbBytes = if (dbFile.exists()) dbFile.length() else 0L,
                    walBytes = if (wal.exists()) wal.length() else 0L,
                    backupBytes = backups.walkBottomUp().filter { it.isFile }.sumOf { it.length() },
                    dbPath = dbFile.absolutePath
                )
            }
        }
    }

    fun export(uri: Uri, format: ExportFormat) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val count = exporter.export(uri, format)
                _exportMessage.emit("✅ ${count} 件を ${format.label} でエクスポートしました")
            } catch (e: Exception) {
                _exportMessage.emit("❌ エクスポート失敗: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }
    fun exportBackupToSaf(treeUri: Uri) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val result = backupExporter.exportToSaf(treeUri)
                _exportMessage.emit(
                    result.fold(
                        onSuccess = { "✅ $it" },
                        onFailure = { "❌ バックアップ失敗: ${it.message}" }
                    )
                )
            } finally {
                _isExporting.value = false
            }
        }
    }
}