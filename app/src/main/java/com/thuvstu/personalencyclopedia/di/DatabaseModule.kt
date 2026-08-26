package com.thuvstu.personalencyclopedia.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.thuvstu.personalencyclopedia.db.AppDatabase
import com.thuvstu.personalencyclopedia.db.MIGRATION_1_2
import com.thuvstu.personalencyclopedia.db.ReadOnlySqlExecutor
import com.thuvstu.personalencyclopedia.db.MIGRATION_2_3
import com.thuvstu.personalencyclopedia.db.MIGRATION_3_4
import com.thuvstu.personalencyclopedia.db.MIGRATION_4_5
import com.thuvstu.personalencyclopedia.db.MIGRATION_5_6
import com.thuvstu.personalencyclopedia.db.MIGRATION_6_7
import com.thuvstu.personalencyclopedia.db.MIGRATION_7_8
import com.thuvstu.personalencyclopedia.db.MIGRATION_8_9
import com.thuvstu.personalencyclopedia.db.MIGRATION_9_10
import com.thuvstu.personalencyclopedia.db.dao.*
import com.thuvstu.personalencyclopedia.integration.NoOpStudyPlusBridge
import com.thuvstu.personalencyclopedia.integration.StudyPlusSdkBridge
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // PERF-1: 高スペック端末に合わせたExecutor分離。WALで読み書き競合を緩和
    private val queryExecutor = java.util.concurrent.Executors.newFixedThreadPool(4)
    private val transactionExecutor = java.util.concurrent.Executors.newFixedThreadPool(2)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "encyclopedia.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .setQueryExecutor(queryExecutor)
            .setTransactionExecutor(transactionExecutor)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // WALでは fsync 頻度を下げても安全性を保てる。書き込み4倍改善の報告あり
                    db.execSQL("PRAGMA synchronous = NORMAL")
                }
            })
            .build()

    @Provides fun provideEntryTypeDao(db: AppDatabase): EntryTypeDao = db.entryTypeDao()
    @Provides fun provideEntryDao(db: AppDatabase): EntryDao = db.entryDao()
    @Provides fun provideThoughtDao(db: AppDatabase): EntryThoughtDao = db.entryThoughtDao()
    @Provides fun provideDefinitionDao(db: AppDatabase): EntryDefinitionDao = db.entryDefinitionDao()
    @Provides fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
    @Provides fun provideTopicDao(db: AppDatabase): TopicDao = db.topicDao()
    @Provides fun provideSrsReviewDao(db: AppDatabase): SrsReviewDao = db.srsReviewDao()
    @Provides fun provideQuizDao(db: AppDatabase): QuizDao = db.quizDao()
    @Provides fun provideEntryExtensionDao(db: AppDatabase): EntryExtensionDao = db.entryExtensionDao()
    @Provides fun provideSearchDocumentDao(db: AppDatabase): SearchDocumentDao = db.searchDocumentDao()
    @Provides fun provideEmbeddingDao(db: AppDatabase): EmbeddingDao = db.embeddingDao()
    @Provides fun provideConnectionDao(db: AppDatabase): ConnectionDao = db.connectionDao()
    @Provides fun provideAiExplanationDao(db: AppDatabase): AiExplanationDao = db.aiExplanationDao()
    @Provides fun provideProgressEventDao(db: AppDatabase): ProgressEventDao = db.progressEventDao()
    @Provides fun providePluginDao(db: AppDatabase): PluginDao = db.pluginDao()
    @Provides fun provideAttachmentDao(db: AppDatabase): EntryAttachmentDao = db.entryAttachmentDao()
    // ★v12.0 追加
    @Provides fun provideWhiteboardDao(db: AppDatabase): WhiteboardDao = db.whiteboardDao()
    @Provides fun provideWikiArticleDao(db: AppDatabase): WikiArticleDao = db.wikiArticleDao()
    // v7 — 和暦マスタ (GAP-5)
    @Provides fun provideEraMasterDao(db: AppDatabase): EraMasterDao = db.eraMasterDao()
    // v8 — カスタムフィールド (§5.8.3)
    @Provides fun provideEntryCustomFieldDao(db: AppDatabase): EntryCustomFieldDao = db.entryCustomFieldDao()
    // v9 — v15.0: タスク管理・編集履歴 (§5.9) + SQL Explorer保存クエリ (§11.12)
    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideTaskTimeLogDao(db: AppDatabase): TaskTimeLogDao = db.taskTimeLogDao()
    @Provides fun provideEntryHistoryDao(db: AppDatabase): EntryHistoryDao = db.entryHistoryDao()
    @Provides fun provideSavedQueryDao(db: AppDatabase): SavedQueryDao = db.savedQueryDao()
    // §11.12 SQL Explorer（読み取り専用）
    @Provides fun provideReadOnlySqlExecutor(db: AppDatabase): ReadOnlySqlExecutor = ReadOnlySqlExecutor(db)
    // §7.8 StudyPlus SDKブリッジ（SDK未導入時はNoOp。JitPack到達可能環境でSdkStudyPlusBridgeへ差し替え）
    @Provides @Singleton
    fun provideStudyPlusSdkBridge(noOp: NoOpStudyPlusBridge): StudyPlusSdkBridge = noOp
}