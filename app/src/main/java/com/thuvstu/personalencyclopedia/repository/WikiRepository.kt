package com.thuvstu.personalencyclopedia.repository

import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.WikiArticleDao
import com.thuvstu.personalencyclopedia.db.entity.WikiArticleEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WikiRepository @Inject constructor(
    private val wikiDao: WikiArticleDao,
    private val entryDao: EntryDao,
    private val definitionDao: EntryDefinitionDao
) {
    fun observeAll(): Flow<List<WikiArticleEntity>> = wikiDao.observeAll()
    fun observeById(id: String): Flow<WikiArticleEntity?> = wikiDao.observeById(id)
    fun observeCount(): Flow<Int> = wikiDao.observeCount()

    suspend fun findByTitle(title: String): WikiArticleEntity? = wikiDao.findByTitle(title)
    suspend fun search(q: String): List<WikiArticleEntity> = wikiDao.search(q)

    suspend fun save(title: String, contentMd: String, summary: String? = null, id: String? = null): String {
        val now = System.currentTimeMillis()
        val article = WikiArticleEntity(
            id = id ?: UUID.randomUUID().toString(),
            title = title.trim(),
            contentMd = contentMd,
            summary = summary,
            createdAt = now,
            updatedAt = now
        )
        wikiDao.upsert(article)
        return article.id
    }

    suspend fun delete(id: String) = wikiDao.delete(id)

    /**
     * エントリー → 記事のひな形を自動生成（§11.6 draftFromEntry）。
     * 定義・メモ・型情報を統合。
     */
    suspend fun draftFromEntry(entryId: String): String? {
        val e = entryDao.getById(entryId) ?: return null
        wikiDao.findByTitle(e.title)?.let { return it.id }   // 既存ならそれを返す

        val sb = StringBuilder()
        sb.appendLine("# ${e.title}")
        sb.appendLine()
        e.summary?.let { sb.appendLine("> $it").appendLine() }

        val def = definitionDao.getByEntryId(entryId)
        if (def != null) {
            sb.appendLine("**${def.term}**（${def.reading ?: ""}）は、${def.definition}")
            def.field?.let { sb.appendLine("\n- 分野: $it") }
        } else {
            e.content?.let { sb.appendLine(it).appendLine() }
        }

        sb.appendLine("## 関連項目")
        sb.appendLine()
        sb.appendLine("- [[ ]]")   // wiki-link の書き方ヒント

        val now = System.currentTimeMillis()
        val article = WikiArticleEntity(
            title = e.title,
            contentMd = sb.toString(),
            summary = e.summary ?: def?.definition?.take(100),
            createdAt = now,
            updatedAt = now
        )
        wikiDao.upsert(article)
        return article.id
    }
}