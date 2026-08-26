package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.dao.WhiteboardDao
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
}