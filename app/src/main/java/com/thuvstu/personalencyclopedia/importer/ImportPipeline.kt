package com.thuvstu.personalencyclopedia.importer

import android.content.Context
import android.net.Uri
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryExtensionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryThoughtDao
import com.thuvstu.personalencyclopedia.db.entity.EntryDefinitionEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryThoughtEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryWebpageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

@Singleton
class ImportPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryDao: EntryDao,
    private val thoughtDao: EntryThoughtDao,
    private val webScraper: WebScraper,
    private val extensionDao: EntryExtensionDao,
    private val definitionDao: EntryDefinitionDao,
    private val obsidianImporter: ObsidianImporter,
    private val contentHashDuplicateDetector: ContentHashDuplicateDetector,   // §12.7
    private val urlDuplicateDetector: UrlDuplicateDetector                    // §12.7
) {
    data class ImportResult(
        val successCount: Int,
        val errorCount: Int,
        val errors: List<String> = emptyList(),
        val skipCount: Int = 0        // §12.7: 重複スキップ件数
    )

    /** §12.7: 候補が既存entryと重複していればスキップ(true)を返す。 */
    private suspend fun isDuplicate(candidate: ImportCandidate): Boolean =
        contentHashDuplicateDetector.findDuplicate(candidate) != null ||
            urlDuplicateDetector.findDuplicate(candidate) != null

    suspend fun importDefinitionsCsv(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0
        var skipped = 0

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

                    // §12.7: 同一用語+定義の重複をスキップ
                    if (isDuplicate(ImportCandidate(title = term, type = "definition", content = definition))) {
                        skipped++
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

        return ImportResult(successCount = success, errorCount = errors.size, errors = errors, skipCount = skipped)
    }

    suspend fun importMarkdown(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0
        var skipped = 0

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

                // §12.7: 同一タイトル+本文の重複をスキップ
                if (isDuplicate(ImportCandidate(title = title, type = "thought", content = body))) {
                    skipped++
                    continue
                }

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

        return ImportResult(successCount = success, errorCount = errors.size, errors = errors, skipCount = skipped)
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
        var skipped = 0
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
                    // §12.7: 重複をスキップ（URL持ちはURL一致、それ以外はタイトル+本文）
                    val candidate = ImportCandidate(
                        title = title, type = type, content = content,
                        sourceUrl = obj["sourceUrl"]?.jsonPrimitive?.content
                    )
                    if (isDuplicate(candidate)) {
                        skipped++
                        continue
                    }
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
        return ImportResult(successCount = success, errorCount = errors.size, errors = errors, skipCount = skipped)
    }

    /** ★F: URLリスト一括取り込み（1行1URLのtxt/csv） */
    suspend fun importUrlList(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0
        var skipped = 0
        try {
            val lines = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readLines()
            } ?: return ImportResult(0, 1, listOf("Cannot open file"))

            val urls = lines.map { it.trim() }
                .filter { it.startsWith("http://") || it.startsWith("https://") }
            for (url in urls) {
                val result = webScraper.scrapeAndSave(url)
                if (result.success) {
                    // §12.7: WebScraper内部のURL重複判定で取り込まれた分はスキップとして計上
                    if (result.deduplicated) skipped++ else success++
                } else {
                    errors.add("$url: ${result.error}")
                }
            }
        } catch (e: Exception) {
            errors.add("File error: ${e.message}")
        }
        return ImportResult(successCount = success, errorCount = errors.size, errors = errors, skipCount = skipped)
    }

    /** ★P6-1: Netscape bookmark.html一括取り込み（軽量Hoarder/Linkwarden代替の第一歩）。
     * フォルダ構造・ADD_DATEを復元し、webpageエントリーとして高速登録する。
     * 本文スクレイプは行わない（数百〜数千件でも固まらない）。依存ゼロ（jsoupのみ）。 */
    data class BookmarkItem(
        val url: String,
        val title: String,
        val folderPath: String,
        val addDateMs: Long?
    )

    suspend fun importBookmarksHtml(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0
        var skipped = 0
        try {
            val html = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            } ?: return ImportResult(0, 1, listOf("Cannot open file"))
            val items = parseNetscapeBookmarks(html)
            if (items.isEmpty()) {
                return ImportResult(0, 1, listOf("ブックマークが見つかりません（Netscape形式のbookmark.htmlですか？）"))
            }
            for (item in items) {
                try {
                    // §12.7: URL重複はスキップ
                    if (isDuplicate(ImportCandidate(title = item.title, type = "webpage", content = null, sourceUrl = item.url))) {
                        skipped++
                        continue
                    }
                    val id = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    val domain = try { java.net.URI(item.url).host ?: item.url } catch (_: Exception) { item.url }
                    val meta = buildJsonObject {
                        put("bookmarkFolder", item.folderPath)
                        put("importedFrom", "bookmark.html")
                    }.toString()
                    entryDao.insert(
                        EntryEntity(
                            id = id,
                            type = "webpage",
                            title = item.title,
                            sourceUrl = item.url,
                            metadataJson = meta,
                            createdAt = item.addDateMs ?: now,
                            updatedAt = now,
                            accessedAt = now
                        )
                    )
                    extensionDao.insertWebpage(
                        EntryWebpageEntity(
                            entryId = id,
                            url = item.url,
                            domain = domain,
                            scraperUsed = "bookmark_import"
                        )
                    )
                    success++
                } catch (e: Exception) {
                    errors.add("${item.url}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            errors.add("File error: ${e.message}")
        }
        return ImportResult(successCount = success, errorCount = errors.size, errors = errors.take(20), skipCount = skipped)
    }

    /** Netscape形式（ブラウザの「ブックマークをHTMLにエクスポート」）のパーサー。純粋関数。 */
    fun parseNetscapeBookmarks(html: String): List<BookmarkItem> {
        val out = mutableListOf<BookmarkItem>()
        try {
            val doc = Jsoup.parse(html)
            val roots = doc.select("dl").filter { dl -> dl.parents().none { it.tagName() == "dl" } }
            // ルートDLが無い壊れた形式では body 全体を走査
            if (roots.isEmpty()) collectBookmarks(doc.body(), "", out)
            else roots.forEach { collectBookmarks(it, "", out) }
        } catch (_: Exception) { /* 空リストを返す */ }
        return out
    }

    private fun collectBookmarks(parent: Element, folderPath: String, out: MutableList<BookmarkItem>) {
        var pendingFolder: String? = null
        fun flushTo(dl: Element) {
            val name = pendingFolder
            collectBookmarks(
                dl,
                if (name.isNullOrEmpty()) folderPath
                else if (folderPath.isEmpty()) name else "$folderPath/$name",
                out
            )
            pendingFolder = null
        }
        for (child in parent.children()) {
            when (child.tagName().lowercase()) {
                "h3" -> pendingFolder = child.text().trim().takeIf { it.isNotEmpty() }
                "dt", "dd" -> {
                    val h3 = child.children().firstOrNull { it.tagName() == "h3" }
                    if (h3 != null) {
                        pendingFolder = h3.text().trim().takeIf { it.isNotEmpty() }
                        child.children().firstOrNull { it.tagName() == "dl" }?.let { flushTo(it) }
                    } else {
                        child.children().firstOrNull { it.tagName() == "a" && it.hasAttr("href") }
                            ?.let { addBookmarkAnchor(it, folderPath, out) }
                        child.children().firstOrNull { it.tagName() == "dl" }
                            ?.let { collectBookmarks(it, folderPath, out) }
                    }
                }
                "a" -> if (child.hasAttr("href")) addBookmarkAnchor(child, folderPath, out)
                "dl" -> flushTo(child)
                // <p>ラッパー内の要素は同列として扱う（pendingは引き継がない）
                "p" -> collectBookmarks(child, folderPath, out)
            }
        }
    }

    private fun addBookmarkAnchor(a: Element, folderPath: String, out: MutableList<BookmarkItem>) {
        val href = a.attr("href").trim()
        if (!(href.startsWith("http://") || href.startsWith("https://"))) return
        val title = a.text().trim().ifEmpty { href }.take(200)
        val addDateMs = a.attr("add_date").toLongOrNull()?.times(1000)
        out.add(BookmarkItem(url = href, title = title, folderPath = folderPath, addDateMs = addDateMs))
    }

    /** ★Drive橋渡しのSAF版: フォルダ内の md/txt/csv/json/html を拡張子で振り分けて一括取込。
     * Drive APIは使わない（骨格）。重複は各経路の既存判定に任せる。最大200ファイル。 */
    suspend fun importSafFolder(treeUri: Uri): ImportResult {
        var success = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        try {
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            )
            val items = mutableListOf<Pair<android.net.Uri, String>>()
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = c.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (c.moveToNext() && items.size < 200) {
                    val docId = c.getString(idIdx) ?: continue
                    val name = c.getString(nameIdx) ?: continue
                    val mime = c.getString(mimeIdx) ?: ""
                    if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) continue
                    val lower = name.lowercase()
                    if (lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt") ||
                        lower.endsWith(".csv") || lower.endsWith(".json") ||
                        lower.endsWith(".html") || lower.endsWith(".htm")
                    ) {
                        items.add(
                            android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId) to lower
                        )
                    }
                }
            }
            if (items.isEmpty()) {
                return ImportResult(0, 1, listOf("対応ファイル（md/txt/csv/json/html）が見つかりません"))
            }
            for ((docUri, lower) in items) {
                try {
                    val r = when {
                        lower.endsWith(".csv") -> importDefinitionsCsv(docUri)
                        lower.endsWith(".json") -> importEntriesJson(docUri)
                        lower.endsWith(".html") || lower.endsWith(".htm") -> importBookmarksHtml(docUri)
                        else -> importMarkdown(docUri)
                    }
                    success += r.successCount
                    skipped += r.skipCount
                    if (r.errorCount > 0) errors.add("$lower: ${r.errors.firstOrNull()}")
                } catch (e: Exception) {
                    errors.add("$lower: ${e.message}")
                }
            }
        } catch (e: Exception) {
            errors.add("File error: ${e.message}")
        }
        return ImportResult(successCount = success, errorCount = errors.size, errors = errors.take(20), skipCount = skipped)
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
