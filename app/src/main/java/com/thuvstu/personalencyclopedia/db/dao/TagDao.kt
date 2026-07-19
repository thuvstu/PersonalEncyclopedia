package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thuvstu.personalencyclopedia.db.entity.EntryTagEntity
import com.thuvstu.personalencyclopedia.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tag ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Query("""
        SELECT t.* FROM tag t
        INNER JOIN entry_tag et ON et.tagId = t.id
        WHERE et.entryId = :entryId
        ORDER BY t.name
    """)
    fun observeTagsForEntry(entryId: String): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkTag(link: EntryTagEntity)

    @Query("DELETE FROM entry_tag WHERE entryId = :entryId AND tagId = :tagId")
    suspend fun unlinkTag(entryId: String, tagId: String)
}