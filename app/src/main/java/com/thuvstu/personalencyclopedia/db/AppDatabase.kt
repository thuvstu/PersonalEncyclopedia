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
        // Phase 4 — v12.0 追加
        WhiteboardEntity::class,
        WhiteboardNoteEntity::class,
        WhiteboardNodeEntity::class,
        WhiteboardSectionEntity::class,
        WikiArticleEntity::class,
        // v7 — 和暦マスタ (GAP-5)
        EraMasterEntity::class,
        // v8 — カスタムフィールド (§5.8.3)
        EntryCustomFieldEntity::class,
        // v9 — v15.0: タスク管理・編集履歴 (§5.9) + SQL Explorer保存クエリ (§11.12)
        TaskEntity::class,
        TaskTimeLogEntity::class,
        EntryHistoryEntity::class,
        SavedQueryEntity::class,
    ],
    views = [
        SrsCurrentView::class,
        QuizMasteryView::class,
    ],
    version = 10,
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
    // Phase 4 — v12.0 追加
    abstract fun whiteboardDao(): WhiteboardDao
    abstract fun wikiArticleDao(): WikiArticleDao
    // v7 — 和暦マスタ (GAP-5)
    abstract fun eraMasterDao(): EraMasterDao
    // v8 — カスタムフィールド (§5.8.3)
    abstract fun entryCustomFieldDao(): EntryCustomFieldDao
    // v9 — v15.0: タスク管理・編集履歴 (§5.9) + SQL Explorer保存クエリ (§11.12)
    abstract fun taskDao(): TaskDao
    abstract fun taskTimeLogDao(): TaskTimeLogDao
    abstract fun entryHistoryDao(): EntryHistoryDao
    abstract fun savedQueryDao(): SavedQueryDao
}