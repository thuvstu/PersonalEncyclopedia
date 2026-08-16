package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.ReadOnlySqlExecutor
import com.thuvstu.personalencyclopedia.db.dao.SavedQueryDao
import com.thuvstu.personalencyclopedia.db.entity.SavedQueryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SqlExplorerViewModel @Inject constructor(
    private val executor: ReadOnlySqlExecutor,
    private val savedQueryDao: SavedQueryDao
) : ViewModel() {

    val savedQueries: StateFlow<List<SavedQueryEntity>> = savedQueryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _schema = MutableStateFlow<List<ReadOnlySqlExecutor.SchemaObject>>(emptyList())
    val schema: StateFlow<List<ReadOnlySqlExecutor.SchemaObject>> = _schema

    private val _columns = MutableStateFlow<List<ReadOnlySqlExecutor.ColumnInfo>>(emptyList())
    val columns: StateFlow<List<ReadOnlySqlExecutor.ColumnInfo>> = _columns

    private val _selectedTable = MutableStateFlow<String?>(null)
    val selectedTable: StateFlow<String?> = _selectedTable

    private val _dbStats = MutableStateFlow<Map<String, String>>(emptyMap())
    val dbStats: StateFlow<Map<String, String>> = _dbStats

    private val _integrity = MutableStateFlow<String?>(null)
    val integrity: StateFlow<String?> = _integrity

    private val _result = MutableStateFlow<ReadOnlySqlExecutor.SqlExecutionResult?>(null)
    val result: StateFlow<ReadOnlySqlExecutor.SqlExecutionResult?> = _result

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    init {
        refreshSchema()
        refreshStats()
    }

    fun refreshSchema() {
        viewModelScope.launch {
            _schema.value = executor.listTables()
            _columns.value = emptyList()
            _selectedTable.value = null
        }
    }

    fun selectTable(name: String) {
        viewModelScope.launch {
            _selectedTable.value = name
            _columns.value = executor.tableInfo(name)
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            _dbStats.value = executor.dbStats()
        }
    }

    fun runIntegrityCheck() {
        viewModelScope.launch {
            _integrity.value = "checking..."
            _integrity.value = executor.integrityCheck()
        }
    }

    fun runQuery(sql: String) {
        if (sql.isBlank() || _isRunning.value) return
        viewModelScope.launch {
            _isRunning.value = true
            _result.value = executor.executeReadOnly(sql)
            _isRunning.value = false
        }
    }

    fun clearResult() {
        _result.value = null
    }

    fun saveQuery(name: String, sql: String) {
        if (name.isBlank() || sql.isBlank()) return
        viewModelScope.launch {
            savedQueryDao.insert(
                SavedQueryEntity(id = UUID.randomUUID().toString(), name = name.trim(), sql = sql)
            )
        }
    }

    fun deleteQuery(id: String) {
        viewModelScope.launch { savedQueryDao.delete(id) }
    }
}
