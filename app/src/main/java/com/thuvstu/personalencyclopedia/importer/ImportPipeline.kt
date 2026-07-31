package com.thuvstu.personalencyclopedia.importer

import android.content.Context
import android.net.Uri
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryThoughtDao
import com.thuvstu.personalencyclopedia.db.entity.EntryDefinitionEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryThoughtEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.*

@Singleton
class ImportPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryDao: EntryDao,
    private val thoughtDao: EntryThoughtDao,
    private val webScraper: WebScraper,
    private val definitionDao: EntryDefinitionDao,
    private val obsidianImporter: ObsidianImporter
) {
    data class ImportResult(
        val successCount: Int,
        val errorCount: Int,
        val errors: List<String> = emptyList()
    )

    suspend fun importDefinitionsCsv(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult(0, 1, listOf("Cannot open file"))

            val lines = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                reader.readLines()
            }

            if (lines.isEmpty()) return ImportResult(0, 0)

            val header = lines.first()
            val columns = parseCsvLine(header).map { it.trim().lowercase() }

            val termIdx = columns.indexOfFirst { it in listOf("term", "用語", "単語", "front") }
            val readingIdx = columns.indexOfFirst { it in listOf("reading", "読み", "ふりがな") }
            val defIdx = columns.indexOfFirst { it in listOf("definition", "定義", "意味", "back") }
            val fieldIdx = columns.indexOfFirst { it in listOf("field", "分野", "ジャンル", "category") }

            if (termIdx < 0 || defIdx < 0) {
                return ImportResult(0, 1, listOf("CSV must have 'term' and 'definition' columns"))
            }

            for (lineNum in 1 until lines.size) {
                val line = lines[lineNum]
                if (line.isBlank()) continue
                try {
                    val cols = parseCsvLine(line)
                    val term = cols.getOrNull(termIdx)?.trim() ?: ""
                    val definition = cols.getOrNull(defIdx)?.trim() ?: ""
                    if (term.isBlank() || definition.isBlank()) {
                        errors.add("Line ${lineNum + 1}: missing term or definition")
                        continue
                    }

                    val id = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()

                    entryDao.insert(
                        EntryEntity(
                            id = id,
                            type = "definition",
                            title = term,
                            createdAt = now,
                            updatedAt = now,
                            accessedAt = now
                        )
                    )
                    definitionDao.insert(
                        EntryDefinitionEntity(
                            entryId = id,
                            term = term,
                            reading = cols.getOrNull(readingIdx)?.trim()?.takeIf { it.isNotBlank() },
                            definition = definition,
                            field = cols.getOrNull(fieldIdx)?.trim()?.takeIf { it.isNotBlank() }
                        )
                    )
                    success++
                } catch (e: Exception) {
                    errors.add("Line ${lineNum + 1}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            errors.add("File error: ${e.message}")
        }

        return ImportResult(success, errors.size, errors)
    }

    suspend fun importMarkdown(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult(0, 1, listOf("Cannot open file"))

            val content = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use {
                it.readText()
            }

            val sections = content.split(Regex("^#{1,2}\\s+", RegexOption.MULTILINE))
                .filter { it.isNotBlank() }

            for (section in sections) {
                val lines = section.lines()
                val title = lines.firstOrNull()?.trim()?.take(200) ?: "Untitled"
                val body = lines.drop(1).joinToString("\n").trim()

                if (title.isBlank()) continue

                val id = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                entryDao.insert(
                    EntryEntity(
                        id = id,
                        type = "thought",
                        title = title,
                        content = body.takeIf { it.isNotBlank() },
                        createdAt = now,
                        updatedAt = now,
                        accessedAt = now
                    )
                )
                thoughtDao.insert(
                    EntryThoughtEntity(entryId = id, context = "markdown_import")
                )
                success++
            }
        } catch (e: Exception) {
            errors.add("File error: ${e.message}")
        }

        return ImportResult(success, errors.size, errors)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')  // エスケープされた ""
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }
    suspend fun importEntriesJson(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0
        try {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            } ?: return ImportResult(0, 1, listOf("Cannot open file"))

            val arr = Json.parseToJsonElement(text).jsonArray
            for ((i, el) in arr.withIndex()) {
                try {
                    val obj = el.jsonObject
                    val type = obj["type"]?.jsonPrimitive?.content ?: "thought"
                    val title = obj["title"]?.jsonPrimitive?.content ?: continue
                    val content = obj["content"]?.jsonPrimitive?.content
                    val id = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    entryDao.insert(
                        EntryEntity(id = id, type = type, title = title, content = content,
                            createdAt = now, updatedAt = now, accessedAt = now)
                    )
                    // definition拡張の復元
                    if (type == "definition") {
                        obj["extension"]?.jsonObject?.let { ext ->
                            val term = ext["term"]?.jsonPrimitive?.content ?: title
                            val definition = ext["definition"]?.jsonPrimitive?.content ?: ""
                            if (definition.isNotBlank()) {
                                definitionDao.insert(
                                    EntryDefinitionEntity(
                                        entryId = id, term = term, definition = definition,
                                        reading = ext["reading"]?.jsonPrimitive?.content,
                                        field = ext["field"]?.jsonPrimitive?.content
                                    )
                                )
                            }
                        }
                    }
                    if (type == "thought") {
                        thoughtDao.insert(EntryThoughtEntity(entryId = id, context = "json_import"))
                    }
                    success++
                } catch (e: Exception) {
                    errors.add("Item ${i + 1}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            errors.add("File error: ${e.message}")
        }
        return ImportResult(success, errors.size, errors)
    }

    /** ★F: URLリスト一括取り込み（1行1URLのtxt/csv） */
    suspend fun importUrlList(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0
        try {
            val lines = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readLines()
            } ?: return ImportResult(0, 1, listOf("Cannot open file"))

            val urls = lines.map { it.trim() }
                .filter { it.startsWith("http://") || it.startsWith("https://") }
            for (url in urls) {
                val result = webScraper.scrapeAndSave(url)
                if (result.success) success++ else errors.add("$url: ${result.error}")
            }
        } catch (e: Exception) {
            errors.add("File error: ${e.message}")
        }
        return ImportResult(success, errors.size, errors)
    }

    suspend fun importNotionMarkdown(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult(0, 1, listOf("Cannot open file"))
            val content = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { it.readText() }

            // Notionエクスポートはファイル名がタイトルになることが多いため、URIから推測またはデフォルトを使用
            val fileName = uri.lastPathSegment?.substringBeforeLast(".") ?: "Notion Export"

            val parsed = WikiLinkParser.parse(fileName, content)
            // ObsidianImporterのロジックを流用（内部でentry生成とconnection作成を行う）
            // ※ObsidianImporterは@Injectされているため、ImportPipelineのコンストラクタに追加しておく必要があります
            val result = obsidianImporter.importNotes(listOf(ObsidianImporter.ObsidianNote(parsed.title, parsed.content, parsed.wikiLinks)))
            success = result.createdEntries

        } catch (e: Exception) {
            errors.add("File error: ${e.message}")
        }
        return ImportResult(success, errors.size, errors)
    }
}