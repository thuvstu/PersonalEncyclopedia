package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
                // topic
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS `topic` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `parentId` TEXT,
                `description` TEXT,
                `colorHex` TEXT
            )
        """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topic_name` ON `topic` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topic_parentId` ON `topic` (`parentId`)")

                // entry_topic
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS `entry_topic` (
                `entryId` TEXT NOT NULL,
                `topicId` TEXT NOT NULL,
                PRIMARY KEY (`entryId`, `topicId`),
                FOREIGN KEY (`entryId`) REFERENCES `entry`(`id`) ON DELETE CASCADE,
                FOREIGN KEY (`topicId`) REFERENCES `topic`(`id`) ON DELETE CASCADE
            )
        """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_topic_topicId` ON `entry_topic` (`topicId`)")

                // srs_review
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS `srs_review` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `entryId` TEXT NOT NULL,
                `reviewedAt` INTEGER NOT NULL,
                `grade` INTEGER NOT NULL,
                `intervalDays` INTEGER NOT NULL,
                `easeFactor` REAL NOT NULL DEFAULT 2.5,
                `nextReviewAt` INTEGER NOT NULL
            )
        """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_srs_review_entryId` ON `srs_review` (`entryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_srs_review_reviewedAt` ON `srs_review` (`reviewedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_srs_review_nextReviewAt` ON `srs_review` (`nextReviewAt`)")

                // quiz_bank
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS `quiz_bank` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `sourceEntryId` TEXT,
                `topicId` TEXT,
                `pluginId` TEXT,
                `quizType` TEXT NOT NULL,
                `question` TEXT NOT NULL,
                `choicesJson` TEXT NOT NULL DEFAULT '[]',
                `answer` TEXT NOT NULL,
                `gradingContextJson` TEXT NOT NULL DEFAULT '{}',
                `hintsJson` TEXT NOT NULL DEFAULT '[]',
                `explanation` TEXT,
                `imagesJson` TEXT NOT NULL DEFAULT '{}',
                `generationMethod` TEXT NOT NULL,
                `numericVariantConfigJson` TEXT,
                `difficulty` INTEGER NOT NULL DEFAULT 3,
                `isActive` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL
            )
        """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_bank_topicId` ON `quiz_bank` (`topicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_bank_quizType` ON `quiz_bank` (`quizType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_bank_isActive` ON `quiz_bank` (`isActive`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_bank_sourceEntryId` ON `quiz_bank` (`sourceEntryId`)")

                // quiz_attempts
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS `quiz_attempts` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `quizId` TEXT NOT NULL,
                `userAnswer` TEXT NOT NULL,
                `isCorrect` INTEGER,
                `score` REAL NOT NULL,
                `gradingMethod` TEXT NOT NULL,
                `hintsRevealed` INTEGER NOT NULL DEFAULT 0,
                `attemptedAt` INTEGER NOT NULL
            )
        """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_attempts_quizId` ON `quiz_attempts` (`quizId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_attempts_attemptedAt` ON `quiz_attempts` (`attemptedAt`)")

                // ── Views（RoomスキーマJSONと文字列完全一致が必要なため形式を合わせる） ──
                db.execSQL(
                    "CREATE VIEW `SrsCurrentView` AS SELECT sr.entryId, sr.grade, sr.intervalDays, sr.easeFactor, sr.nextReviewAt,\n" +
                        "           sr.reviewedAt AS lastReviewedAt\n" +
                        "    FROM srs_review sr\n" +
                        "    INNER JOIN (\n" +
                        "        SELECT entryId, MAX(reviewedAt) AS maxReviewedAt\n" +
                        "        FROM srs_review\n" +
                        "        GROUP BY entryId\n" +
                        "    ) latest ON sr.entryId = latest.entryId AND sr.reviewedAt = latest.maxReviewedAt"
                )

                db.execSQL(
                    "CREATE VIEW `QuizMasteryView` AS SELECT quizId, MAX(score) AS masteryScore FROM quiz_attempts GROUP BY quizId"
                )
        }
}