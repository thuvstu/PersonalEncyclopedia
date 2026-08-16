package com.thuvstu.personalencyclopedia.integration

import android.util.Log
import com.thuvstu.personalencyclopedia.db.dao.TaskDao
import com.thuvstu.personalencyclopedia.db.dao.TaskTimeLogDao
import com.thuvstu.personalencyclopedia.db.entity.TaskEntity
import com.thuvstu.personalencyclopedia.db.entity.TaskTimeLogEntity
import com.thuvstu.personalencyclopedia.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StudyPlus連携（設計書§7.8）。
 *
 * 公式SDK（com.github.studyplus:Studyplus-Android-SDK:4.0.2、JitPack配布）は、
 * ・端末にStudyplusアプリ(7.0+)のインストールが必須
 * ・consumerKey/consumerSecret の取得には教材アプリ開発者登録が必要
 * という制約があり、開発者登録が承認されるまでは連携をOFFにしたまま開発を進める設計とする（§7.8.1）。
 *
 * 本クラスは同期キュー（task_time_log.studyPlusSynced）・未同期件数の管理・認証状態の保持という
 * データフロー全体を実装し、実際のSDK呼び出しだけを [StudyPlusSdkBridge] に隔離している。
 * SDKを導入できる環境になったら [SdkStudyPlusBridge]（後述の実装手順）を差し込むことで、
 * このファイルのロジックを一切変更せずに実投稿が有効になる。
 */
@Singleton
class StudyPlusClient @Inject constructor(
    private val settings: SettingsRepository,
    private val taskDao: TaskDao,
    private val timeLogDao: TaskTimeLogDao,
    private val bridge: StudyPlusSdkBridge
) {
    private val tag = "StudyPlusClient"

    /** consumerKey/Secret が設定済みかどうか（SDKインスタンス生成条件、§7.8.2）。 */
    suspend fun isConfigured(): Boolean =
        !settings.studyPlusConsumerKey.first().isNullOrBlank() &&
            !settings.studyPlusConsumerSecret.first().isNullOrBlank()

    suspend fun isEnabled(): Boolean = settings.studyPlusEnabled.first()

    /**
     * 認証開始（Studyplusアプリへ遷移してOAuth）。
     * 戻り値 false = Studyplusアプリ未インストール等で開始できなかった。
     */
    suspend fun startAuth(): Boolean {
        if (!isConfigured()) return false
        return bridge.startAuth()
    }

    /**
     * タスクのタイムログをStudyplusへ同期する（§7.8.2）。
     * - 未同期・endedAt確定済みのみ対象
     * - 未認証/未設定/投稿失敗時は静かにスキップし（エラー扱いしない）、次回一括同期に回す
     */
    /** @return true = 投稿に成功しstudyPlusSyncedを立てた。 */
    suspend fun syncTaskTimeLog(log: TaskTimeLogEntity, task: TaskEntity, topicName: String?): Boolean {
        if (log.studyPlusSynced || log.endedAt == null) return false
        if (!isEnabled() || !isConfigured()) return false
        val durationSec = ((log.endedAt - log.startedAt) / 1000).toInt().coerceAtMost(86_400) // 最大24h
        if (durationSec <= 0) return false
        val comment = listOfNotNull(task.title, topicName).joinToString(" / ")
        val ok = bridge.postRecord(durationSec, comment)
        if (ok) {
            timeLogDao.markSynced(log.id)
            Log.i(tag, "StudyPlus送信成功: ${task.title} (${durationSec}s)")
            return true
        }
        Log.w(tag, "StudyPlus送信失敗（次回一括同期で再試行）: ${task.title}")
        return false
    }

    /** 未同期のタイムログ件数（設定画面に表示、§7.8.2）。 */
    fun observePendingSyncCount(): Flow<Int> = timeLogDao.observePendingSyncCount()

    /** 未同期分を一括再試行（設定画面の「一括同期」ボタン）。@return 今回同期できた件数 */
    suspend fun syncAllPending(): Int {
        var synced = 0
        for (log in timeLogDao.getPendingSyncLogs()) {
            val task = taskDao.getById(log.taskId) ?: continue
            val topicName = task.linkedTopicId?.let { "科目:$it" }
            if (syncTaskTimeLog(log, task, topicName)) synced++
        }
        return synced
    }
}

/**
 * SDK呼び出しを隔離するブリッジ。
 *
 * 【SDK導入手順】（JitPackへ到達できる環境で実施）:
 * 1. settings.gradle.kts の dependencyResolutionManagement.repositories に
 *    `maven { url = uri("https://jitpack.io") }` を追加
 * 2. app/build.gradle.kts に
 *    `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")` と
 *    `implementation("com.github.studyplus:Studyplus-Android-SDK:4.0.2")` を追加
 * 3. SdkStudyPlusBridge のコメントアウト実装を有効化し、コンストラクタ引数の
 *    Studyplus インスタンス生成（consumerKey/consumerSecret）を有効化する
 */
interface StudyPlusSdkBridge {
    /** 認証開始。false = アプリ未インストール等で開始不可。 */
    suspend fun startAuth(): Boolean

    /** 学習記録を投稿。true = 成功。 */
    suspend fun postRecord(durationSec: Int, comment: String): Boolean
}

/** SDK未導入時のブリッジ：常に「未対応」を返し、連携OFF相当で動作する。 */
@Singleton
class NoOpStudyPlusBridge @Inject constructor() : StudyPlusSdkBridge {
    override suspend fun startAuth(): Boolean = false
    override suspend fun postRecord(durationSec: Int, comment: String): Boolean = false
}
