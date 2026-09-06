package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
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
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_attachment_entryId` ON `entry_attachment` (`entryId`)")
    }
}