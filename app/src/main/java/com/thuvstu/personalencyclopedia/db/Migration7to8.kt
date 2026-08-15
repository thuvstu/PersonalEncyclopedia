package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v7 → v8（設計書§5.8.3 / §5.8.5 / §8.7.3）:
 * - entry_custom_field テーブル新規追加（カスタムフィールド）
 * - srs_review に repetitionCount 追加（反復回数の明示的記録、FSRS移行§8.8の前提）
 * - quiz_attempts に answeredWithinMs 追加（早押しスコア係数用）
 * - SrsCurrentView を repetitionCount を含む定義へ再作成
 *
 * 注意: ビュー定義は Room スキーマJSON(8.json)と文字列完全一致が要求されるため、
 * インデントを Room の出力形式と揃えている（view 検証は完全一致比較）。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // §5.8.3: カスタムフィールド
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `entry_custom_field` (" +
                "`id` TEXT NOT NULL, " +
                "`entryId` TEXT NOT NULL, " +
                "`fieldName` TEXT NOT NULL, " +
                "`fieldValue` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_entry_custom_field_entryId` " +
                "ON `entry_custom_field` (`entryId`)"
        )

        // §5.8.5: 反復回数の明示的記録
        db.execSQL(
            "ALTER TABLE `srs_review` ADD COLUMN `repetitionCount` INTEGER NOT NULL DEFAULT 0"
        )

        // §8.7.3: 回答経過時間
        db.execSQL(
            "ALTER TABLE `quiz_attempts` ADD COLUMN `answeredWithinMs` INTEGER"
        )

        // SrsCurrentView 再作成（repetitionCount を含む定義へ更新）
        db.execSQL("DROP VIEW IF EXISTS `SrsCurrentView`")
        db.execSQL(SRS_CURRENT_VIEW_SQL)
    }
}

/** SrsReviewEntity の @DatabaseView と同一の定義（RoomスキーマJSONと文字列完全一致させる）。 */
private val SRS_CURRENT_VIEW_SQL: String =
    "CREATE VIEW `SrsCurrentView` AS SELECT sr.entryId, sr.grade, sr.intervalDays, sr.easeFactor, sr.nextReviewAt,\n" +
        "           sr.repetitionCount, sr.reviewedAt AS lastReviewedAt\n" +
        "    FROM srs_review sr\n" +
        "    INNER JOIN (\n" +
        "        SELECT entryId, MAX(reviewedAt) AS maxReviewedAt\n" +
        "        FROM srs_review\n" +
        "        GROUP BY entryId\n" +
        "    ) latest ON sr.entryId = latest.entryId AND sr.reviewedAt = latest.maxReviewedAt"
