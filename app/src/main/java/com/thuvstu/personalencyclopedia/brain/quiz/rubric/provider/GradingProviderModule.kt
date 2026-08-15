package com.thuvstu.personalencyclopedia.brain.quiz.rubric.provider

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 新採点システムのプロバイダー切替ポイント(差し替えはここだけ)。
 *
 * 現状: GeminiGradingProviders(既存Gemini API)
 * 将来: 端末内モデル(Qwen3-Embedding-4B / NLI / Qwen3-Reranker / ローカルLLM)を実装した
 *       LocalGradingProviders へこのモジュールのバインディングを差し替える。
 *       または provider 設定(gemini/local)でルーティングするファサードに拡張する。
 */
@Module
@InstallIn(SingletonComponent::class)
object GradingProviderModule {

    @Provides
    @Singleton
    fun provideEmbeddingProvider(gemini: GeminiGradingProviders): IEmbeddingProvider = gemini

    @Provides
    @Singleton
    fun provideEntailmentProvider(gemini: GeminiGradingProviders): IEntailmentProvider = gemini

    @Provides
    @Singleton
    fun provideCrossEncoderProvider(gemini: GeminiGradingProviders): ICrossEncoderProvider = gemini

    @Provides
    @Singleton
    fun provideJudgerProvider(gemini: GeminiGradingProviders): IJudgerProvider = gemini
}
