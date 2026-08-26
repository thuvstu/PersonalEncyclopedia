package com.thuvstu.personalencyclopedia.repository

import com.thuvstu.personalencyclopedia.db.dao.NodeWithContent
import com.thuvstu.personalencyclopedia.db.dao.WhiteboardDao
import com.thuvstu.personalencyclopedia.db.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhiteboardRepository @Inject constructor(
    private val whiteboardDao: WhiteboardDao
) {
    fun observeBoards(): Flow<List<WhiteboardEntity>> = whiteboardDao.observeBoards()
    fun observeBoard(id: String): Flow<WhiteboardEntity?> = whiteboardDao.observeBoard(id)
    fun observeNodes(boardId: String): Flow<List<WhiteboardNodeEntity>> = whiteboardDao.observeNodes(boardId)
    fun observeSections(boardId: String): Flow<List<WhiteboardSectionEntity>> = whiteboardDao.observeSections(boardId)

    suspend fun createBoard(title: String, summary: String? = null): String {
        val board = WhiteboardEntity(title = title, summary = summary)
        whiteboardDao.upsertBoard(board)
        return board.id
    }

    suspend fun deleteBoard(id: String) = whiteboardDao.deleteBoard(id)

    /** 型なし自由記述カードを作成して配置 */
    suspend fun addFreeNote(boardId: String, contentMd: String, x: Float, y: Float): String {
        val note = WhiteboardNoteEntity(contentMd = contentMd)
        whiteboardDao.upsertNote(note)
        whiteboardDao.upsertNode(
            WhiteboardNodeEntity(boardId = boardId, noteId = note.id, x = x, y = y)
        )
        whiteboardDao.touchBoard(boardId)
        return note.id
    }

    /** 既存エントリーをボードに配置（参照） */
    suspend fun addEntryRef(boardId: String, entryId: String, x: Float, y: Float) {
        whiteboardDao.upsertNode(
            WhiteboardNodeEntity(boardId = boardId, entryId = entryId, x = x, y = y)
        )
        whiteboardDao.touchBoard(boardId)
    }

    suspend fun moveNode(nodeId: String, x: Float, y: Float) =
        whiteboardDao.moveNode(nodeId, x, y)

    suspend fun setNodeSection(nodeId: String, sectionId: String?) =
        whiteboardDao.setNodeSection(nodeId, sectionId)

    suspend fun deleteNode(nodeId: String) = whiteboardDao.deleteNode(nodeId)

    suspend fun touchBoard(boardId: String) = whiteboardDao.touchBoard(boardId)

    suspend fun addSection(boardId: String, title: String, x: Float, y: Float, colorHex: String? = null): String {
        val section = WhiteboardSectionEntity(
            boardId = boardId, title = title, x = x, y = y, colorHex = colorHex
        )
        whiteboardDao.upsertSection(section)
        return section.id
    }

    suspend fun deleteSection(id: String) = whiteboardDao.deleteSection(id)

    /** ノード + 中身（entry/note）を一括解決 */
    suspend fun resolveNodes(boardId: String): List<NodeWithContent> {
        val nodes = whiteboardDao.observeNodes(boardId).first()
        val entryIds = nodes.mapNotNull { it.entryId }
        val noteIds = nodes.mapNotNull { it.noteId }
        val entries = whiteboardDao.getEntriesByIds(entryIds).associateBy { it.id }
        val notes = whiteboardDao.getNotesByIds(noteIds).associateBy { it.id }
        return nodes.map { n ->
            NodeWithContent(n, entries[n.entryId], notes[n.noteId])
        }
    }
}