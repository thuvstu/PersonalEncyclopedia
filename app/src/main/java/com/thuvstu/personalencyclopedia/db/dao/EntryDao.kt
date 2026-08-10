package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Insert
    suspend fun insert(entry: EntryEntity)

    @Update
    suspend fun update(entry: EntryEntity)

    @Query("UPDATE entry SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE entry SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE entry SET isFavorite = :fav, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE entry SET accessedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun getById(id: String): EntryEntity?

    @Query("SELECT * FROM entry WHERE id = :id")
    fun observeById(id: String): Flow<EntryEntity?>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun observeAll(limit: Int = 50, offset: Int = 0): Flow<List<EntryEntity>>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL AND type = :type
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun observeByType(type: String, limit: Int = 50, offset: Int = 0): Flow<List<EntryEntity>>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL
          AND (title LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%')
        ORDER BY
          CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END,
          createdAt DESC
        LIMIT :limit
    """)
    fun search(q: String, limit: Int = 50): Flow<List<EntryEntity>>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL AND isFavorite = 1
        ORDER BY createdAt DESC
    """)
    fun observeFavorites(): Flow<List<EntryEntity>>

    @Query("SELECT COUNT(*) FROM entry WHERE deletedAt IS NULL")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM entry WHERE deletedAt IS NULL AND type = :type")
    fun observeCountByType(type: String): Flow<Int>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int = 10): Flow<List<EntryEntity>>

    @Query("""
SELECT type, COUNT(*) AS cnt FROM entry
WHERE deletedAt IS NULL GROUP BY type ORDER BY cnt DESC
""")

    fun observeCountsByType(): Flow<List<TypeCount>>

    @Query("SELECT * FROM entry WHERE deletedAt IS NULL ORDER BY createdAt LIMIT :limit OFFSET :offset")
    suspend fun getAllPaged(limit: Int, offset: Int): List<EntryEntity>

    @Query("SELECT * FROM entry WHERE title = :title AND deletedAt IS NULL LIMIT 1")
    suspend fun findByTitle(title: String): EntryEntity?

    @Query("SELECT * FROM entry WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<EntryEntity>

    @Query("SELECT * FROM entry WHERE sourceUrl = :url AND deletedAt IS NULL LIMIT 1")
    suspend fun findBySourceUrl(url: String): EntryEntity?
}

data class TypeCount(val type: String, val cnt: Int)
