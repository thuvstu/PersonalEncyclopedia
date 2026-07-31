package com.thuvstu.personalencyclopedia.db.dao

import androidx.room.*
import com.thuvstu.personalencyclopedia.db.entity.ConnectionCandidateEntity
import com.thuvstu.personalencyclopedia.db.entity.ConnectionEntity
import com.thuvstu.personalencyclopedia.db.entity.ConnectionTypeDefEntity
import kotlinx.coroutines.flow.Flow

data class ConnectionWithEntry(
    val connectionId: String,
    val relationType: String,
    val strength: Float,
    val note: String?,
    val isDirected: Boolean,
    val otherEntryId: String,
    val otherEntryTitle: String,
    val otherEntryType: String
)

data class GraphNode(
    val src: String,
    val dst: String,
    val relationType: String,
    val strength: Float,
    val depth: Int
)

@Dao
interface ConnectionDao {

    // ── Type definitions ──
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTypeDefs(types: List<ConnectionTypeDefEntity>)

    @Query("SELECT * FROM connection_type_def ORDER BY name")
    fun observeTypeDefs(): Flow<List<ConnectionTypeDefEntity>>

    @Query("SELECT * FROM connection_type_def WHERE name = :name")
    suspend fun getTypeDef(name: String): ConnectionTypeDefEntity?

    // ── Connections ──
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(connection: ConnectionEntity): Long

    @Query("DELETE FROM connection WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM connection WHERE id = :id")
    suspend fun getById(id: String): ConnectionEntity?

    @Query("SELECT COUNT(*) FROM connection")
    fun observeCount(): Flow<Int>

    // Connections for an entry (both directions), joined with the other entry's info
    @Query("""
        SELECT c.id AS connectionId, c.relationType AS relationType, c.strength AS strength,
               c.note AS note, c.isDirected AS isDirected,
               e.id AS otherEntryId, e.title AS otherEntryTitle, e.type AS otherEntryType
        FROM connection c
        INNER JOIN entry e ON e.id = c.entryBId
        WHERE c.entryAId = :entryId AND e.deletedAt IS NULL
        UNION ALL
        SELECT c.id AS connectionId, c.relationType AS relationType, c.strength AS strength,
               c.note AS note, c.isDirected AS isDirected,
               e.id AS otherEntryId, e.title AS otherEntryTitle, e.type AS otherEntryType
        FROM connection c
        INNER JOIN entry e ON e.id = c.entryAId
        WHERE c.entryBId = :entryId AND e.deletedAt IS NULL
    """)
    fun observeConnectionsForEntry(entryId: String): Flow<List<ConnectionWithEntry>>

    // Duplicate check for undirected relations
    @Query("""
        SELECT COUNT(*) FROM connection
        WHERE canonicalA = :canonicalA AND canonicalB = :canonicalB AND relationType = :relationType
    """)
    suspend fun countUndirectedDuplicate(canonicalA: String, canonicalB: String, relationType: String): Int

    // Duplicate check for directed relations
    @Query("""
        SELECT COUNT(*) FROM connection
        WHERE entryAId = :a AND entryBId = :b AND relationType = :relationType
    """)
    suspend fun countDirectedDuplicate(a: String, b: String, relationType: String): Int

    // ── Graph traversal (WITH RECURSIVE) ──
    @Query("""
        WITH RECURSIVE graph AS (
            SELECT entryAId AS src, entryBId AS dst, relationType, strength, 1 AS depth
            FROM connection WHERE entryAId = :entryId OR entryBId = :entryId
            UNION ALL
            SELECT c.entryAId, c.entryBId, c.relationType, c.strength, g.depth + 1
            FROM connection c
            JOIN graph g ON (c.entryAId = g.dst OR c.entryBId = g.dst)
            WHERE g.depth < :maxDepth
        )
        SELECT DISTINCT src, dst, relationType, strength, depth FROM graph LIMIT :maxNodes
    """)
    suspend fun traverseGraph(entryId: String, maxDepth: Int = 3, maxNodes: Int = 100): List<GraphNode>

    // ── Candidates ──
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCandidate(candidate: ConnectionCandidateEntity): Long

    @Query("SELECT * FROM connection_candidate WHERE status = 'pending' ORDER BY similarity DESC")
    fun observePendingCandidates(): Flow<List<ConnectionCandidateEntity>>

    @Query("SELECT COUNT(*) FROM connection_candidate WHERE status = 'pending'")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE connection_candidate SET status = :status, connectionId = :connectionId, reviewedAt = :reviewedAt WHERE id = :id")
    suspend fun updateCandidateStatus(id: String, status: String, connectionId: String? = null, reviewedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM connection_candidate WHERE entryAId = :a AND entryBId = :b")
    suspend fun countCandidatePair(a: String, b: String): Int

    @Query("""
SELECT c.id AS connectionId, c.relationType AS relationType, c.strength AS strength,
       c.note AS note, c.isDirected AS isDirected, c.createdAt AS createdAt,
       ea.id AS entryAId, ea.title AS entryATitle, ea.type AS entryAType,
       eb.id AS entryBId, eb.title AS entryBTitle, eb.type AS entryBType
FROM connection c
INNER JOIN entry ea ON ea.id = c.entryAId
INNER JOIN entry eb ON eb.id = c.entryBId
WHERE ea.deletedAt IS NULL AND eb.deletedAt IS NULL
ORDER BY c.createdAt DESC
""")
    fun observeAllConnections(): Flow<List<ConnectionListItem>>

    @Query("SELECT * FROM connection_candidate WHERE id = :id")
    suspend fun getCandidateById(id: String): ConnectionCandidateEntity?

}

data class ConnectionListItem(
    val connectionId: String,
    val relationType: String,
    val strength: Float,
    val note: String?,
    val isDirected: Boolean,
    val createdAt: Long,
    val entryAId: String,
    val entryATitle: String,
    val entryAType: String,
    val entryBId: String,
    val entryBTitle: String,
    val entryBType: String
)
