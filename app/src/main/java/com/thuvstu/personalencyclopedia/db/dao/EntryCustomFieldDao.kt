package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.thuvstu.personalencyclopedia.db.entity.EntryCustomFieldEntity
import kotlinx.coroutines.flow.Flow

/** §5.8.3 カスタムフィールド（v8） */
@Dao
interface EntryCustomFieldDao {

    @Insert
    suspend fun insert(field: EntryCustomFieldEntity)

    @Insert
    suspend fun insertAll(fields: List<EntryCustomFieldEntity>)

    @Query("SELECT * FROM entry_custom_field WHERE entryId = :entryId ORDER BY sortOrder ASC, fieldName ASC")
    fun observeByEntryId(entryId: String): Flow<List<EntryCustomFieldEntity>>

    @Query("SELECT * FROM entry_custom_field WHERE entryId = :entryId ORDER BY sortOrder ASC, fieldName ASC")
    suspend fun getByEntryId(entryId: String): List<EntryCustomFieldEntity>

    @Query("DELETE FROM entry_custom_field WHERE entryId = :entryId")
    suspend fun deleteByEntryId(entryId: String)
}
