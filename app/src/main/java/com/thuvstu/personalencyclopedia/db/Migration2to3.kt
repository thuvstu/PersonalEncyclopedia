package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ── Entry extension tables ──
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entry_webpage` (
                `entryId` TEXT NOT NULL PRIMARY KEY,
                `url` TEXT NOT NULL,
                `domain` TEXT NOT NULL,
                `scrapedAt` INTEGER,
                `fullText` TEXT,
                `thumbnailPath` TEXT,
                `readingTimeS` INTEGER,
                `author` TEXT,
                `publishedAt` INTEGER,
                `scraperUsed` TEXT,
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entry_book` (
                `entryId` TEXT NOT NULL PRIMARY KEY,
                `isbn` TEXT,
                `authorsJson` TEXT NOT NULL DEFAULT '[]',
                `publisher` TEXT,
                `publishedYear` INTEGER,
                `totalPages` INTEGER,
                `readStatus` TEXT NOT NULL DEFAULT 'unread',
                `readStartDate` INTEGER,
                `readEndDate` INTEGER,
                `rating` INTEGER,
                `coverPath` TEXT,
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entry_video` (
                `entryId` TEXT NOT NULL PRIMARY KEY,
                `platform` TEXT NOT NULL,
                `videoId` TEXT,
                `channelName` TEXT,
                `durationS` INTEGER,
                `thumbnailUrl` TEXT,
                `transcript` TEXT,
                `watchedAt` INTEGER,
                `watchProgress` REAL,
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entry_document` (
                `entryId` TEXT NOT NULL PRIMARY KEY,
                `docType` TEXT NOT NULL,
                `blobPath` TEXT,
                `gdriveId` TEXT,
                `mimeType` TEXT NOT NULL,
                `fileSizeBytes` INTEGER,
                `pageCount` INTEGER,
                `extractedText` TEXT,
                `extractionMethod` TEXT,
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entry_media` (
                `entryId` TEXT NOT NULL PRIMARY KEY,
                `mediaType` TEXT NOT NULL,
                `blobPath` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `widthPx` INTEGER,
                `heightPx` INTEGER,
                `durationS` REAL,
                `ocrText` TEXT,
                `caption` TEXT,
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entry_person` (
                `entryId` TEXT NOT NULL PRIMARY KEY,
                `fullName` TEXT NOT NULL,
                `aliasesJson` TEXT NOT NULL DEFAULT '[]',
                `birthYear` INTEGER,
                `deathYear` INTEGER,
                `nationality` TEXT,
                `occupationsJson` TEXT NOT NULL DEFAULT '[]',
                `biography` TEXT,
                `photoPath` TEXT,
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entry_org` (
                `entryId` TEXT NOT NULL PRIMARY KEY,
                `officialName` TEXT NOT NULL,
                `orgType` TEXT,
                `foundedYear` INTEGER,
                `country` TEXT,
                `websiteUrl` TEXT,
                `description` TEXT,
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entry_place` (
                `entryId` TEXT NOT NULL PRIMARY KEY,
                `placeName` TEXT NOT NULL,
                `placeType` TEXT,
                `address` TEXT,
                `latitude` REAL,
                `longitude` REAL,
                `visitedDatesJson` TEXT NOT NULL DEFAULT '[]',
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entry_event` (
                `entryId` TEXT NOT NULL PRIMARY KEY,
                `eventName` TEXT NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `endedAt` INTEGER,
                `locationText` TEXT,
                `placeEntryId` TEXT,
                `isPersonal` INTEGER NOT NULL DEFAULT 1,
                `participantsJson` TEXT NOT NULL DEFAULT '[]',
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entry_liked` (
                `entryId` TEXT NOT NULL PRIMARY KEY,
                `platform` TEXT NOT NULL,
                `originalId` TEXT NOT NULL,
                `likedAt` INTEGER,
                `contentType` TEXT NOT NULL,
                `authorName` TEXT,
                `fullText` TEXT,
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entry_ai_conv` (
                `entryId` TEXT NOT NULL PRIMARY KEY,
                `model` TEXT NOT NULL,
                `provider` TEXT NOT NULL,
                `messagesJson` TEXT NOT NULL DEFAULT '[]',
                `tokenCount` INTEGER,
                `topic` TEXT,
                `isUseful` INTEGER,
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)

        // ── Search Document + FTS4 ──
            db.execSQL("""
            CREATE TABLE IF NOT EXISTS `search_document` (
                `entryId` TEXT NOT NULL PRIMARY KEY,
                `combinedText` TEXT NOT NULL,
                `lang` TEXT NOT NULL DEFAULT 'ja',
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)

            db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS `search_document_fts`
            USING FTS4(`ftsContent`)
        """)


        // ── Embedding ──
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `embedding` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `entryId` TEXT NOT NULL,
                `vectorBlob` BLOB NOT NULL,
                `model` TEXT NOT NULL DEFAULT 'gemini-embedding-2-preview',
                `inputText` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_embedding_entryId` ON `embedding` (`entryId`)")

        // ── Embedding Job Queue ──
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `embedding_job` (
                `entryId` TEXT NOT NULL PRIMARY KEY,
                `status` TEXT NOT NULL DEFAULT 'queued',
                `attempts` INTEGER NOT NULL DEFAULT 0,
                `error` TEXT,
                `queuedAt` INTEGER NOT NULL,
                `doneAt` INTEGER
            )
        """)
    }
}