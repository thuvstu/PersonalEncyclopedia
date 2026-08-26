package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "progress_events",
    indices = [Index("entityType"), Index("eventType"), Index("createdAt"), Index("entityId")]
)
data class ProgressEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entityType: String,   // entry/quiz/srs/connection
    val entityId: String,
    val eventType: String,    // viewed/edited/answered/reviewed/connected
    val createdAt: Long = System.currentTimeMillis()
)