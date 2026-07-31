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
        // Phase 2
        EntryWebpageEntity::class,
        EntryBookEntity::class,
        EntryVideoEntity::class,
        EntryDocumentEntity::class,
        EntryMediaEntity::class,
        EntryPersonEntity::class,
        EntryOrgEntity::class,
        EntryPlaceEntity::class,
        EntryEventEntity::class,
        EntryLikedEntity::class,
        EntryAiConvEntity::class,
        SearchDocumentEntity::class,
        SearchDocumentFtsEntity::class,
        EmbeddingEntity::class,
        EmbeddingJobEntity::class,
        // Phase 3
        ConnectionTypeDefEntity::class,
        ConnectionEntity::class,
        ConnectionCandidateEntity::class,
        AiExplanationEntity::class,
        ProgressEventEntity::class,
        PluginEntity::class,

        EntryAttachmentEntity::class,
        WhiteboardNodeEntity::class,
    ],
    views = [
        SrsCurrentView::class,
        QuizMasteryView::class,
    ],
    version = 6,
    exportSchema = false
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
    // Phase 2
    abstract fun entryExtensionDao(): EntryExtensionDao
    abstract fun searchDocumentDao(): SearchDocumentDao
    abstract fun embeddingDao(): EmbeddingDao
    // Phase 3
    abstract fun connectionDao(): ConnectionDao
    abstract fun aiExplanationDao(): AiExplanationDao
    abstract fun progressEventDao(): ProgressEventDao
    abstract fun pluginDao(): PluginDao

    abstract fun entryAttachmentDao(): EntryAttachmentDao
    abstract fun whiteboardDao(): WhiteboardDao
}