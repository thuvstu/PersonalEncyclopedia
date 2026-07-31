package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.thuvstu.personalencyclopedia.db.entity.EntryAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryAttachmentDao {
    @Insert
    suspend fun insert(attachment: EntryAttachmentEntity)

    @Query("SELECT * FROM entry_attachment WHERE entryId = :entryId ORDER BY sortOrder, createdAt")
    fun observeForEntry(entryId: String): Flow<List<EntryAttachmentEntity>>

    @Query("SELECT * FROM entry_attachment WHERE id = :id")
    suspend fun getById(id: String): EntryAttachmentEntity?

    @Query("DELETE FROM entry_attachment WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM entry_attachment WHERE entryId = :entryId")
    suspend fun countForEntry(entryId: String): Int
}