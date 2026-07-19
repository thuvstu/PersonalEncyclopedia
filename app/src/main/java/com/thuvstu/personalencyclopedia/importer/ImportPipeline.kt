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

/**
 * Common import pipeline (§12.1)
 * Source → Adapter → Normalize → entry + extension → (Phase 2: Embedding queue)
 */
@Singleton
class ImportPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryDao: EntryDao,
    private val thoughtDao: EntryThoughtDao,
    private val definitionDao: EntryDefinitionDao
) {
    data class ImportResult(
        val successCount: Int,
        val errorCount: Int,
        val errors: List<String> = emptyList()
    )

    /**
     * Import CSV as definitions (flashcards).
     * Expected columns: term, reading(optional), definition, field(optional)
     */
    suspend fun importDefinitionsCsv(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult(0, 1, listOf("Cannot open file"))

            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                val header = reader.readLine() ?: return ImportResult(0, 0)
                val columns = parseCsvLine(header).map { it.trim().lowercase() }

                val termIdx = columns.indexOfFirst { it in listOf("term", "用語", "単語", "front") }
                val readingIdx = columns.indexOfFirst { it in listOf("reading", "読み", "ふりがな") }
                val defIdx = columns.indexOfFirst { it in listOf("definition", "定義", "意味", "back") }
                val fieldIdx = columns.indexOfFirst { it in listOf("field", "分野", "ジャンル", "category") }

                if (termIdx < 0 || defIdx < 0) {
                    return ImportResult(0, 1, listOf("CSV must have 'term' and 'definition' columns"))
                }

                var lineNum = 1
                reader.forEachLine { line ->
                    lineNum++
                    if (line.isBlank()) return@forEachLine
                    try {
                        val cols = parseCsvLine(line)
                        val term = cols.getOrNull(termIdx)?.trim() ?: ""
                        val definition = cols.getOrNull(defIdx)?.trim() ?: ""
                        if (term.isBlank() || definition.isBlank()) {
                            errors.add("Line $lineNum: missing term or definition")
                            return@forEachLine
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
                        errors.add("Line $lineNum: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("File error: ${e.message}")
        }

        return ImportResult(success, errors.size, errors)
    }

    /**
     * Import Markdown file as thought entries.
     * Each H1/H2 heading becomes a separate entry.
     */
    suspend fun importMarkdown(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult(0, 1, listOf("Cannot open file"))

            val content = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use {
                it.readText()
            }

            // Split by headings
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

        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
        }
        result.add(sb.toString())
        return result
    }
}