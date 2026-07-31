package com.thuvstu.personalencyclopedia.repository

import com.thuvstu.personalencyclopedia.brain.connection.ConnectionEngine
import com.thuvstu.personalencyclopedia.db.dao.ConnectionDao
import com.thuvstu.personalencyclopedia.db.dao.ConnectionWithEntry
import com.thuvstu.personalencyclopedia.db.dao.GraphNode
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.entity.ConnectionCandidateEntity
import com.thuvstu.personalencyclopedia.db.entity.ConnectionTypeDefEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.dao.ConnectionListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton


data class CandidateWithEntries(
    val candidate: ConnectionCandidateEntity,
    val entryA: EntryEntity?,
    val entryB: EntryEntity?
)

@Singleton
class ConnectionRepository @Inject constructor(
    private val connectionDao: ConnectionDao,
    private val entryDao: EntryDao,
    private val connectionEngine: ConnectionEngine
) {
    fun observeTypeDefs(): Flow<List<ConnectionTypeDefEntity>> =
        connectionDao.observeTypeDefs()

    fun observeConnectionsForEntry(entryId: String): Flow<List<ConnectionWithEntry>> =
        connectionDao.observeConnectionsForEntry(entryId)

    fun observePendingCount(): Flow<Int> =
        connectionDao.observePendingCount()

    fun observeAllConnections(): Flow<List<ConnectionListItem>> =
        connectionDao.observeAllConnections()
    suspend fun getPendingCandidatesWithEntries(): List<CandidateWithEntries> {
        val candidates = connectionDao.observePendingCandidates().first()
        return candidates.map { c ->
            CandidateWithEntries(
                candidate = c,
                entryA = entryDao.getById(c.entryAId),
                entryB = entryDao.getById(c.entryBId)
            )
        }
    }

    suspend fun createManualConnection(
        entryAId: String,
        entryBId: String,
        relationType: String,
        strength: Float = 0.5f,
        note: String? = null
    ): String? = connectionEngine.createManualConnection(
        entryAId, entryBId, relationType, strength = strength, note = note
    )
    suspend fun approveCandidate(candidateId: String, relationType: String? = null): String? =
        connectionEngine.approveCandidate(candidateId, relationType)

    suspend fun rejectCandidate(candidateId: String) =
        connectionEngine.rejectCandidate(candidateId)

    suspend fun removeConnection(connectionId: String) =
        connectionEngine.removeConnection(connectionId)

    suspend fun traverseGraph(entryId: String, maxDepth: Int = 3): List<GraphNode> =
        connectionDao.traverseGraph(entryId, maxDepth)

    suspend fun getRelatedEntries(entryId: String, limit: Int = 5): List<EntryEntity> {
        val connections = connectionDao.observeConnectionsForEntry(entryId).first()
        return connections
            .sortedByDescending { it.strength }
            .take(limit)
            .mapNotNull { entryDao.getById(it.otherEntryId) }
    }
}