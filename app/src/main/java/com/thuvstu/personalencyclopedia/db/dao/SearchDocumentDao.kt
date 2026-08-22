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

    /** Round 0 (M-1): SyntheticDataSeeder用の一括挿入 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(docs: List<SearchDocumentEntity>)

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

    /** Round 0 (M-1): 複数entryIdのrowid一括取得（FTS一括登録用） */
    @Query("SELECT rowid, entryId FROM search_document WHERE entryId IN (:entryIds)")
    suspend fun getRowids(entryIds: List<String>): List<SearchDocRowId>

    @Query("SELECT COUNT(*) FROM search_document")
    fun observeCount(): Flow<Int>

    /**
     * Round 0 (M-1): 合成データ分のFTS行を削除。
     * search_document_ftsは手動rowid同期方式のため、entry CASCADEでは消えないので明示的に消す。
     */
    @Query("""
        DELETE FROM search_document_fts WHERE rowid IN (
            SELECT sd.rowid FROM search_document sd
            INNER JOIN entry e ON e.id = sd.entryId
            WHERE e.metadataJson LIKE '%"synthetic":true%'
        )
    """)
    suspend fun deleteSyntheticFts()
}

/** Round 0 (M-1): getRowidsの投影用 */
data class SearchDocRowId(val rowid: Long, val entryId: String)