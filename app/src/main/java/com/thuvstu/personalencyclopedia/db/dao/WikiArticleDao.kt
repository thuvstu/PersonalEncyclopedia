package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.*
import com.thuvstu.personalencyclopedia.db.entity.WikiArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WikiArticleDao {
    @Query("SELECT * FROM wiki_article ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WikiArticleEntity>>

    @Query("SELECT * FROM wiki_article WHERE id = :id")
    fun observeById(id: String): Flow<WikiArticleEntity?>

    @Query("SELECT * FROM wiki_article WHERE title = :title LIMIT 1")
    suspend fun findByTitle(title: String): WikiArticleEntity?

    @Query("""
        SELECT * FROM wiki_article
        WHERE title LIKE '%' || :q || '%' OR contentMd LIKE '%' || :q || '%'
        ORDER BY updatedAt DESC
    """)
    suspend fun search(q: String): List<WikiArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(article: WikiArticleEntity)

    @Query("DELETE FROM wiki_article WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM wiki_article")
    fun observeCount(): Flow<Int>
}