package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── Heptabase ホワイトボード ──
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `whiteboard` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `title` TEXT NOT NULL,
                `summary` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `whiteboard_note` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `contentMd` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `whiteboard_node` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `boardId` TEXT NOT NULL,
                `entryId` TEXT,
                `noteId` TEXT,
                `sectionId` TEXT,
                `x` REAL NOT NULL,
                `y` REAL NOT NULL,
                `width` REAL NOT NULL,
                `height` REAL NOT NULL,
                `zIndex` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_whiteboard_node_boardId` ON `whiteboard_node` (`boardId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_whiteboard_node_entryId` ON `whiteboard_node` (`entryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_whiteboard_node_noteId` ON `whiteboard_node` (`noteId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_whiteboard_node_sectionId` ON `whiteboard_node` (`sectionId`)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `whiteboard_section` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `boardId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `x` REAL NOT NULL,
                `y` REAL NOT NULL,
                `width` REAL NOT NULL,
                `height` REAL NOT NULL,
                `colorHex` TEXT,
                `zIndex` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_whiteboard_section_boardId` ON `whiteboard_section` (`boardId`)")

        // ── Wikipediaビルダー ──
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `wiki_article` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `title` TEXT NOT NULL,
                `contentMd` TEXT NOT NULL,
                `summary` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wiki_article_title` ON `wiki_article` (`title`)")
    }
}