package com.thuvstu.personalencyclopedia.importer

import com.thuvstu.personalencyclopedia.brain.connection.ConnectionEngine
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryThoughtEntity
import com.thuvstu.personalencyclopedia.db.dao.EntryThoughtDao
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Obsidian インポーター (§12.3).
 * Markdown ファイル内の [[wiki-link]] を解析し、エントリー生成および参照接続を作成する。
 */
@Singleton
class ObsidianImporter @Inject constructor(
    private val entryDao: EntryDao,
    private val thoughtDao: EntryThoughtDao,
    private val connectionEngine: ConnectionEngine
) {
    data class ObsidianNote(
        val title: String,
        val content: String,
        val wikiLinks: List<String>
    )

    data class ImportResult(
        val createdEntries: Int,
        val createdConnections: Int
    )

    fun parseMarkdown(title: String, content: String): ObsidianNote {
        val parsed = WikiLinkParser.parse(title, content)
        return ObsidianNote(title = parsed.title, content = parsed.content, wikiLinks = parsed.wikiLinks)
    }

    suspend fun importNotes(notes: List<ObsidianNote>): ImportResult {
        val now = System.currentTimeMillis()
        val titleToIdMap = mutableMapOf<String, String>()
        var createdEntriesCount = 0
        var createdConnectionsCount = 0

        // 1st pass: create entries
        for (note in notes) {
            val existing = entryDao.getById(note.title) // Check or create
            val id = existing?.id ?: UUID.randomUUID().toString()

            if (existing == null) {
                entryDao.insert(
                    EntryEntity(
                        id = id,
                        type = "thought",
                        title = note.title,
                        content = note.content,
                        createdAt = now,
                        updatedAt = now,
                        accessedAt = now
                    )
                )
                thoughtDao.insert(EntryThoughtEntity(entryId = id))
                createdEntriesCount++
            }
            titleToIdMap[note.title] = id
        }

        // 2nd pass: create connections for [[wiki-link]]
        for (note in notes) {
            val sourceId = titleToIdMap[note.title] ?: continue
            for (linkTitle in note.wikiLinks) {
                val targetId = titleToIdMap[linkTitle]
                if (targetId != null && targetId != sourceId) {
                    val connId = connectionEngine.createManualConnection(
                        entryAId = sourceId,
                        entryBId = targetId,
                        relationType = "references",
                        note = "Obsidian [[wiki-link]] インポート"
                    )
                    if (connId != null) createdConnectionsCount++
                }
            }
        }

        return ImportResult(
            createdEntries = createdEntriesCount,
            createdConnections = createdConnectionsCount
        )
    }
}
