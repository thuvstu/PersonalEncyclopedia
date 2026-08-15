package com.thuvstu.personalencyclopedia.importer

import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §12.7 重複排除アーキテクチャ。
 *
 * URL・メモ・クイズ・CSV・Obsidian の各インポート経路で同一内容が重複登録されるのを防ぐため、
 * 経路ごとに個別実装せず、この共通インターフェースで吸収する。
 * 各インポートパイプライン(§12.1)の正規化ステップに差し込む。
 */
interface DuplicateDetector {
    /** 候補と重複する既存entryを返す。無ければ null。 */
    suspend fun findDuplicate(candidate: ImportCandidate): EntryEntity?
}

/** §12.7 インポート候補の正規化表現（EntryCreateRequest相当）。 */
data class ImportCandidate(
    val title: String,
    val type: String = "thought",
    val content: String? = null,
    val sourceUrl: String? = null
)

/** URL完全一致で判定（webpage取り込み）。 */
@Singleton
class UrlDuplicateDetector @Inject constructor(
    private val entryDao: EntryDao
) : DuplicateDetector {
    override suspend fun findDuplicate(candidate: ImportCandidate): EntryEntity? {
        val url = candidate.sourceUrl?.trim()?.takeIf { it.startsWith("http") } ?: return null
        return entryDao.findBySourceUrl(url)
    }
}

/**
 * メモ・定義・JSON等、URLを持たない型の正規化ハッシュで判定。
 * まずタイトル一致で候補を絞り、本文の正規化文字列を比較する（スキーマ変更なしの軽量方式）。
 */
@Singleton
class ContentHashDuplicateDetector @Inject constructor(
    private val entryDao: EntryDao
) : DuplicateDetector {

    override suspend fun findDuplicate(candidate: ImportCandidate): EntryEntity? {
        val title = candidate.title.trim()
        if (title.isBlank()) return null
        val existing = entryDao.findByTitle(title) ?: return null

        // 本文が無い候補はタイトル一致だけで重複とみなす
        val candidateBody = normalize(candidate.content ?: "")
        if (candidateBody.isBlank()) return existing

        return existing.takeIf { normalize(it.content ?: "") == candidateBody }
    }

    /** 全角/半角空白・改行・タブを潰して比較しやすくする。 */
    private fun normalize(text: String): String =
        text.replace(Regex("\\s+"), "").trim()
}
