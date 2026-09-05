package com.thuvstu.personalencyclopedia.brain.ai

import android.util.Log
import com.thuvstu.personalencyclopedia.brain.search.EmbeddingTextBuilder
import com.thuvstu.personalencyclopedia.brain.search.InMemoryVectorIndex
import com.thuvstu.personalencyclopedia.brain.search.NgramTokenizer
import com.thuvstu.personalencyclopedia.db.dao.EmbeddingDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryExtensionDao
import com.thuvstu.personalencyclopedia.db.dao.SearchDocumentDao
import com.thuvstu.personalencyclopedia.db.entity.EmbeddingEntity
import com.thuvstu.personalencyclopedia.db.entity.EmbeddingJobEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.SearchDocumentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddingQueue @Inject constructor(
    private val embeddingDao: EmbeddingDao,
    private val entryDao: EntryDao,
    private val extensionDao: EntryExtensionDao,
    private val definitionDao: EntryDefinitionDao,
    private val searchDocumentDao: SearchDocumentDao,
    private val geminiClient: GeminiClient,
    private val vectorIndex: InMemoryVectorIndex
) {
    companion object {
        private const val TAG = "EmbeddingQueue"
        private const val MAX_ATTEMPTS = 3
    }

    private val channel = Channel<String>(Channel.UNLIMITED)
    private var workerJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun enqueue(entryId: String) {
        updateSearchDocument(entryId)

        if (!geminiClient.isConfigured()) return

        val existing = embeddingDao.getJob(entryId)
        if (existing != null && existing.status == "done") {
            val currentDoc = searchDocumentDao.getByEntryId(entryId)
            val currentEmbedding = embeddingDao.getByEntryId(entryId)
            if (currentDoc != null && currentEmbedding != null &&
                currentDoc.combinedText == currentEmbedding.inputText
            ) {
                return
            }
        }

        embeddingDao.upsertJob(
            EmbeddingJobEntity(
                entryId = entryId,
                status = "queued",
                attempts = existing?.attempts ?: 0,
                queuedAt = System.currentTimeMillis()
            )
        )
        channel.send(entryId)
    }

    suspend fun updateSearchDocument(entryId: String) {
        val entry = entryDao.getById(entryId) ?: return

        if (entry.deletedAt != null) {
            val rowid = searchDocumentDao.getRowid(entryId)
            if (rowid != null) {
                searchDocumentDao.deleteFts(rowid)
            }
            searchDocumentDao.deleteByEntryId(entryId)
            return
        }

        val extension: Any? = when (entry.type) {
            "webpage" -> extensionDao.getWebpage(entryId)
            "book" -> extensionDao.getBook(entryId)
            "video" -> extensionDao.getVideo(entryId)
            "document" -> extensionDao.getDocument(entryId)
            "media" -> extensionDao.getMedia(entryId)
            "person" -> extensionDao.getPerson(entryId)
            "org" -> extensionDao.getOrg(entryId)
            "place" -> extensionDao.getPlace(entryId)
            "event" -> extensionDao.getEvent(entryId)
            "liked" -> extensionDao.getLiked(entryId)
            "ai_conv" -> extensionDao.getAiConv(entryId)
            "definition" -> definitionDao.getByEntryId(entryId)
            "thought" -> null
            else -> null
        }

        val combinedText = EmbeddingTextBuilder.build(entry, extension)
        if (combinedText.isBlank()) return

        // ★FTS差分: 内容不変ならFTSの delete+insert をスキップする（enqueue経路の冪等化）
        val existingDoc = searchDocumentDao.getByEntryId(entryId)
        if (existingDoc != null && existingDoc.combinedText == combinedText) return

        val existingRowid = searchDocumentDao.getRowid(entryId)
        if (existingRowid != null) {
            searchDocumentDao.deleteFts(existingRowid)
        }

        searchDocumentDao.upsert(
            SearchDocumentEntity(
                entryId = entryId,
                combinedText = combinedText,
                lang = entry.lang ?: "ja",
                updatedAt = System.currentTimeMillis()
            )
        )

        val newRowid = searchDocumentDao.getRowid(entryId)
        if (newRowid != null) {
            val ngramText = NgramTokenizer.tokenize(combinedText)
            searchDocumentDao.insertFts(newRowid, ngramText)
        }
    }

    fun startWorker() {
        if (workerJob?.isActive == true) return
        workerJob = scope.launch {
            for (entryId in channel) {
                processEmbedding(entryId)
            }
        }
    }

    private suspend fun processEmbedding(entryId: String) {
        val job = embeddingDao.getJob(entryId) ?: return
        if (job.attempts >= MAX_ATTEMPTS) {
            embeddingDao.updateJobStatus(entryId, "failed", job.attempts, "Max attempts reached")
            return
        }

        embeddingDao.updateJobStatus(entryId, "running", job.attempts)

        try {
            val doc = searchDocumentDao.getByEntryId(entryId)
            if (doc == null) {
                embeddingDao.updateJobStatus(
                    entryId,
                    "failed",
                    job.attempts + 1,
                    "No search document"
                )
                return
            }

            val vector = geminiClient.embed(doc.combinedText)
            if (vector == null) {
                embeddingDao.updateJobStatus(
                    entryId,
                    "failed",
                    job.attempts + 1,
                    "API returned null"
                )
                return
            }

            embeddingDao.upsert(
                EmbeddingEntity(
                    id = UUID.randomUUID().toString(),
                    entryId = entryId,
                    vectorBlob = vector.toBlob(),
                    inputText = doc.combinedText,
                    createdAt = System.currentTimeMillis()
                )
            )

            vectorIndex.addVector(entryId, vector)

            embeddingDao.updateJobStatus(
                entryId, "done", job.attempts + 1,
                doneAt = System.currentTimeMillis()
            )

            Log.d(TAG, "Embedded: $entryId (${vector.size}d)")
        } catch (e: Exception) {
            Log.e(TAG, "Embedding failed for $entryId", e)
            embeddingDao.updateJobStatus(entryId, "failed", job.attempts + 1, e.message)
        }
    }

    suspend fun recoverJobs() {
        val pending = embeddingDao.getPendingJobs()
        if (pending.isEmpty()) return

        Log.i(TAG, "Recovering ${pending.size} embedding jobs")
        for (job in pending) {
            val attempts = if (job.status == "running") job.attempts + 1 else job.attempts
            if (attempts >= MAX_ATTEMPTS) {
                embeddingDao.updateJobStatus(
                    job.entryId,
                    "failed",
                    attempts,
                    "Recovery: max attempts"
                )
            } else {
                embeddingDao.upsertJob(job.copy(status = "queued", attempts = attempts))
                channel.send(job.entryId)
            }
        }
    }

    /**
     * ★FTS差分: entry.updatedAt <= search_document.updatedAt の文書は読み飛ばす。
     * 起動時の全件FTS再構築（逆スケール問題）を、変更分のみの更新に変える。
     * 削除済み・文書なし・本文空は updateSearchDocument に委ねる（掃除のためスキップしない）。
     */
    suspend fun rebuildAllSearchDocuments() {
        var offset = 0
        val batchSize = 100
        var updated = 0
        var skipped = 0
        while (true) {
            val entries = entryDao.getAllPaged(batchSize, offset)
            if (entries.isEmpty()) break
            for (e in entries) {
                if (isSearchDocumentFresh(e)) {
                    skipped++
                    continue
                }
                updateSearchDocument(e.id)
                updated++
            }
            offset += batchSize
        }
        Log.i(TAG, "Rebuilt search documents: updated=$updated skipped=$skipped")
    }

    private suspend fun isSearchDocumentFresh(entry: EntryEntity): Boolean {
        if (entry.deletedAt != null) return false
        val doc = searchDocumentDao.getByEntryId(entry.id) ?: return false
        if (doc.combinedText.isBlank()) return false
        return entry.updatedAt <= doc.updatedAt
    }
}