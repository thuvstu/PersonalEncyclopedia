package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Wikipediaビルダー（§11.6）。
 * エントリーを記事として編纂する。
 */
@Entity(
    tableName = "wiki_article",
    indices = [Index("title", unique = true)]
)
data class WikiArticleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val contentMd: String,   // Markdown + KaTeX + [[wiki-link]] ソース
    val summary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)