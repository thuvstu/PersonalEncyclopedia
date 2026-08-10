package com.thuvstu.personalencyclopedia.di

import android.content.Context
import androidx.room.Room
import com.thuvstu.personalencyclopedia.db.AppDatabase
import com.thuvstu.personalencyclopedia.db.MIGRATION_1_2
import com.thuvstu.personalencyclopedia.db.MIGRATION_2_3
import com.thuvstu.personalencyclopedia.db.MIGRATION_3_4
import com.thuvstu.personalencyclopedia.db.MIGRATION_4_5
import com.thuvstu.personalencyclopedia.db.MIGRATION_5_6
import com.thuvstu.personalencyclopedia.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "encyclopedia.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
}