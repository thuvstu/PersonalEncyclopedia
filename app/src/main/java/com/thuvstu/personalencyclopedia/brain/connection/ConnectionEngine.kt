package com.thuvstu.personalencyclopedia.brain.connection

import android.util.Log
import com.thuvstu.personalencyclopedia.brain.search.InMemoryVectorIndex
import com.thuvstu.personalencyclopedia.db.dao.ConnectionDao
import com.thuvstu.personalencyclopedia.db.entity.ConnectionCandidateEntity
import com.thuvstu.personalencyclopedia.db.entity.ConnectionEntity
import com.thuvstu.personalencyclopedia.db.entity.ConnectionTypeDefEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionEngine @Inject constructor(
    private val connectionDao: ConnectionDao,
    private val vectorIndex: InMemoryVectorIndex
) {
    companion object {
        private const val TAG = "ConnectionEngine"
        private const val DEFAULT_THRESHOLD = 0.88f
    }

    var autoConnectEnabled = false
    var autoConnectThreshold = DEFAULT_THRESHOLD

    suspend fun seedTypeDefs() {
        connectionDao.insertTypeDefs(
            listOf(
                ConnectionTypeDefEntity("related", "関連", isDirected = false),
                ConnectionTypeDefEntity("references", "参照", isDirected = true, inverseLabelJa = "被参照"),
                ConnectionTypeDefEntity("contradicts", "矛盾", isDirected = false),
                ConnectionTypeDefEntity("extends", "拡張", isDirected = true, inverseLabelJa = "被拡張"),
                ConnectionTypeDefEntity("exemplifies", "例示", isDirected = true, inverseLabelJa = "被例示"),
                ConnectionTypeDefEntity("authored_by", "著者", isDirected = true, inverseLabelJa = "著作"),
                ConnectionTypeDefEntity("published_by", "発行者", isDirected = true, inverseLabelJa = "発行物"),
                ConnectionTypeDefEntity("located_at", "所在地", isDirected = true, inverseLabelJa = "所在先"),
                ConnectionTypeDefEntity("occurred_at", "発生地", isDirected = true, inverseLabelJa = "発生元"),
            )
        )
    }

    suspend fun generateCandidatesForEntry(entryId: String, entryVector: FloatArray): Int {
        if (!autoConnectEnabled) return 0
        if (!vectorIndex.isLoaded()) return 0

        val similar = vectorIndex.topK(entryVector, k = 10)
        var created = 0

        for ((candidateId, similarity) in similar) {
            if (candidateId == entryId) continue
            if (similarity < autoConnectThreshold) continue

            val existing = connectionDao.countCandidatePair(entryId, candidateId) +
                    connectionDao.countCandidatePair(candidateId, entryId)
            if (existing > 0) continue

            connectionDao.insertCandidate(
                ConnectionCandidateEntity(
                    entryAId = entryId,
                    entryBId = candidateId,
                    similarity = similarity,
                    suggestedType = "related"
                )
            )
            created++
        }

        if (created > 0) Log.i(TAG, "Generated $created candidates for $entryId")
        return created
    }

    suspend fun createManualConnection(
        entryAId: String,
        entryBId: String,
        relationType: String,
        strength: Float = 0.5f,
        note: String? = null
    ): String? {
        if (entryAId == entryBId) return null

        val typeDef = connectionDao.getTypeDef(relationType) ?: return null

        val canonicalA = if (entryAId < entryBId) entryAId else entryBId
        val canonicalB = if (entryAId < entryBId) entryBId else entryAId

        val isDuplicate = if (typeDef.isDirected) {
            connectionDao.countDirectedDuplicate(entryAId, entryBId, relationType) > 0
        } else {
            connectionDao.countUndirectedDuplicate(canonicalA, canonicalB, relationType) > 0
        }
        if (isDuplicate) return null

        val connection = ConnectionEntity(
            entryAId = entryAId,
            entryBId = entryBId,
            relationType = relationType,
            strength = strength,
            note = note,
            isAuto = false,
            isDirected = typeDef.isDirected,
            canonicalA = canonicalA,
            canonicalB = canonicalB
        )
        connectionDao.insert(connection)
        return connection.id
    }

    suspend fun approveCandidate(candidateId: String, relationType: String? = null): String? {
        val candidate = connectionDao.getCandidateById(candidateId) ?: return null
        val type = relationType ?: candidate.suggestedType
        val connectionId = createManualConnection(
            entryAId = candidate.entryAId,
            entryBId = candidate.entryBId,
            relationType = type,
            strength = candidate.similarity
        ) ?: return null
        connectionDao.updateCandidateStatus(
            id = candidateId, status = "approved", connectionId = connectionId
        )
        return connectionId
    }

    suspend fun rejectCandidate(candidateId: String) {
        connectionDao.updateCandidateStatus(id = candidateId, status = "rejected")
    }

    suspend fun removeConnection(connectionId: String) {
        connectionDao.delete(connectionId)
    }
}