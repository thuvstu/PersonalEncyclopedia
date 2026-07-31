package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.*
import java.util.UUID

@Entity(tableName = "connection_type_def")
data class ConnectionTypeDefEntity(
    @PrimaryKey val name: String,
    val labelJa: String,
    val isDirected: Boolean,
    val inverseLabelJa: String? = null
)

@Entity(
    tableName = "connection",
    indices = [
        Index("entryAId"),
        Index("entryBId"),
        Index("relationType"),
        Index(value = ["canonicalA", "canonicalB", "relationType"], unique = true)
    ]
)
data class ConnectionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryAId: String,
    val entryBId: String,
    val relationType: String,
    val strength: Float = 0.5f,
    val note: String? = null,
    val isAuto: Boolean = false,
    val isDirected: Boolean,
    val canonicalA: String,
    val canonicalB: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "connection_candidate",
    indices = [
        Index("status"),
        Index(value = ["entryAId", "entryBId"], unique = true)
    ]
)
data class ConnectionCandidateEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryAId: String,
    val entryBId: String,
    val similarity: Float,
    val suggestedType: String = "related",
    val status: String = "pending",   // pending/approved/rejected
    val connectionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null
)