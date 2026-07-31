package com.thuvstu.personalencyclopedia.di

import android.content.Context
import com.thuvstu.personalencyclopedia.db.dao.ConnectionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryThoughtDao
import com.thuvstu.personalencyclopedia.db.dao.PluginDao
import com.thuvstu.personalencyclopedia.db.dao.ProgressEventDao
import com.thuvstu.personalencyclopedia.db.dao.QuizDao
import com.thuvstu.personalencyclopedia.db.dao.SrsReviewDao
import com.thuvstu.personalencyclopedia.server.LocalServer
import com.thuvstu.personalencyclopedia.server.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServerModule {

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext ctx: Context): TokenManager =
        TokenManager(ctx)

    @Provides
    @Singleton
    fun provideLocalServer(
        tokenManager: TokenManager,
        entryDao: EntryDao,
        thoughtDao: EntryThoughtDao,
        definitionDao: EntryDefinitionDao,
        srsReviewDao: SrsReviewDao,
        quizDao: QuizDao,
        connectionDao: ConnectionDao,
        progressEventDao: ProgressEventDao,
        pluginDao: PluginDao
    ): LocalServer = LocalServer(
        tokenManager = tokenManager,
        entryDao = entryDao,
        thoughtDao = thoughtDao,
        definitionDao = definitionDao,
        srsReviewDao = srsReviewDao,
        quizDao = quizDao,
        connectionDao = connectionDao,
        progressEventDao = progressEventDao,
        pluginDao = pluginDao
    )
}