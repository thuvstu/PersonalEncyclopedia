package com.thuvstu.personalencyclopedia.server

import com.thuvstu.personalencyclopedia.brain.quiz.QuizGraderService
import com.thuvstu.personalencyclopedia.db.dao.ConnectionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryThoughtDao
import com.thuvstu.personalencyclopedia.db.dao.PluginDao
import com.thuvstu.personalencyclopedia.db.dao.ProgressEventDao
import com.thuvstu.personalencyclopedia.db.dao.QuizDao
import com.thuvstu.personalencyclopedia.db.dao.SrsReviewDao
import javax.inject.Inject

/**
 * LocalServer へ注入する依存の束(設計書§10.1)。
 * エンドポイント分割後も、ルーティング登録は単一の依存オブジェクトを渡すだけで済む。
 */
class ServerDependencies @Inject constructor(
    val entryDao: EntryDao,
    val thoughtDao: EntryThoughtDao,
    val definitionDao: EntryDefinitionDao,
    val srsReviewDao: SrsReviewDao,
    val quizDao: QuizDao,
    val connectionDao: ConnectionDao,
    val progressEventDao: ProgressEventDao,
    val pluginDao: PluginDao,
    // ★最適化R6: 採点はアプリと共通のQuizGraderServiceに統一
    val quizGraderService: QuizGraderService
)
