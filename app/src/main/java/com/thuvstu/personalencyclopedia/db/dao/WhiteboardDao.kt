package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.*
import com.thuvstu.personalencyclopedia.db.entity.*
import kotlinx.coroutines.flow.Flow

data class NodeWithContent(
    val node: WhiteboardNodeEntity,
    val entry: EntryEntity?,
    val note: WhiteboardNoteEntity?
) {
    val displayTitle: String
        get() = entry?.title ?: note?.contentMd?.lineSequence()?.firstOrNull()?.take(40) ?: "（無題）"
    val isEntry: Boolean get() = node.entryId != null
}

@Dao
interface WhiteboardDao {
    // ── ボード ──
    @Query("SELECT * FROM whiteboard ORDER BY updatedAt DESC")
    fun observeBoards(): Flow<List<WhiteboardEntity>>

    @Query("SELECT * FROM whiteboard WHERE id = :id")
    fun observeBoard(id: String): Flow<WhiteboardEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBoard(board: WhiteboardEntity)

    @Query("DELETE FROM whiteboard WHERE id = :id")
    suspend fun deleteBoard(id: String)

    @Query("UPDATE whiteboard SET updatedAt = :now WHERE id = :id")
    suspend fun touchBoard(id: String, now: Long = System.currentTimeMillis())

    // ── ノード（配置）──
    @Query("SELECT * FROM whiteboard_node WHERE boardId = :boardId ORDER BY zIndex, createdAt")
    fun observeNodes(boardId: String): Flow<List<WhiteboardNodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNode(node: WhiteboardNodeEntity)

    @Query("UPDATE whiteboard_node SET x = :x, y = :y WHERE id = :id")
    suspend fun moveNode(id: String, x: Float, y: Float)

    @Query("UPDATE whiteboard_node SET sectionId = :sectionId WHERE id = :id")
    suspend fun setNodeSection(id: String, sectionId: String?)

    @Query("DELETE FROM whiteboard_node WHERE id = :id")
    suspend fun deleteNode(id: String)

    // ── 型なしノート ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: WhiteboardNoteEntity)

    @Query("SELECT * FROM whiteboard_note WHERE id = :id")
    suspend fun getNote(id: String): WhiteboardNoteEntity?

    @Query("DELETE FROM whiteboard_note WHERE id = :id")
    suspend fun deleteNote(id: String)

    // ── セクション ──
    @Query("SELECT * FROM whiteboard_section WHERE boardId = :boardId ORDER BY zIndex")
    fun observeSections(boardId: String): Flow<List<WhiteboardSectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSection(section: WhiteboardSectionEntity)

    @Query("UPDATE whiteboard_section SET x = :x, y = :y, width = :w, height = :h WHERE id = :id")
    suspend fun updateSectionRect(id: String, x: Float, y: Float, w: Float, h: Float)

    @Query("DELETE FROM whiteboard_section WHERE id = :id")
    suspend fun deleteSection(id: String)

    // ── 中身の一括解決（N+1回避）──
    @Query("SELECT * FROM entry WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun getEntriesByIds(ids: List<String>): List<EntryEntity>

    @Query("SELECT * FROM whiteboard_note WHERE id IN (:ids)")
    suspend fun getNotesByIds(ids: List<String>): List<WhiteboardNoteEntity>
}