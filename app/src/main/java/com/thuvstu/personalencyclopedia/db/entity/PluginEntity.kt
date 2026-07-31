package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugins")
data class PluginEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val manifestJson: String,
    val scriptPath: String,
    val isActive: Boolean = true,
    val installedAt: Long = System.currentTimeMillis()
)