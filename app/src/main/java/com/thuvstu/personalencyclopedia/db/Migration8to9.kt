package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v8 → v9（設計書§5.9 / §11.12、v15.0で新設）:
 * - task テーブル新規追加（§5.9.1 タスク管理・パーキンソンの法則対抗）
 * - task_time_log テーブル新規追加（§5.9.1 見積もり vs 実績の乖離記録 / §7.8 StudyPlus同期）
 * - entry_history テーブル新規追加（§5.9.2 編集履歴）
 * - saved_query テーブル新規追加（§11.12 SQL Explorer 保存済みクエリ）
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // §5.9.1: task
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task` (" +
                "`id` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`description` TEXT, " +
                "`estimatedMinutes` INTEGER NOT NULL, " +
                "`deadlineAt` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`postponeCount` INTEGER NOT NULL, " +
                "`linkedEntryId` TEXT, " +
                "`linkedTopicId` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`completedAt` INTEGER, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_status` ON `task` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_deadlineAt` ON `task` (`deadlineAt`)")

        // §5.9.1: task_time_log
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_time_log` (" +
                "`id` TEXT NOT NULL, " +
                "`taskId` TEXT NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, " +
                "`endedAt` INTEGER, " +
                "`studyPlusSynced` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_task_time_log_taskId` " +
                "ON `task_time_log` (`taskId`)"
        )

        // §5.9.2: entry_history
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `entry_history` (" +
                "`id` TEXT NOT NULL, " +
                "`entryId` TEXT NOT NULL, " +
                "`recordedAt` INTEGER NOT NULL, " +
                "`titleSnapshot` TEXT NOT NULL, " +
                "`contentSnapshot` TEXT, " +
                "`changeSummary` TEXT NOT NULL, " +
                "`charCountDelta` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_entry_history_entryId` " +
                "ON `entry_history` (`entryId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_entry_history_recordedAt` " +
                "ON `entry_history` (`recordedAt`)"
        )

        // §11.12: saved_query
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_query` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`sql` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}
