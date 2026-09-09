package com.thuvstu.personalencyclopedia.brain.search

import com.thuvstu.personalencyclopedia.db.entity.*

/**
 * Builds the combined text for search_document and embedding input.
 * Strategy per entry type (§7.1.2).
 */
object EmbeddingTextBuilder {

    /**
     * @param stickyNotes ★wt56: 付箋本文。末尾に連結して FTS/意味検索の対象にする。
     *   2000字上限は本文が優先されるよう、付箋は合計400字までに切り詰める。
     */
    fun build(entry: EntryEntity, extension: Any?, stickyNotes: List<String> = emptyList()): String {
        val parts = mutableListOf<String>()
        parts.add(entry.title)
        entry.content?.let { if (it.isNotBlank()) parts.add(it) }
        entry.summary?.let { if (it.isNotBlank()) parts.add(it) }

        when (entry.type) {
            "webpage" -> (extension as? EntryWebpageEntity)?.let { ext ->
                ext.author?.let { parts.add(it) }
                ext.fullText?.let { parts.add(it.take(1500)) }
            }
            "thought" -> { /* title + content already added */ }
            "book" -> (extension as? EntryBookEntity)?.let { ext ->
                // authorsJson is a JSON array string
                try {
                    val authors = kotlinx.serialization.json.Json.parseToJsonElement(ext.authorsJson)
                    parts.add(authors.toString().replace(Regex("[\\[\\]\"]"), ""))
                } catch (_: Exception) {}
            }
            "video" -> (extension as? EntryVideoEntity)?.let { ext ->
                ext.channelName?.let { parts.add(it) }
                ext.transcript?.let { parts.add(it.take(1000)) }
            }
            "document" -> (extension as? EntryDocumentEntity)?.let { ext ->
                ext.extractedText?.let { parts.add(it.take(1500)) }
            }
            "definition" -> (extension as? EntryDefinitionEntity)?.let { ext ->
                parts.add(ext.definition)
                ext.reading?.let { parts.add(it) }
            }
            "person" -> (extension as? EntryPersonEntity)?.let { ext ->
                try {
                    val occ = kotlinx.serialization.json.Json.parseToJsonElement(ext.occupationsJson)
                    parts.add(occ.toString().replace(Regex("[\\[\\]\"]"), ""))
                } catch (_: Exception) {}
                ext.biography?.let { parts.add(it.take(300)) }
            }
            "place" -> (extension as? EntryPlaceEntity)?.let { ext ->
                ext.placeType?.let { parts.add(it) }
                ext.address?.let { parts.add(it) }
            }
            "event" -> (extension as? EntryEventEntity)?.let { ext ->
                ext.locationText?.let { parts.add(it) }
            }
            "ai_conv" -> (extension as? EntryAiConvEntity)?.let { ext ->
                ext.topic?.let { parts.add(it) }
            }
            "liked" -> (extension as? EntryLikedEntity)?.let { ext ->
                ext.authorName?.let { parts.add(it) }
                ext.fullText?.let { parts.add(it.take(500)) }
            }
        }

        val body = parts.filter { it.isNotBlank() }.joinToString("\n").take(1600)
        val notes = stickyNotes.map { it.trim() }.filter { it.isNotEmpty() }
        if (notes.isEmpty()) return body.take(2000)
        val noteText = notes.joinToString("\n").take(400)
        return (body + "\n" + noteText).take(2000)
    }
}