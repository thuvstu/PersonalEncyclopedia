package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entry_attachment` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `entryId` TEXT NOT NULL,
                `blobPath` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `caption` TEXT,
                `sortOrder` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_attachment_entryId` ON `entry_attachment` (`entryId`)")
    }
}