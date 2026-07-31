package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `whiteboard_node` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `entryId` TEXT NOT NULL,
                `x` REAL NOT NULL,
                `y` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_whiteboard_node_entryId` ON `whiteboard_node` (`entryId`)")
    }
}