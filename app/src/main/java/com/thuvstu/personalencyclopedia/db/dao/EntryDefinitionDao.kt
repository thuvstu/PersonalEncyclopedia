package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.thuvstu.personalencyclopedia.db.entity.EntryDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDefinitionDao {
    @Insert
    suspend fun insert(def: EntryDefinitionEntity)

    @Update
    suspend fun update(def: EntryDefinitionEntity)

    @Query("SELECT * FROM entry_definition WHERE entryId = :entryId")
    suspend fun getByEntryId(entryId: String): EntryDefinitionEntity?

    @Query("SELECT * FROM entry_definition WHERE entryId = :entryId")
    fun observeByEntryId(entryId: String): Flow<EntryDefinitionEntity?>

    @Query("""
        SELECT ed.* FROM entry_definition ed
        INNER JOIN entry e ON e.id = ed.entryId
        WHERE e.deletedAt IS NULL
          AND (ed.term LIKE '%' || :q || '%' OR ed.definition LIKE '%' || :q || '%')
        ORDER BY e.createdAt DESC
        LIMIT :limit
    """)
    fun search(q: String, limit: Int = 50): Flow<List<EntryDefinitionEntity>>
}