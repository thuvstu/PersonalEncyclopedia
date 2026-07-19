package com.thuvstu.personalencyclopedia.di

import android.content.Context
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
    fun provideLocalServer(tokenManager: TokenManager): LocalServer =
        LocalServer(tokenManager)
}