package com.thuvstu.personalencyclopedia.backup

import android.content.Context
import android.net.Uri
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryExtensionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryThoughtDao
import com.thuvstu.personalencyclopedia.db.dao.EntryStickyNoteDao
import com.thuvstu.personalencyclopedia.db.dao.TagDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DB管理画面からの手動エクスポート（§6.3 可搬バックアップ）。
 * SAF CreateDocument で取得した Uri へ直接書き込むため権限不要。
 */
enum class ExportFormat(val label: String) {
    MARKDOWN("Markdown"),
    CSV("CSV(単語帳)"),
    JSON("JSON")
}

@Singleton
class EntryExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryDao: EntryDao,
    private val extensionDao: EntryExtensionDao,
    private val definitionDao: EntryDefinitionDao,
    private val tagDao: TagDao,
    private val thoughtDao: EntryThoughtDao,
    private val stickyNoteDao: EntryStickyNoteDao   // ★wt56: 付箋も可搬exportに含める
) {
    /**
     * エクスポート本体。呼び出し元（DatabaseManagementViewModel）は suspend コンテキスト。
     */
    suspend fun export(uri: Uri, format: ExportFormat): Int = withContext(Dispatchers.IO) {
        val entries = entryDao.observeAll(limit = 100_000).first()
        val text = when (format) {
            ExportFormat.MARKDOWN -> buildMarkdown(entries)
            ExportFormat.CSV -> buildCsv()
            ExportFormat.JSON -> buildJson(entries)
        }
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
        } ?: throw java.io.IOException("出力ストリームを開けません")
        entries.size
    }

    // ── Markdown ──────────────────────────────────────────────

    private suspend fun buildMarkdown(entries: List<EntryEntity>): String {
        val sb = StringBuilder()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        sb.appendLine("# Personal Encyclopedia — Export")
        sb.appendLine()
        sb.appendLine("- 出力日時: $now")
        sb.appendLine("- エントリー数: ${entries.size}")
        sb.appendLine()

        for (e in entries) {
            val tags = tagDao.observeTagsForEntry(e.id).first()
            sb.appendLine("---")
            sb.appendLine()
            sb.appendLine("## [${e.type}] ${e.title}")
            sb.appendLine()
            sb.appendLine("- ID: `${e.id}`")
            sb.appendLine("- 作成: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(e.createdAt))}")
            if (tags.isNotEmpty()) {
                sb.appendLine("- タグ: ${tags.joinToString(", ") { it.name }}")
            }
            e.sourceUrl?.let { sb.appendLine("- ソース: $it") }
            if (!e.content.isNullOrBlank()) {
                sb.appendLine()
                sb.appendLine(e.content)
            }
            appendTypeDetails(sb, e)
            // ★wt56 付箋
            val notes = stickyNoteDao.getByEntryId(e.id)
            if (notes.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("### 📝 付箋")
                for (n in notes) {
                    val mark = if (n.isResolved) "[x]" else "[ ]"
                    val pin = if (n.isPinned) " 📌" else ""
                    sb.appendLine("- $mark$pin ${n.text.replace("\n", "  \n  ")}")
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    private suspend fun appendTypeDetails(sb: StringBuilder, e: EntryEntity) {
        when (e.type) {
            "definition" -> definitionDao.getByEntryId(e.id)?.let {
                sb.appendLine()
                sb.appendLine("**${it.term}** (${it.reading ?: ""}) — ${it.definition}")
                it.field?.let { f -> sb.appendLine("- 分野: $f") }
            }
            "webpage" -> extensionDao.getWebpage(e.id)?.let {
                sb.appendLine("- URL: ${it.url}")
                it.author?.let { a -> sb.appendLine("- 著者: $a") }
                it.fullText?.let { t ->
                    sb.appendLine()
                    sb.appendLine("> ${t.take(800)}")
                }
            }
            "book" -> extensionDao.getBook(e.id)?.let {
                sb.appendLine("- 著者: ${parseList(it.authorsJson).joinToString(" / ")}")
                it.isbn?.let { v -> sb.appendLine("- ISBN: $v") }
                it.publisher?.let { v -> sb.appendLine("- 出版社: $v") }
                sb.appendLine("- ステータス: ${it.readStatus}")
            }
            "person" -> extensionDao.getPerson(e.id)?.let {
                sb.appendLine("- 職業: ${parseList(it.occupationsJson).joinToString(", ")}")
                it.biography?.let { b ->
                    sb.appendLine()
                    sb.appendLine("> ${b.take(500)}")
                }
            }
            "place" -> extensionDao.getPlace(e.id)?.let {
                it.address?.let { a -> sb.appendLine("- 住所: $a") }
                if (it.latitude != null) {
                    sb.appendLine("- 座標: ${it.latitude}, ${it.longitude}")
                }
            }
            "event" -> extensionDao.getEvent(e.id)?.let {
                sb.appendLine("- 開始: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.startedAt))}")
                it.locationText?.let { l -> sb.appendLine("- 開催地: $l") }
            }
            "video" -> extensionDao.getVideo(e.id)?.let {
                sb.appendLine("- プラットフォーム: ${it.platform}")
                it.channelName?.let { c -> sb.appendLine("- チャンネル: $c") }
            }
            "org" -> extensionDao.getOrg(e.id)?.let {
                it.websiteUrl?.let { u -> sb.appendLine("- Web: $u") }
                it.description?.let { d -> sb.appendLine("- 説明: $d") }
            }
            "ai_conv" -> extensionDao.getAiConv(e.id)?.let {
                sb.appendLine("- モデル: ${it.model} (${it.provider})")
            }
            "liked" -> extensionDao.getLiked(e.id)?.let {
                sb.appendLine("- プラットフォーム: ${it.platform}")
                it.authorName?.let { a -> sb.appendLine("- 作者: $a") }
            }
        }
    }

    // ── CSV（definitionDao.search().first() を呼ぶため suspend）──

    private suspend fun buildCsv(): String {
        val defs = definitionDao.search("", limit = 100_000).first()
        val sb = StringBuilder("term,reading,definition,field\n")
        defs.forEach { d ->
            sb.appendLine("${esc(d.term)},${esc(d.reading ?: "")},${esc(d.definition)},${esc(d.field ?: "")}")
        }
        return sb.toString()
    }

    // ── JSON ──────────────────────────────────────────────────

    private suspend fun buildJson(entries: List<EntryEntity>): String {
        val arr = JsonArray(entries.map { e ->
            buildJsonObject {
                put("id", e.id)
                put("type", e.type)
                put("title", e.title)
                e.content?.let { put("content", it) }
                e.summary?.let { put("summary", it) }
                e.sourceUrl?.let { put("sourceUrl", it) }
                e.lang?.let { put("lang", it) }
                put("isFavorite", e.isFavorite)
                put("isMuted", e.isMuted)
                put("metadataJson", e.metadataJson)
                e.accessedAt?.let { put("accessedAt", it) }
                put("createdAt", e.createdAt)
                put("updatedAt", e.updatedAt)

                val tags = tagDao.observeTagsForEntry(e.id).first()
                if (tags.isNotEmpty()) {
                    putJsonArray("tags") { tags.forEach { add(it.name) } }
                }

                // ★往復対称(mismatch §6-2): 11型すべての拡張を全カラム書き出す。
                // ImportPipeline.importEntriesJson が同じキーで復元する。片側だけ増やさないこと。
                val ext: JsonElement? = when (e.type) {
                    "definition" -> definitionDao.getByEntryId(e.id)?.let {
                        buildJsonObject {
                            put("term", it.term)
                            put("definition", it.definition)
                            it.reading?.let { v -> put("reading", v) }
                            it.field?.let { v -> put("field", v) }
                            put("examplesJson", it.examplesJson)
                            put("relatedTermsJson", it.relatedTermsJson)
                        }
                    }
                    "thought" -> thoughtDao.getByEntryId(e.id)?.let {
                        buildJsonObject {
                            it.mood?.let { v -> put("mood", v) }
                            it.context?.let { v -> put("context", v) }
                            put("isDraft", it.isDraft)
                        }
                    }
                    "webpage" -> extensionDao.getWebpage(e.id)?.let {
                        buildJsonObject {
                            put("url", it.url)
                            put("domain", it.domain)
                            it.scrapedAt?.let { v -> put("scrapedAt", v) }
                            it.fullText?.let { v -> put("fullText", v) }
                            it.readingTimeS?.let { v -> put("readingTimeS", v) }
                            it.author?.let { v -> put("author", v) }
                            it.publishedAt?.let { v -> put("publishedAt", v) }
                            it.scraperUsed?.let { v -> put("scraperUsed", v) }
                        }
                    }
                    "book" -> extensionDao.getBook(e.id)?.let {
                        buildJsonObject {
                            it.isbn?.let { v -> put("isbn", v) }
                            put("authorsJson", it.authorsJson)
                            it.publisher?.let { v -> put("publisher", v) }
                            it.publishedYear?.let { v -> put("publishedYear", v) }
                            it.totalPages?.let { v -> put("totalPages", v) }
                            put("readStatus", it.readStatus)
                            it.readStartDate?.let { v -> put("readStartDate", v) }
                            it.readEndDate?.let { v -> put("readEndDate", v) }
                            it.rating?.let { v -> put("rating", v) }
                        }
                    }
                    "video" -> extensionDao.getVideo(e.id)?.let {
                        buildJsonObject {
                            put("platform", it.platform)
                            it.videoId?.let { v -> put("videoId", v) }
                            it.channelName?.let { v -> put("channelName", v) }
                            it.durationS?.let { v -> put("durationS", v) }
                            it.thumbnailUrl?.let { v -> put("thumbnailUrl", v) }
                            it.transcript?.let { v -> put("transcript", v) }
                            it.watchedAt?.let { v -> put("watchedAt", v) }
                            it.watchProgress?.let { v -> put("watchProgress", v) }
                        }
                    }
                    "document" -> extensionDao.getDocument(e.id)?.let {
                        buildJsonObject {
                            put("docType", it.docType)
                            put("mimeType", it.mimeType)
                            it.fileSizeBytes?.let { v -> put("fileSizeBytes", v) }
                            it.pageCount?.let { v -> put("pageCount", v) }
                            it.extractedText?.let { v -> put("extractedText", v) }
                            it.extractionMethod?.let { v -> put("extractionMethod", v) }
                        }
                    }
                    "media" -> extensionDao.getMedia(e.id)?.let {
                        buildJsonObject {
                            put("mediaType", it.mediaType)
                            put("blobPath", it.blobPath)
                            put("mimeType", it.mimeType)
                            it.widthPx?.let { v -> put("widthPx", v) }
                            it.heightPx?.let { v -> put("heightPx", v) }
                            it.durationS?.let { v -> put("durationS", v) }
                            it.ocrText?.let { v -> put("ocrText", v) }
                            it.caption?.let { v -> put("caption", v) }
                        }
                    }
                    "person" -> extensionDao.getPerson(e.id)?.let {
                        buildJsonObject {
                            put("fullName", it.fullName)
                            put("aliasesJson", it.aliasesJson)
                            it.birthYear?.let { v -> put("birthYear", v) }
                            it.deathYear?.let { v -> put("deathYear", v) }
                            it.nationality?.let { v -> put("nationality", v) }
                            put("occupationsJson", it.occupationsJson)
                            it.biography?.let { v -> put("biography", v) }
                        }
                    }
                    "org" -> extensionDao.getOrg(e.id)?.let {
                        buildJsonObject {
                            put("officialName", it.officialName)
                            it.orgType?.let { v -> put("orgType", v) }
                            it.foundedYear?.let { v -> put("foundedYear", v) }
                            it.country?.let { v -> put("country", v) }
                            it.websiteUrl?.let { v -> put("websiteUrl", v) }
                            it.description?.let { v -> put("description", v) }
                        }
                    }
                    "place" -> extensionDao.getPlace(e.id)?.let {
                        buildJsonObject {
                            put("placeName", it.placeName)
                            it.placeType?.let { v -> put("placeType", v) }
                            it.address?.let { v -> put("address", v) }
                            it.latitude?.let { v -> put("latitude", v) }
                            it.longitude?.let { v -> put("longitude", v) }
                            put("visitedDatesJson", it.visitedDatesJson)
                        }
                    }
                    "event" -> extensionDao.getEvent(e.id)?.let {
                        buildJsonObject {
                            put("eventName", it.eventName)
                            put("startedAt", it.startedAt)
                            it.endedAt?.let { v -> put("endedAt", v) }
                            it.locationText?.let { v -> put("locationText", v) }
                            it.placeEntryId?.let { v -> put("placeEntryId", v) }
                            put("isPersonal", it.isPersonal)
                            put("participantsJson", it.participantsJson)
                        }
                    }
                    "liked" -> extensionDao.getLiked(e.id)?.let {
                        buildJsonObject {
                            put("platform", it.platform)
                            put("originalId", it.originalId)
                            it.likedAt?.let { v -> put("likedAt", v) }
                            put("contentType", it.contentType)
                            it.authorName?.let { v -> put("authorName", v) }
                            it.fullText?.let { v -> put("fullText", v) }
                        }
                    }
                    "ai_conv" -> extensionDao.getAiConv(e.id)?.let {
                        buildJsonObject {
                            put("model", it.model)
                            put("provider", it.provider)
                            put("messagesJson", it.messagesJson)
                            it.tokenCount?.let { v -> put("tokenCount", v) }
                            it.topic?.let { v -> put("topic", v) }
                            it.isUseful?.let { v -> put("isUseful", v) }
                        }
                    }
                    else -> null
                }
                ext?.let { put("extension", it) }

                // ★wt56 付箋（往復対称: EntryJsonCodec.decode が同じキーで復元する）
                val notes = stickyNoteDao.getByEntryId(e.id)
                if (notes.isNotEmpty()) {
                    putJsonArray("stickyNotes") {
                        notes.forEach { n ->
                            add(buildJsonObject {
                                put("id", n.id)
                                put("text", n.text)
                                put("color", n.color)
                                put("source", n.source)
                                n.contextId?.let { put("contextId", it) }
                                put("isPinned", n.isPinned)
                                put("isResolved", n.isResolved)
                                n.resolvedAt?.let { put("resolvedAt", it) }
                                put("sortOrder", n.sortOrder)
                                n.promotedEntryId?.let { put("promotedEntryId", it) }
                                n.promotedTaskId?.let { put("promotedTaskId", it) }
                                n.promotedCandidateId?.let { put("promotedCandidateId", it) }
                                put("createdAt", n.createdAt)
                                put("updatedAt", n.updatedAt)
                            })
                        }
                    }
                }
            }
        })
        return Json { prettyPrint = true }
            .encodeToString(JsonElement.serializer(), arr)
    }

    // ── ヘルパー ──────────────────────────────────────────────

    private fun esc(v: String): String =
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r"))
            "\"${v.replace("\"", "\"\"")}\"" else v

    private fun parseList(json: String?): List<String> = try {
        Json.parseToJsonElement(json ?: "[]").jsonArray.map { it.jsonPrimitive.content }
    } catch (_: Exception) {
        emptyList()
    }
}