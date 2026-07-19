package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entry_type")
data class EntryTypeEntity(
    @PrimaryKey val name: String,
    val labelJa: String,
    val icon: String? = null,
    val colorHex: String,
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)