package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "ai_explanations",
    indices = [Index(value = ["sourceType", "sourceId"], unique = true)]
)
data class AiExplanationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceType: String,   // quiz_mistake/weak_point_analysis/entry_summary
    val sourceId: String,
    val prompt: String,
    val response: String,
    val createdAt: Long = System.currentTimeMillis()
)