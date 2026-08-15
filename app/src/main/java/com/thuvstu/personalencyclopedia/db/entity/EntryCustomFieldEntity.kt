package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * §5.8.3 カスタムフィールド（v8）。
 * 13型のいずれにも属さない自由記述項目を、型定義を変更せずに追加するための汎用テーブル。
 */
@Entity(
    tableName = "entry_custom_field",
    indices = [Index("entryId")]
)
data class EntryCustomFieldEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    val fieldName: String,
    val fieldValue: String,
    val sortOrder: Int = 0
)
