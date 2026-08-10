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

    val currentBoard: StateFlow<WhiteboardEntity?> = boardId?.let { repo.observeBoard(it) }
        ?: MutableStateFlow(null)

    val nodes: StateFlow<List<WhiteboardNodeEntity>> = boardId?.let { repo.observeNodes(it) }
        ?: MutableStateFlow(emptyList())

    val sections: StateFlow<List<WhiteboardSectionEntity>> = boardId?.let { repo.observeSections(it) }
        ?: MutableStateFlow(emptyList())

    fun createBoard(title: String) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            whiteboardDao.upsertBoard(WhiteboardEntity(id = id, title = title))
        }
    }

    fun addFreeNote(content: String) {
        val bId = boardId ?: return
        viewModelScope.launch {
            val noteId = UUID.randomUUID().toString()
            whiteboardDao.upsertNote(WhiteboardNoteEntity(id = noteId, contentMd = content))
            whiteboardDao.upsertNode(WhiteboardNodeEntity(
                id = UUID.randomUUID().toString(),
                boardId = bId,
                noteId = noteId,
                x = 100f, y = 100f
            ))
        }
    }

    fun moveNode(nodeId: String, x: Float, y: Float) {
        viewModelScope.launch {
            whiteboardDao.moveNode(nodeId, x, y)
        }
    }

    fun deleteNode(nodeId: String) {
        viewModelScope.launch {
            whiteboardDao.deleteNode(nodeId)
        }
    }
}