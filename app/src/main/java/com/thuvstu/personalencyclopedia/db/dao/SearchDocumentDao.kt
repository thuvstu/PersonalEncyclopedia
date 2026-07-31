package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thuvstu.personalencyclopedia.db.entity.SearchDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: SearchDocumentEntity)

    @Query("SELECT * FROM search_document WHERE entryId = :entryId")
    suspend fun getByEntryId(entryId: String): SearchDocumentEntity?

    @Query("DELETE FROM search_document WHERE entryId = :entryId")
    suspend fun deleteByEntryId(entryId: String)

    @Query("""
        SELECT sd.entryId
        FROM search_document_fts fts
        INNER JOIN search_document sd ON sd.rowid = fts.rowid
        WHERE search_document_fts MATCH :query
        LIMIT :limit
    """)
    suspend fun ftsSearch(query: String, limit: Int = 50): List<String>

    @Query("INSERT INTO search_document_fts(rowid, ftsContent) VALUES (:rowid, :content)")
    suspend fun insertFts(rowid: Long, content: String)

    @Query("DELETE FROM search_document_fts WHERE rowid = :rowid")
    suspend fun deleteFts(rowid: Long)

    @Query("SELECT rowid FROM search_document WHERE entryId = :entryId")
    suspend fun getRowid(entryId: String): Long?

    @Query("SELECT COUNT(*) FROM search_document")
    fun observeCount(): Flow<Int>
}