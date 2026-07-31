package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.*
import com.thuvstu.personalencyclopedia.db.entity.WhiteboardNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WhiteboardDao {
    @Query("SELECT * FROM whiteboard_node")
    fun observeAll(): Flow<List<WhiteboardNodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(node: WhiteboardNodeEntity)

    @Query("DELETE FROM whiteboard_node WHERE entryId = :entryId")
    suspend fun removeByEntryId(entryId: String)

    @Query("UPDATE whiteboard_node SET x = :x, y = :y WHERE entryId = :entryId")
    suspend fun updatePosition(entryId: String, x: Float, y: Float)
}