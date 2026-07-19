package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.*
import com.thuvstu.personalencyclopedia.db.entity.EntryTopicEntity
import com.thuvstu.personalencyclopedia.db.entity.TopicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(topic: TopicEntity): Long

    @Query("SELECT * FROM topic WHERE parentId IS NULL ORDER BY name")
    fun observeGenres(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topic WHERE parentId = :parentId ORDER BY name")
    fun observeFieldsByGenre(parentId: String): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topic WHERE name = :name AND parentId IS :parentId LIMIT 1")
    suspend fun findByName(name: String, parentId: String?): TopicEntity?

    @Query("""
        SELECT t.* FROM topic t
        INNER JOIN entry_topic et ON et.topicId = t.id
        WHERE et.entryId = :entryId
    """)
    fun observeTopicsForEntry(entryId: String): Flow<List<TopicEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkEntryTopic(link: EntryTopicEntity)

    @Query("DELETE FROM entry_topic WHERE entryId = :entryId AND topicId = :topicId")
    suspend fun unlinkEntryTopic(entryId: String, topicId: String)
}