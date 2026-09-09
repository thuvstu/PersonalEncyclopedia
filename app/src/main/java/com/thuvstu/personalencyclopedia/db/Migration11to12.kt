package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v11 → v12 (wt56 付箋):
 * - entry_sticky_note テーブル新規追加（カードに貼る一言メモ。1エントリに複数枚）。
 *   カラム定義は EntryStickyNoteEntity と一致させる（Roomのスキーマ検証に通す）。
 * - 既存データは無改変（非破壊マイグレーション方針を維持）。
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `entry_sticky_note` (" +
                "`id` TEXT NOT NULL, " +
                "`entryId` TEXT NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`color` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`contextId` TEXT, " +
                "`isPinned` INTEGER NOT NULL, " +
                "`isResolved` INTEGER NOT NULL, " +
                "`resolvedAt` INTEGER, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`promotedEntryId` TEXT, " +
                "`promotedTaskId` TEXT, " +
                "`promotedCandidateId` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_sticky_note_entryId` ON `entry_sticky_note` (`entryId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_sticky_note_createdAt` ON `entry_sticky_note` (`createdAt`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_sticky_note_isResolved` ON `entry_sticky_note` (`isResolved`)")
    }
}
