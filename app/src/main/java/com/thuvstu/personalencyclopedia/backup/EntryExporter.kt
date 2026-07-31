package com.thuvstu.personalencyclopedia.backup

import android.content.Context
import android.net.Uri
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryExtensionDao
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
    private val tagDao: TagDao
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
                put("isFavorite", e.isFavorite)
                put("isMuted", e.isMuted)
                put("createdAt", e.createdAt)
                put("updatedAt", e.updatedAt)

                val tags = tagDao.observeTagsForEntry(e.id).first()
                if (tags.isNotEmpty()) {
                    putJsonArray("tags") { tags.forEach { add(it.name) } }
                }

                val ext: JsonElement? = when (e.type) {
                    "definition" -> definitionDao.getByEntryId(e.id)?.let {
                        buildJsonObject {
                            put("term", it.term)
                            put("definition", it.definition)
                            it.reading?.let { v -> put("reading", v) }
                            it.field?.let { v -> put("field", v) }
                        }
                    }
                    "webpage" -> extensionDao.getWebpage(e.id)?.let {
                        buildJsonObject {
                            put("url", it.url)
                            put("domain", it.domain)
                            it.fullText?.let { v -> put("fullText", v) }
                        }
                    }
                    "book" -> extensionDao.getBook(e.id)?.let {
                        buildJsonObject {
                            put("isbn", it.isbn)
                            put("readStatus", it.readStatus)
                            put("authorsJson", it.authorsJson)
                        }
                    }
                    "person" -> extensionDao.getPerson(e.id)?.let {
                        buildJsonObject {
                            put("fullName", it.fullName)
                            put("occupationsJson", it.occupationsJson)
                        }
                    }
                    "event" -> extensionDao.getEvent(e.id)?.let {
                        buildJsonObject {
                            put("eventName", it.eventName)
                            put("startedAt", it.startedAt)
                        }
                    }
                    "place" -> extensionDao.getPlace(e.id)?.let {
                        buildJsonObject {
                            put("placeName", it.placeName)
                            it.latitude?.let { v -> put("latitude", v) }
                            it.longitude?.let { v -> put("longitude", v) }
                        }
                    }
                    else -> null
                }
                ext?.let { put("extension", it) }
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