package com.thuvstu.personalencyclopedia.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.thuvstu.personalencyclopedia.db.dao.*
import com.thuvstu.personalencyclopedia.db.entity.*

@Database(
    entities = [
        // Phase 0
        EntryTypeEntity::class,
        EntryEntity::class,
        EntryThoughtEntity::class,
        EntryDefinitionEntity::class,
        TagEntity::class,
        EntryTagEntity::class,
        // Phase 1
        TopicEntity::class,
        EntryTopicEntity::class,
        SrsReviewEntity::class,
        QuizBankEntity::class,
        QuizAttemptEntity::class,
    ],
    views = [
        SrsCurrentView::class,
        QuizMasteryView::class,
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    // Phase 0
    abstract fun entryTypeDao(): EntryTypeDao
    abstract fun entryDao(): EntryDao
    abstract fun entryThoughtDao(): EntryThoughtDao
    abstract fun entryDefinitionDao(): EntryDefinitionDao
    abstract fun tagDao(): TagDao
    // Phase 1
    abstract fun topicDao(): TopicDao
    abstract fun srsReviewDao(): SrsReviewDao
    abstract fun quizDao(): QuizDao
}