package com.thuvstu.personalencyclopedia.brain.search

import com.thuvstu.personalencyclopedia.brain.search.SearchRefiner.Criteria
import com.thuvstu.personalencyclopedia.brain.search.SearchRefiner.Period
import com.thuvstu.personalencyclopedia.brain.search.SearchRefiner.SortKey
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ★mismatch §3.4: 並べ替え・絞り込みの純粋関数テスト */
class SearchRefinerTest {

    private val day = 86_400_000L
    private val now = 1_000L * day

    private fun e(id: String, type: String = "thought", fav: Boolean = false, created: Long, updated: Long = created, title: String = id) =
        EntryEntity(id = id, type = type, title = title, isFavorite = fav, createdAt = created, updatedAt = updated)

    // 関連度順: a(古い・定義・お気に入り) → b(新しい・メモ) → c(中間・メモ・お気に入り)
    private val ranked = listOf(
        e("a", type = "definition", fav = true, created = now - 400 * day, updated = now - 200 * day, title = "beta"),
        e("b", created = now - 2 * day, title = "alpha"),
        e("c", fav = true, created = now - 20 * day, updated = now - 1 * day, title = "Gamma")
    )

    @Test
    fun defaultCriteriaKeepsRelevanceOrder() {
        assertTrue(Criteria().isDefault)
        assertEquals(listOf("a", "b", "c"), SearchRefiner.apply(ranked, Criteria(), now = now).map { it.id })
    }

    @Test
    fun sortKeys() {
        assertEquals(listOf("c", "b", "a"), SearchRefiner.apply(ranked, Criteria(sort = SortKey.UPDATED_DESC), now = now).map { it.id })
        assertEquals(listOf("b", "c", "a"), SearchRefiner.apply(ranked, Criteria(sort = SortKey.CREATED_DESC), now = now).map { it.id })
        assertEquals(listOf("a", "c", "b"), SearchRefiner.apply(ranked, Criteria(sort = SortKey.CREATED_ASC), now = now).map { it.id })
        assertEquals(listOf("b", "a", "c"), SearchRefiner.apply(ranked, Criteria(sort = SortKey.TITLE_ASC), now = now).map { it.id })
    }

    @Test
    fun typeFavoriteAndPeriodFilters() {
        assertEquals(listOf("a"), SearchRefiner.apply(ranked, Criteria(type = "definition"), now = now).map { it.id })
        assertEquals(listOf("a", "c"), SearchRefiner.apply(ranked, Criteria(favoritesOnly = true), now = now).map { it.id })
        // 期間は updatedAt 基準: 1週間以内 = b(2日前), c(1日前)
        assertEquals(listOf("b", "c"), SearchRefiner.apply(ranked, Criteria(period = Period.WEEK), now = now).map { it.id })
        // 1か月以内 = b, c(a は updated 200日前)。1年以内 = 全件
        assertEquals(listOf("b", "c"), SearchRefiner.apply(ranked, Criteria(period = Period.MONTH), now = now).map { it.id })
        assertEquals(listOf("a", "b", "c"), SearchRefiner.apply(ranked, Criteria(period = Period.YEAR), now = now).map { it.id })
    }

    @Test
    fun tagFilterIsAndAndCaseInsensitive() {
        val tags = mapOf("a" to setOf("CS", "Math"), "b" to setOf("cs"), "c" to emptySet())
        assertEquals(listOf("a", "b"), SearchRefiner.apply(ranked, Criteria(tags = setOf("cs")), tags, now).map { it.id })
        assertEquals(listOf("a"), SearchRefiner.apply(ranked, Criteria(tags = setOf("cs", "math")), tags, now).map { it.id })
        assertTrue(SearchRefiner.apply(ranked, Criteria(tags = setOf("none")), tags, now).isEmpty())
    }

    @Test
    fun combinedCriteriaApplyInOrder() {
        // お気に入り + 更新順 → c, a
        val r = SearchRefiner.apply(ranked, Criteria(favoritesOnly = true, sort = SortKey.UPDATED_DESC), now = now)
        assertEquals(listOf("c", "a"), r.map { it.id })
    }
}
