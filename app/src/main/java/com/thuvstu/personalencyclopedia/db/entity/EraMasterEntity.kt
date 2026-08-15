package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 和暦マスタ(設計書§5.8.4)。
 * 元年の西暦 + (yearInEra - 1) で西暦へ変換する。
 * endYear == null は現在も継続中の元号(令和)を意味する。
 */
@Entity(tableName = "era_master")
data class EraMasterEntity(
    @PrimaryKey val name: String,
    val startYear: Int,
    val endYear: Int?,
    val sortOrder: Int
)
