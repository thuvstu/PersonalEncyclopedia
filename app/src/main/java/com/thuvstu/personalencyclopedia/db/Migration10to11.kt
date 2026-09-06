package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v10 → v11 (★P3-1 白板エッジ):
 * - whiteboard_edge テーブル新規追加（ノード間の接続線。Heptabaseの"edge"）。
 *   ボード固有の見た目の線であり、承認制の connection とは独立。
 *   カラム定義は WhiteboardEdgeEntity と一致させる（Roomのスキーマ検証に通す）。
 * - 既存データは無改変（非破壊マイグレーション方針を維持）。
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `whiteboard_edge` (" +
                "`id` TEXT NOT NULL, " +
                "`boardId` TEXT NOT NULL, " +
                "`sourceNodeId` TEXT NOT NULL, " +
                "`targetNodeId` TEXT NOT NULL, " +
                "`label` TEXT, " +
                "`colorHex` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_whiteboard_edge_boardId` ON `whiteboard_edge` (`boardId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_whiteboard_edge_sourceNodeId` ON `whiteboard_edge` (`sourceNodeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_whiteboard_edge_targetNodeId` ON `whiteboard_edge` (`targetNodeId`)")
    }
}
