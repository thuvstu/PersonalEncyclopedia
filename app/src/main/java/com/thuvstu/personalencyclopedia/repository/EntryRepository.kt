package com.thuvstu.personalencyclopedia.repository

import com.thuvstu.personalencyclopedia.db.dao.*
import com.thuvstu.personalencyclopedia.db.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton


data class ThoughtDraft(
    val title: String,
    val content: String?,
    val mood: String? = null,
    val context: String? = null
)

data class DefinitionDraft(
    val term: String,
    val reading: String? = null,
    val definition: String,
    val field: String? = null,
    val examples: List<String> = emptyList()
)

@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val thoughtDao: EntryThoughtDao,
    private val definitionDao: EntryDefinitionDao,
    private val tagDao: TagDao,
    private val entryTypeDao: EntryTypeDao
) {
    // ── Observe ──
    fun observeRecent(limit: Int = 10): Flow<List<EntryEntity>> =
        entryDao.observeRecent(limit)

    fun observeAll(limit: Int = 50, offset: Int = 0): Flow<List<EntryEntity>> =
        entryDao.observeAll(limit, offset)

    fun observeByType(type: String, limit: Int = 50): Flow<List<EntryEntity>> =
        entryDao.observeByType(type, limit)

    fun observeFavorites(): Flow<List<EntryEntity>> =
        entryDao.observeFavorites()

    fun observeEntry(id: String): Flow<EntryEntity?> =
        entryDao.observeById(id)

    fun observeThought(entryId: String): Flow<EntryThoughtEntity?> =
        thoughtDao.observeByEntryId(entryId)

    fun observeDefinition(entryId: String): Flow<EntryDefinitionEntity?> =
        definitionDao.observeByEntryId(entryId)

    fun observeTagsForEntry(entryId: String): Flow<List<TagEntity>> =
        tagDao.observeTagsForEntry(entryId)

    fun observeAllTags(): Flow<List<TagEntity>> =
        tagDao.observeAll()

    fun observeCount(): Flow<Int> = entryDao.observeCount()

    fun search(query: String, limit: Int = 50): Flow<List<EntryEntity>> =
        entryDao.search(query, limit)

    // ── Create ──
    suspend fun createThought(draft: ThoughtDraft): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(
            EntryEntity(
                id = id,
                type = "thought",
                title = draft.title,
                content = draft.content,
                createdAt = now,
                updatedAt = now,
                accessedAt = now
            )
        )
        thoughtDao.insert(
            EntryThoughtEntity(
                entryId = id,
                mood = draft.mood,
                context = draft.context
            )
        )
        return id
    }

    suspend fun createDefinition(draft: DefinitionDraft): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(
            EntryEntity(
                id = id,
                type = "definition",
                title = draft.term,
                content = null,
                createdAt = now,
                updatedAt = now,
                accessedAt = now
            )
        )
        definitionDao.insert(
            EntryDefinitionEntity(
                entryId = id,
                term = draft.term,
                reading = draft.reading,
                definition = draft.definition,
                field = draft.field,
                examplesJson = Json.encodeToString(draft.examples)
            )
        )
        return id
    }

    // ── Update ──
    suspend fun updateThought(entryId: String, draft: ThoughtDraft) {
        val now = System.currentTimeMillis()
        entryDao.getById(entryId)?.let { existing ->
            entryDao.update(
                existing.copy(
                    title = draft.title,
                    content = draft.content,
                    updatedAt = now
                )
            )
        }
        thoughtDao.getByEntryId(entryId)?.let { existing ->
            thoughtDao.update(
                existing.copy(mood = draft.mood, context = draft.context)
            )
        }
    }

    suspend fun updateDefinition(entryId: String, draft: DefinitionDraft) {
        val now = System.currentTimeMillis()
        entryDao.getById(entryId)?.let { existing ->
            entryDao.update(
                existing.copy(
                    title = draft.term,
                    updatedAt = now
                )
            )
        }
        definitionDao.getByEntryId(entryId)?.let { existing ->
            definitionDao.update(
                existing.copy(
                    term = draft.term,
                    reading = draft.reading,
                    definition = draft.definition,
                    field = draft.field,
                    examplesJson = Json.encodeToString(draft.examples)
                )
            )
        }
    }

    // ── Delete / Favorite ──
    suspend fun softDelete(id: String) = entryDao.softDelete(id)
    suspend fun restore(id: String) = entryDao.restore(id)
    suspend fun toggleFavorite(id: String) {
        entryDao.getById(id)?.let {
            entryDao.setFavorite(id, !it.isFavorite)
        }
    }

    suspend fun touch(id: String) = entryDao.touch(id)

    // ── Tags ──
    suspend fun addTag(entryId: String, tagName: String) {
        val existing = tagDao.getByName(tagName)
        val tagId = existing?.id ?: run {
            val newId = UUID.randomUUID().toString()
            tagDao.insert(TagEntity(id = newId, name = tagName))
            newId
        }
        tagDao.linkTag(EntryTagEntity(entryId, tagId))
    }

    suspend fun removeTag(entryId: String, tagId: String) =
        tagDao.unlinkTag(entryId, tagId)
}