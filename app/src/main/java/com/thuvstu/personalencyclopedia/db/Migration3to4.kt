package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `connection_type_def` (
                `name` TEXT NOT NULL PRIMARY KEY,
                `labelJa` TEXT NOT NULL,
                `isDirected` INTEGER NOT NULL,
                `inverseLabelJa` TEXT
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `connection` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `entryAId` TEXT NOT NULL,
                `entryBId` TEXT NOT NULL,
                `relationType` TEXT NOT NULL,
                `strength` REAL NOT NULL DEFAULT 0.5,
                `note` TEXT,
                `isAuto` INTEGER NOT NULL DEFAULT 0,
                `isDirected` INTEGER NOT NULL,
                `canonicalA` TEXT NOT NULL,
                `canonicalB` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_connection_entryAId` ON `connection` (`entryAId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_connection_entryBId` ON `connection` (`entryBId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_connection_relationType` ON `connection` (`relationType`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_connection_canonical` ON `connection` (`canonicalA`, `canonicalB`, `relationType`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `connection_candidate` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `entryAId` TEXT NOT NULL,
                `entryBId` TEXT NOT NULL,
                `similarity` REAL NOT NULL,
                `suggestedType` TEXT NOT NULL DEFAULT 'related',
                `status` TEXT NOT NULL DEFAULT 'pending',
                `connectionId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `reviewedAt` INTEGER
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_connection_candidate_status` ON `connection_candidate` (`status`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_connection_candidate_pair` ON `connection_candidate` (`entryAId`, `entryBId`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ai_explanations` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `sourceType` TEXT NOT NULL,
                `sourceId` TEXT NOT NULL,
                `prompt` TEXT NOT NULL,
                `response` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_explanations_source` ON `ai_explanations` (`sourceType`, `sourceId`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `progress_events` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `entityType` TEXT NOT NULL,
                `entityId` TEXT NOT NULL,
                `eventType` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_progress_events_entityType` ON `progress_events` (`entityType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_progress_events_eventType` ON `progress_events` (`eventType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_progress_events_createdAt` ON `progress_events` (`createdAt`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `plugins` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `version` TEXT NOT NULL,
                `manifestJson` TEXT NOT NULL,
                `scriptPath` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL DEFAULT 1,
                `installedAt` INTEGER NOT NULL
            )
        """)
    }
}