package com.thuvstu.personalencyclopedia.brain.search

import com.thuvstu.personalencyclopedia.db.entity.EntryEntity

/**
 * ★検索の並べ替え・絞り込み(mismatch §3.4「ソート固定・複合条件なし」の解消)。
 *
 * 検索エンジン(`HybridSearchEngine`)は関連度順の候補を返すだけにし、
 * その後段で本オブジェクトが「ユーザーが選んだ条件」を純粋関数として適用する。
 * DBに触れないのでJVMテスト可能(`SearchRefinerTest`)。
 *
 * 適用順: 型 → お気に入り → 期間 → タグ(AND) → 並べ替え。
 */
object SearchRefiner {

    enum class SortKey(val label: String) {
        RELEVANCE("関連度"),
        UPDATED_DESC("更新が新しい"),
        CREATED_DESC("作成が新しい"),
        CREATED_ASC("作成が古い"),
        TITLE_ASC("タイトル順")
    }

    /** 期間フィルタ(updatedAt 基準)。`days=null` は無制限 */
    enum class Period(val label: String, val days: Int?) {
        ANY("全期間", null),
        WEEK("1週間", 7),
        MONTH("1か月", 30),
        YEAR("1年", 365)
    }

    data class Criteria(
        val type: String? = null,
        val favoritesOnly: Boolean = false,
        val period: Period = Period.ANY,
        /** 全て含むこと(AND)。名前一致・大文字小文字は区別しない */
        val tags: Set<String> = emptySet(),
        val sort: SortKey = SortKey.RELEVANCE
    ) {
        val isDefault: Boolean
            get() = type == null && !favoritesOnly && period == Period.ANY && tags.isEmpty() && sort == SortKey.RELEVANCE
    }

    /**
     * @param ranked 関連度順(先頭が最上位)の候補
     * @param tagsByEntry entryId → タグ名集合(タグ条件が空なら参照しない)
     * @param now 期間判定の基準時刻(テストで固定できるよう引数化)
     */
    fun apply(
        ranked: List<EntryEntity>,
        criteria: Criteria,
        tagsByEntry: Map<String, Set<String>> = emptyMap(),
        now: Long = System.currentTimeMillis()
    ): List<EntryEntity> {
        var list = ranked.asSequence()
        criteria.type?.let { t -> list = list.filter { it.type == t } }
        if (criteria.favoritesOnly) list = list.filter { it.isFavorite }
        criteria.period.days?.let { d ->
            val since = now - d * 86_400_000L
            list = list.filter { it.updatedAt >= since }
        }
        if (criteria.tags.isNotEmpty()) {
            val want = criteria.tags.map { it.lowercase() }.toSet()
            list = list.filter { e ->
                val have = tagsByEntry[e.id]?.map { it.lowercase() }?.toSet() ?: emptySet()
                have.containsAll(want)
            }
        }
        val filtered = list.toList()
        return when (criteria.sort) {
            SortKey.RELEVANCE -> filtered
            SortKey.UPDATED_DESC -> filtered.sortedByDescending { it.updatedAt }
            SortKey.CREATED_DESC -> filtered.sortedByDescending { it.createdAt }
            SortKey.CREATED_ASC -> filtered.sortedBy { it.createdAt }
            SortKey.TITLE_ASC -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
    }
}
