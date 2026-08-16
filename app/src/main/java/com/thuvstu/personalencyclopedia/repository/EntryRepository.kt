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
    private val extensionDao: EntryExtensionDao,
    private val tagDao: TagDao,
    private val entryTypeDao: EntryTypeDao,
    private val entryHistoryDao: EntryHistoryDao,
    private val embeddingQueue: com.thuvstu.personalencyclopedia.brain.ai.EmbeddingQueue
) {

    /**
     * §5.9.2: 編集履歴のスナップショットを記録する（entry.content のみ対象）。
     * prev == null の場合は作成時（charCountDelta = 本文文字数）。
     */
    private suspend fun recordHistory(
        entryId: String,
        prev: EntryEntity?,
        newTitle: String,
        newContent: String?
    ) {
        val prevLen = prev?.content?.length ?: 0
        val newLen = newContent?.length ?: 0
        entryHistoryDao.insert(
            EntryHistoryEntity(
                entryId = entryId,
                titleSnapshot = newTitle,
                contentSnapshot = newContent,
                changeSummary = "",
                charCountDelta = newLen - prevLen
            )
        )
    }
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

    suspend fun getEntry(id: String): EntryEntity? = entryDao.getById(id)

    fun observeThought(entryId: String): Flow<EntryThoughtEntity?> =
        thoughtDao.observeByEntryId(entryId)

    fun observeDefinition(entryId: String): Flow<EntryDefinitionEntity?> =
        definitionDao.observeByEntryId(entryId)

    suspend fun getWebpage(entryId: String) = extensionDao.getWebpage(entryId)
    suspend fun getBook(entryId: String) = extensionDao.getBook(entryId)
    suspend fun getVideo(entryId: String) = extensionDao.getVideo(entryId)
    suspend fun getDocument(entryId: String) = extensionDao.getDocument(entryId)
    suspend fun getMedia(entryId: String) = extensionDao.getMedia(entryId)
    suspend fun getPerson(entryId: String) = extensionDao.getPerson(entryId)
    suspend fun getOrg(entryId: String) = extensionDao.getOrg(entryId)
    suspend fun getPlace(entryId: String) = extensionDao.getPlace(entryId)
    suspend fun getEvent(entryId: String) = extensionDao.getEvent(entryId)
    suspend fun getLiked(entryId: String) = extensionDao.getLiked(entryId)
    suspend fun getAiConv(entryId: String) = extensionDao.getAiConv(entryId)

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

        // §5.9.2: 編集履歴（作成時スナップショット）
        recordHistory(id, null, draft.title, draft.content)

        // ★ Phase 2: 検索インデックス更新 + Embeddingキュー投入
        embeddingQueue.enqueue(id)

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

        // §5.9.2: 編集履歴（作成時スナップショット）
        recordHistory(id, null, draft.term, null)

        // ★ Phase 2: 検索インデックス更新 + Embeddingキュー投入
        embeddingQueue.enqueue(id)

        return id
    }

    suspend fun createWebpage(title: String, content: String?, url: String, author: String? = null, fullText: String? = null): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val domain = try { java.net.URI(url).host ?: "" } catch (e: Exception) { "" }
        entryDao.insert(EntryEntity(id = id, type = "webpage", title = title, content = content, sourceUrl = url, createdAt = now, updatedAt = now, accessedAt = now))
        extensionDao.insertWebpage(EntryWebpageEntity(entryId = id, url = url, domain = domain, scrapedAt = now, fullText = fullText, author = author))
        embeddingQueue.enqueue(id)
        return id
    }

    suspend fun createBook(title: String, content: String?, isbn: String?, authors: List<String>, publisher: String?, year: Int?, pages: Int?, status: String = "unread", rating: Int? = null): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(EntryEntity(id = id, type = "book", title = title, content = content, createdAt = now, updatedAt = now, accessedAt = now))
        extensionDao.insertBook(EntryBookEntity(entryId = id, isbn = isbn, authorsJson = Json.encodeToString(authors), publisher = publisher, publishedYear = year, totalPages = pages, readStatus = status, rating = rating))
        embeddingQueue.enqueue(id)
        return id
    }

    suspend fun createVideo(title: String, content: String?, platform: String, videoId: String?, channelName: String?, durationS: Int?, transcript: String?): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(EntryEntity(id = id, type = "video", title = title, content = content, createdAt = now, updatedAt = now, accessedAt = now))
        extensionDao.insertVideo(EntryVideoEntity(entryId = id, platform = platform, videoId = videoId, channelName = channelName, durationS = durationS, transcript = transcript))
        embeddingQueue.enqueue(id)
        return id
    }

    suspend fun createDocument(title: String, content: String?, docType: String, mimeType: String, sizeBytes: Long? = null, pages: Int? = null, extractedText: String? = null): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(EntryEntity(id = id, type = "document", title = title, content = content, createdAt = now, updatedAt = now, accessedAt = now))
        extensionDao.insertDocument(EntryDocumentEntity(entryId = id, docType = docType, mimeType = mimeType, fileSizeBytes = sizeBytes, pageCount = pages, extractedText = extractedText))
        embeddingQueue.enqueue(id)
        return id
    }

    suspend fun createMedia(title: String, content: String?, mediaType: String, blobPath: String, mimeType: String, ocrText: String? = null, caption: String? = null): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(EntryEntity(id = id, type = "media", title = title, content = content, createdAt = now, updatedAt = now, accessedAt = now))
        extensionDao.insertMedia(EntryMediaEntity(entryId = id, mediaType = mediaType, blobPath = blobPath, mimeType = mimeType, ocrText = ocrText, caption = caption))
        embeddingQueue.enqueue(id)
        return id
    }

    suspend fun createPerson(title: String, content: String?, fullName: String, aliases: List<String> = emptyList(), birthYear: Int? = null, deathYear: Int? = null, nationality: String? = null, occupations: List<String> = emptyList(), biography: String? = null): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(EntryEntity(id = id, type = "person", title = title, content = content, createdAt = now, updatedAt = now, accessedAt = now))
        extensionDao.insertPerson(EntryPersonEntity(entryId = id, fullName = fullName, aliasesJson = Json.encodeToString(aliases), birthYear = birthYear, deathYear = deathYear, nationality = nationality, occupationsJson = Json.encodeToString(occupations), biography = biography))
        embeddingQueue.enqueue(id)
        return id
    }

    suspend fun createOrg(title: String, content: String?, officialName: String, orgType: String? = null, foundedYear: Int? = null, country: String? = null, websiteUrl: String? = null, description: String? = null): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(EntryEntity(id = id, type = "org", title = title, content = content, sourceUrl = websiteUrl, createdAt = now, updatedAt = now, accessedAt = now))
        extensionDao.insertOrg(EntryOrgEntity(entryId = id, officialName = officialName, orgType = orgType, foundedYear = foundedYear, country = country, websiteUrl = websiteUrl, description = description))
        embeddingQueue.enqueue(id)
        return id
    }

    suspend fun createPlace(title: String, content: String?, placeName: String, placeType: String? = null, address: String? = null, latitude: Double? = null, longitude: Double? = null): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(EntryEntity(id = id, type = "place", title = title, content = content, createdAt = now, updatedAt = now, accessedAt = now))
        extensionDao.insertPlace(EntryPlaceEntity(entryId = id, placeName = placeName, placeType = placeType, address = address, latitude = latitude, longitude = longitude))
        embeddingQueue.enqueue(id)
        return id
    }

    suspend fun createEvent(title: String, content: String?, eventName: String, startedAt: Long, endedAt: Long? = null, locationText: String? = null, isPersonal: Boolean = true, participants: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(EntryEntity(id = id, type = "event", title = title, content = content, createdAt = now, updatedAt = now, accessedAt = now))
        extensionDao.insertEvent(EntryEventEntity(entryId = id, eventName = eventName, startedAt = startedAt, endedAt = endedAt, locationText = locationText, isPersonal = isPersonal, participantsJson = Json.encodeToString(participants)))
        embeddingQueue.enqueue(id)
        return id
    }

    suspend fun createLiked(title: String, content: String?, platform: String, originalId: String, contentType: String, authorName: String? = null, fullText: String? = null): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(EntryEntity(id = id, type = "liked", title = title, content = content, createdAt = now, updatedAt = now, accessedAt = now))
        extensionDao.insertLiked(EntryLikedEntity(entryId = id, platform = platform, originalId = originalId, contentType = contentType, authorName = authorName, fullText = fullText))
        embeddingQueue.enqueue(id)
        return id
    }

    suspend fun createAiConv(title: String, content: String?, model: String, provider: String, topic: String? = null, isUseful: Boolean? = null, messagesJson: String = "[]"): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(EntryEntity(id = id, type = "ai_conv", title = title, content = content, createdAt = now, updatedAt = now, accessedAt = now))
        extensionDao.insertAiConv(EntryAiConvEntity(entryId = id, model = model, provider = provider, topic = topic, isUseful = isUseful, messagesJson = messagesJson))
        embeddingQueue.enqueue(id)
        return id
    }

    // ── Update ──
    suspend fun updateThought(entryId: String, draft: ThoughtDraft) {
        val now = System.currentTimeMillis()
        val prev = entryDao.getById(entryId)
        prev?.let { existing ->
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

        // §5.9.2: 編集履歴（前回スナップショットとの差分）
        recordHistory(entryId, prev, draft.title, draft.content)

        // ★ Phase 2: 更新後にインデックス再構築
        embeddingQueue.enqueue(entryId)
    }

    suspend fun updateDefinition(entryId: String, draft: DefinitionDraft) {
        val now = System.currentTimeMillis()
        val prev = entryDao.getById(entryId)
        prev?.let { existing ->
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

        // §5.9.2: 編集履歴（前回スナップショットとの差分）
        recordHistory(entryId, prev, draft.term, null)

        // ★ Phase 2: 更新後にインデックス再構築
        embeddingQueue.enqueue(entryId)
    }

    // ── Delete / Favorite ──
    suspend fun softDelete(id: String) {
        entryDao.softDelete(id)
        // ★ Phase 2: 削除時は検索インデックスからも除去
        embeddingQueue.updateSearchDocument(id)  // deletedAt != null なので削除される
    }

    suspend fun restore(id: String) {
        entryDao.restore(id)
        embeddingQueue.enqueue(id)  // 復元時は再インデックス
    }

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

    suspend fun updateEntryCommon(entryId: String, title: String, content: String?) {
        val existing = entryDao.getById(entryId) ?: return
        entryDao.update(existing.copy(
            title = title, content = content, updatedAt = System.currentTimeMillis()))
        // §5.9.2: 編集履歴（前回スナップショットとの差分）
        recordHistory(entryId, existing, title, content)
        embeddingQueue.enqueue(entryId)
    }

    suspend fun upsertExtension(entity: Any) {
        val entryId = when (entity) {
            is EntryWebpageEntity -> { extensionDao.insertWebpage(entity); entity.entryId }
            is EntryBookEntity -> { extensionDao.insertBook(entity); entity.entryId }
            is EntryVideoEntity -> { extensionDao.insertVideo(entity); entity.entryId }
            is EntryDocumentEntity -> { extensionDao.insertDocument(entity); entity.entryId }
            is EntryMediaEntity -> { extensionDao.insertMedia(entity); entity.entryId }
            is EntryPersonEntity -> { extensionDao.insertPerson(entity); entity.entryId }
            is EntryOrgEntity -> { extensionDao.insertOrg(entity); entity.entryId }
            is EntryPlaceEntity -> { extensionDao.insertPlace(entity); entity.entryId }
            is EntryEventEntity -> { extensionDao.insertEvent(entity); entity.entryId }
            is EntryLikedEntity -> { extensionDao.insertLiked(entity); entity.entryId }
            is EntryAiConvEntity -> { extensionDao.insertAiConv(entity); entity.entryId }
            else -> return
        }
        embeddingQueue.enqueue(entryId)  // search_document + Embedding 再生成キューへ
    }

    suspend fun findByTitle(title: String): EntryEntity? = entryDao.findByTitle(title)
}