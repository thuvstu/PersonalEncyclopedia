// viewmodel/WhiteboardViewModel.kt (新規)
package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.dao.ConnectionDao
import com.thuvstu.personalencyclopedia.db.dao.ConnectionListItem
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.WhiteboardDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.WhiteboardNodeEntity
import com.thuvstu.personalencyclopedia.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BoardNode(
    val node: WhiteboardNodeEntity,
    val entry: EntryEntity
)

@HiltViewModel
class WhiteboardViewModel @Inject constructor(
    private val whiteboardDao: WhiteboardDao,
    private val entryDao: EntryDao,
    private val connectionDao: ConnectionDao,
    private val connectionRepo: ConnectionRepository
) : ViewModel() {

    data class BoardState(
        val nodes: List<BoardNode> = emptyList(),
        val edges: List<ConnectionListItem> = emptyList()
    )

    val board: StateFlow<BoardState> = combine(
        whiteboardDao.observeAll(),
        connectionDao.observeAllConnections()
    ) { nodes, edges ->
        val withEntry = nodes.mapNotNull { n ->
            entryDao.getById(n.entryId)?.let { BoardNode(n, it) }
        }
        val onBoard = withEntry.map { it.entry.id }.toSet()
        // ボード上のノード同士の接続だけ描画
        val visible = edges.filter { it.entryAId in onBoard && it.entryBId in onBoard }
        BoardState(withEntry, visible)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BoardState())

    private val _searchResults = MutableStateFlow<List<EntryEntity>>(emptyList())
    val searchResults: StateFlow<List<EntryEntity>> = _searchResults

    fun searchEntries(q: String) {
        viewModelScope.launch {
            _searchResults.value = if (q.isBlank()) entryDao.observeRecent(20).first()
            else entryDao.search(q, 20).first()
        }
    }

    fun addEntry(entryId: String, x: Float, y: Float) {
        viewModelScope.launch {
            whiteboardDao.upsert(WhiteboardNodeEntity(entryId = entryId, x = x, y = y))
        }
    }

    fun moveNode(entryId: String, x: Float, y: Float) {
        viewModelScope.launch { whiteboardDao.updatePosition(entryId, x, y) }
    }

    fun removeNode(entryId: String) {
        viewModelScope.launch { whiteboardDao.removeByEntryId(entryId) }
    }

    fun connect(a: String, b: String) {
        viewModelScope.launch { connectionRepo.createManualConnection(a, b, "related") }
    }
}