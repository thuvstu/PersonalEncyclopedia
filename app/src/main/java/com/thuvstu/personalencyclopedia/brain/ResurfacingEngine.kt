package com.thuvstu.personalencyclopedia.brain

import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.ln

/**
 * §7.5 時間軸レイヤー — リサーフェシングエンジン。
 *
 * 設計原則（§7.5.2「情報寿命」）:
 * - 自動削除・自動mute は絶対に行わない
 * - 「整理しませんか」の提案のみを行う
 * - isFavorite=true のエントリーは提案対象外
 * - accessedAt を閲覧の都度更新する前提（EntryDetailViewModel.init で実施済み）
 *
 * 減衰スコア計算:
 * - 最終アクセスからの経過日数に基づく指数減衰
 * - 型ごとの重み付け（webpage/liked/ai_conv は減衰が早い）
 * - 半年(180日)以上未アクセス かつ isFavorite=false を「整理候補」とする
 *
 * 再浮上スコア:
 * - 適度に古いが完全に忘れ去られていないエントリーを「再浮上」候補とする
 * - 30〜180日未アクセス の definition/thought を優先
 */
@Singleton
class ResurfacingEngine @Inject constructor(
    private val entryDao: EntryDao
) {
    data class ResurfacingCandidate(
        val entry: EntryEntity,
        val score: Float,           // 0.0〜1.0（高いほど再浮上推奨）
        val daysSinceAccess: Long,
        val reason: String
    )

    data class CleanupSuggestion(
        val entry: EntryEntity,
        val daysSinceAccess: Long,
        val reason: String
    )

    companion object {
        // 型ごとの半減期（日数）。短いほど「鮮度が重要」とみなす
        private val HALF_LIFE_DAYS = mapOf(
            "webpage" to 60.0,
            "liked" to 45.0,
            "ai_conv" to 30.0,
            "thought" to 120.0,
            "definition" to 365.0,   // 単語帳は長期保持前提
            "book" to 180.0,
            "video" to 90.0,
            "document" to 150.0,
            "media" to 90.0,
            "person" to 365.0,
            "org" to 365.0,
            "place" to 365.0,
            "event" to 180.0
        )
        private const val DEFAULT_HALF_LIFE = 120.0
        private const val CLEANUP_THRESHOLD_DAYS = 180L   // §7.5: 半年
        private const val RESURFACE_MIN_DAYS = 30L
        private const val RESURFACE_MAX_DAYS = 180L
    }

    /**
     * 再浮上候補を取得（Dashboard の「再浮上」セクション用）。
     * 30〜180日未アクセスの definition/thought を優先。
     */
    suspend fun getResurfacingCandidates(limit: Int = 5): List<ResurfacingCandidate> {
        val allEntries = entryDao.observeAll(limit = 500, offset = 0).first()
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000

        return allEntries
            .filter { e ->
                e.deletedAt == null &&
                        !e.isFavorite &&
                        !e.isMuted &&
                        e.accessedAt != null
            }
            .mapNotNull { e ->
                val daysSince = (now - (e.accessedAt ?: e.createdAt)) / dayMs
                if (daysSince < RESURFACE_MIN_DAYS || daysSince > RESURFACE_MAX_DAYS) {
                    return@mapNotNull null
                }
                val halfLife = HALF_LIFE_DAYS[e.type] ?: DEFAULT_HALF_LIFE
                // 減衰スコア: 半減期ベースの指数減衰
                val decay = exp(-ln(2.0) * daysSince / halfLife).toFloat()
                // 再浮上スコア: 適度に古い（完全に減衰していない）ほど高い
                // 30日付近で最大、180日付近で最小
                val recencyFactor = 1.0f - (daysSince - RESURFACE_MIN_DAYS).toFloat() /
                        (RESURFACE_MAX_DAYS - RESURFACE_MIN_DAYS)
                val score = decay * 0.6f + recencyFactor * 0.4f

                val reason = when (e.type) {
                    "definition" -> "復習のタイミングです"
                    "thought" -> "過去の思考を振り返りませんか"
                    "webpage" -> "保存したページを再訪しませんか"
                    "book" -> "読書を再開しませんか"
                    else -> "しばらく開いていません"
                }

                ResurfacingCandidate(
                    entry = e,
                    score = score,
                    daysSinceAccess = daysSince,
                    reason = reason
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * 整理候補を取得（§7.5.2: 半年以上未アクセス かつ isFavorite=false）。
     * 自動削除はしない。提案のみ。
     */
    suspend fun getCleanupSuggestions(limit: Int = 10): List<CleanupSuggestion> {
        val allEntries = entryDao.observeAll(limit = 1000, offset = 0).first()
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000

        // 整理提案の対象型（§7.5.2: webpage/liked/ai_conv）
        val cleanupTypes = setOf("webpage", "liked", "ai_conv")

        return allEntries
            .filter { e ->
                e.deletedAt == null &&
                        !e.isFavorite &&
                        !e.isMuted &&
                        e.type in cleanupTypes &&
                        e.accessedAt != null
            }
            .mapNotNull { e ->
                val daysSince = (now - (e.accessedAt ?: e.createdAt)) / dayMs
                if (daysSince < CLEANUP_THRESHOLD_DAYS) return@mapNotNull null

                val reason = when (e.type) {
                    "webpage" -> "${daysSince}日未アクセスのWebページ"
                    "liked" -> "${daysSince}日未アクセスのいいね"
                    "ai_conv" -> "${daysSince}日未アクセスのAI会話"
                    else -> "${daysSince}日未アクセス"
                }

                CleanupSuggestion(
                    entry = e,
                    daysSinceAccess = daysSince,
                    reason = reason
                )
            }
            .sortedByDescending { it.daysSinceAccess }
            .take(limit)
    }
}