package com.thuvstu.personalencyclopedia.importer

import android.content.Context
import android.net.Uri
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryExtensionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryThoughtDao
import com.thuvstu.personalencyclopedia.db.dao.EntryStickyNoteDao
import com.thuvstu.personalencyclopedia.db.dao.TagDao
import com.thuvstu.personalencyclopedia.db.entity.EntryDefinitionEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryDocumentEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryTagEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryThoughtEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryWebpageEntity
import com.thuvstu.personalencyclopedia.db.entity.TagEntity
import com.thuvstu.personalencyclopedia.brain.ai.EmbeddingQueue
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
    private val urlDuplicateDetector: UrlDuplicateDetector,                   // §12.7
    private val tagDao: TagDao,                                               // ★往復対称: タグ復元
    private val embeddingQueue: EmbeddingQueue,                               // ★往復対称: 検索文書即時更新
    private val documentExtractor: DocumentExtractor,                         // ★wt43: PDF/DOCX 取込
    private val stickyNoteDao: EntryStickyNoteDao                             // ★wt56: 付箋の往復
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

    /**
     * ★往復対称(mismatch §6-2): `EntryExporter.buildJson` の出力を欠落なく復元する。
     * - 旧ID・時刻・お気に入り・タグ・11型拡張を保持(`EntryJsonCodec`)。
     * - 同じIDが既に存在すれば「同一データの再取込」とみなしてスキップ(更新はしない)。
     * - IDが無い旧形式は §12.7 の重複判定(URL/ハッシュ)でスキップし、新IDで登録する。
     * - 取り込んだentryは search_document を即時更新する(起動時の差分再構築を待たない)。
     */
    suspend fun importEntriesJson(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0
        var skipped = 0
        try {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            } ?: return ImportResult(0, 1, listOf("Cannot open file"))

            val root = Json.parseToJsonElement(text)
            // 配列直下 or {"entries":[...]} の両方を受ける
            val arr = (root as? JsonArray) ?: (root as? JsonObject)?.get("entries") as? JsonArray
                ?: return ImportResult(0, 1, listOf("JSON形式が不正です(配列ではありません)"))
            for ((i, el) in arr.withIndex()) {
                try {
                    val obj = el as? JsonObject ?: continue
                    val now = System.currentTimeMillis()
                    val oldId = obj["id"]?.jsonPrimitive?.contentOrNull
                    val keepId = oldId != null && entryDao.getById(oldId) == null
                    if (oldId != null && !keepId) {
                        skipped++      // 同一IDが既にある = 同じデータ。上書きしない
                        continue
                    }
                    val decoded = EntryJsonCodec.decode(obj, keepId = keepId, newId = UUID.randomUUID().toString(), now = now)
                        ?: continue
                    if (oldId == null) {
                        // §12.7: 旧形式(ID無し)は内容ベースの重複判定
                        val candidate = ImportCandidate(
                            title = decoded.entry.title, type = decoded.entry.type,
                            content = decoded.entry.content, sourceUrl = decoded.entry.sourceUrl
                        )
                        if (isDuplicate(candidate)) { skipped++; continue }
                    }
                    insertDecoded(decoded)
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

    /** 復元結果をDBへ書き込む(entry→拡張→タグ→検索文書)。 */
    private suspend fun insertDecoded(d: EntryJsonCodec.Decoded) {
        entryDao.insert(d.entry)
        d.thought?.let { thoughtDao.insert(it) }
        d.definition?.let { definitionDao.insert(it) }
        d.webpage?.let { extensionDao.insertWebpage(it) }
        d.book?.let { extensionDao.insertBook(it) }
        d.video?.let { extensionDao.insertVideo(it) }
        d.document?.let { extensionDao.insertDocument(it) }
        d.media?.let { extensionDao.insertMedia(it) }
        d.person?.let { extensionDao.insertPerson(it) }
        d.org?.let { extensionDao.insertOrg(it) }
        d.place?.let { extensionDao.insertPlace(it) }
        d.event?.let { extensionDao.insertEvent(it) }
        d.liked?.let { extensionDao.insertLiked(it) }
        d.aiConv?.let { extensionDao.insertAiConv(it) }
        for (name in d.tags) {
            val existing = tagDao.getByName(name)
            val tagId = existing?.id ?: TagEntity(name = name).also { tagDao.insert(it) }.id
            tagDao.linkTag(EntryTagEntity(entryId = d.entry.id, tagId = tagId))
        }
        if (d.stickyNotes.isNotEmpty()) stickyNoteDao.insertAll(d.stickyNotes)   // ★wt56
        try { embeddingQueue.enqueue(d.entry.id) } catch (_: Exception) { /* 検索文書は起動時差分で追いつく */ }
    }

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

    /**
     * ★wt43 (mismatch §1.3): PDF / DOCX を document 型エントリーとして取り込む。
     * 1. ファイルを `filesDir/blobs/documents/<entryId>/<name>` にコピー(端末内保管。Drive API不使用)
     * 2. `DocumentExtractor` で本文テキストを抽出(pdfbox / docx解凍)。失敗しても登録は続行(抽出方式 "none")
     * 3. entry.content には抽出テキストの先頭500字を要約代わりに入れる(カード・検索での視認性)
     * 重複は §12.7 のタイトル+本文ハッシュ判定。PDF のページ数は pdfbox から取得。
     */
    suspend fun importDocumentFile(uri: Uri, displayNameHint: String? = null): ImportResult {
        val name = displayNameHint ?: queryDisplayName(uri) ?: "document"
        val lower = name.lowercase()
        val (docType, mime) = when {
            lower.endsWith(".pdf") -> "pdf" to "application/pdf"
            lower.endsWith(".docx") -> "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> return ImportResult(0, 1, listOf("対応外の形式です(pdf/docx のみ): $name"))
        }
        val title = name.substringBeforeLast('.').ifBlank { name }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        try {
            // 1. blob コピー
            val dir = java.io.File(context.filesDir, "blobs/documents/$id").apply { mkdirs() }
            val safeName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val file = java.io.File(dir, safeName)
            val size = context.contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(file).use { out -> input.copyTo(out) }
            } ?: run { dir.deleteRecursively(); return ImportResult(0, 1, listOf("Cannot open file: $name")) }

            // 2. 抽出(失敗しても続行)
            val extracted = documentExtractor.extractText(Uri.fromFile(file), mime)?.trim()?.takeIf { it.isNotBlank() }
            val pages = if (docType == "pdf") documentExtractor.pageCount(file) else null

            // 重複判定(タイトル + 抽出本文)
            if (isDuplicate(ImportCandidate(title = title, type = "document", content = extracted))) {
                dir.deleteRecursively()
                return ImportResult(0, 0, emptyList(), skipCount = 1)
            }

            // 3. 登録
            entryDao.insert(
                EntryEntity(
                    id = id, type = "document", title = title,
                    content = extracted?.take(500),
                    createdAt = now, updatedAt = now, accessedAt = now
                )
            )
            extensionDao.insertDocument(
                EntryDocumentEntity(
                    entryId = id, docType = docType, blobPath = file.absolutePath, mimeType = mime,
                    fileSizeBytes = size, pageCount = pages, extractedText = extracted,
                    extractionMethod = if (extracted != null) (if (docType == "pdf") "pdfbox" else "docx-xml") else "none"
                )
            )
            try { embeddingQueue.enqueue(id) } catch (_: Exception) { /* 検索文書は後の再構築でも復旧可 */ }
            return ImportResult(1, 0)
        } catch (e: Exception) {
            java.io.File(context.filesDir, "blobs/documents/$id").deleteRecursively()
            return ImportResult(0, 1, listOf("$name: ${e.message}"))
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')
    } catch (_: Exception) { uri.lastPathSegment }

    /** ★Drive橋渡しのSAF版: フォルダ内の md/txt/csv/json/html/pdf/docx を拡張子で振り分けて一括取込。
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
                        lower.endsWith(".html") || lower.endsWith(".htm") ||
                        lower.endsWith(".pdf") || lower.endsWith(".docx")   // ★wt43
                    ) {
                        items.add(
                            android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId) to lower
                        )
                    }
                }
            }
            if (items.isEmpty()) {
                return ImportResult(0, 1, listOf("対応ファイル（md/txt/csv/json/html/pdf/docx）が見つかりません"))
            }
            for ((docUri, lower) in items) {
                try {
                    val r = when {
                        lower.endsWith(".csv") -> importDefinitionsCsv(docUri)
                        lower.endsWith(".json") -> importEntriesJson(docUri)
                        lower.endsWith(".html") || lower.endsWith(".htm") -> importBookmarksHtml(docUri)
                        lower.endsWith(".pdf") || lower.endsWith(".docx") -> importDocumentFile(docUri, lower)   // ★wt43
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
