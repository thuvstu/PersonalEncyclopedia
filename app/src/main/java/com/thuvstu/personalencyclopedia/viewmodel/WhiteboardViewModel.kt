package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.WhiteboardDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.WhiteboardEntity
import com.thuvstu.personalencyclopedia.db.entity.WhiteboardNodeEntity
import com.thuvstu.personalencyclopedia.db.entity.WhiteboardNoteEntity
import com.thuvstu.personalencyclopedia.db.entity.WhiteboardSectionEntity
import com.thuvstu.personalencyclopedia.repository.WhiteboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WhiteboardViewModel @Inject constructor(
    private val repo: WhiteboardRepository,
    private val whiteboardDao: WhiteboardDao,
    private val entryDao: EntryDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val boardId: String? = savedStateHandle["boardId"]

    val boards: StateFlow<List<WhiteboardEntity>> = repo.observeBoards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentBoard: StateFlow<WhiteboardEntity?> = boardId?.let {
        repo.observeBoard(it).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    } ?: MutableStateFlow(null)

    val nodes: StateFlow<List<WhiteboardNodeEntity>> = boardId?.let {
        repo.observeNodes(it).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } ?: MutableStateFlow(emptyList())

    val sections: StateFlow<List<WhiteboardSectionEntity>> = boardId?.let {
        repo.observeSections(it).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } ?: MutableStateFlow(emptyList())

    /** ノードID → 表示タイトル(entry表題 or メモ先頭行)。表示専用の解決マップ。
     *  geometry/drag用の nodes フローには触れず、タイトル解決だけを分離する */
    val resolvedTitles: StateFlow<Map<String, String>> = boardId?.let { bId ->
        repo.observeNodes(bId).mapLatest { repo.resolveNodes(bId) }
            .map { list -> list.associate { it.node.id to it.displayTitle } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    } ?: MutableStateFlow(emptyMap())

    fun createBoard(title: String) {
        val t = title.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            repo.createBoard(t)
        }
    }

    fun addFreeNote(content: String) {
        val bId = boardId ?: return
        val c = content.trim()
        if (c.isBlank()) return
        viewModelScope.launch {
            // ランダムにずらして重なりを避ける
            val rx = 80f + (0..3).random() * 40f
            val ry = 80f + (0..3).random() * 40f
            repo.addFreeNote(bId, c, x = rx, y = ry)
        }
    }

    // ── 既存エントリーの配置 (Heptabaseのカード配置相当) ──
    private val entryQuery = MutableStateFlow("")

    /** 空欄時は最近20件、入力時はLIKE検索(お気に入り優先)。既配置の除外はしない */
    val entryResults: StateFlow<List<EntryEntity>> = entryQuery
        .flatMapLatest { q ->
            if (q.isBlank()) entryDao.observeRecent(20) else entryDao.search(q, 20)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEntryQuery(q: String) {
        entryQuery.value = q
    }

    fun addEntry(entryId: String) {
        val bId = boardId ?: return
        viewModelScope.launch {
            // ランダムにずらして重なりを避ける
            val rx = 80f + (0..3).random() * 40f
            val ry = 80f + (0..3).random() * 40f
            repo.addEntryRef(bId, entryId, x = rx, y = ry)
        }
    }

    fun moveNode(nodeId: String, x: Float, y: Float) {
        viewModelScope.launch {
            whiteboardDao.moveNode(nodeId, x, y)
            boardId?.let { repo.touchBoard(it) }
        }
    }

    fun deleteNode(nodeId: String) {
        viewModelScope.launch {
            whiteboardDao.deleteNode(nodeId)
            boardId?.let { repo.touchBoard(it) }
        }
    }

    // ── ★P1-1: セクションCRUD（Repo/DAOは実装済み・ここでVMに開通させる）──
    fun createSection(title: String) {
        val bId = boardId ?: return
        val t = title.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            // ノード配置と同じくランダムにずらして重なりを避ける
            val rx = 60f + (0..3).random() * 40f
            val ry = 60f + (0..3).random() * 40f
            repo.addSection(bId, t, x = rx, y = ry)
            repo.touchBoard(bId)
        }
    }

    fun renameSection(id: String, title: String) {
        val bId = boardId ?: return
        val t = title.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            val current = whiteboardDao.observeSections(bId).first()
                .firstOrNull { it.id == id } ?: return@launch
            whiteboardDao.upsertSection(current.copy(title = t))
            repo.touchBoard(bId)
        }
    }

    fun deleteSection(id: String) {
        viewModelScope.launch {
            repo.deleteSection(id)
            boardId?.let { repo.touchBoard(it) }
        }
    }
}