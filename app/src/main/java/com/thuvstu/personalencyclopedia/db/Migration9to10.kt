package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v9 → v10 (PERF-2):
 * - progress_events.entityId に索引を追加。
 *   「この特定entryの履歴を見る」クエリが全件スキャンになるのを防ぐ。
 *   無制限に増えるログテーブルのため、件数増に伴う劣化が顕著になる。
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_progress_events_entityId` ON `progress_events` (`entityId`)")
    }
}
