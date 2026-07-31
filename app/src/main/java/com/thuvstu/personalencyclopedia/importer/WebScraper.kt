package com.thuvstu.personalencyclopedia.importer

import android.util.Log
import com.thuvstu.personalencyclopedia.brain.ai.EmbeddingQueue
import com.thuvstu.personalencyclopedia.brain.ai.GeminiClient
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryExtensionDao
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryWebpageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §12.2 Webスクレイパー（段階的フォールバック、Android向け再設計）。
 *
 * Stage 1 (Must):  OkHttp + Jsoup + Readability相当のボイラープレート除去
 * Stage 2 (Should): 本文が100文字未満の場合、Gemini LLM にHTMLを渡して
 *                   「本文のみ抽出して要約せず全文を返す」プロンプトで構造化抽出
 *
 * Playwright は Chromium 依存で Android に不向きなため不採用。
 * Bot検知回避が必要な高度なケースは無理に自動化せず、
 * 取り込み失敗としてユーザーに手動貼り付けを促す。
 */
@Singleton
class WebScraper @Inject constructor(
    private val entryDao: EntryDao,
    private val extensionDao: EntryExtensionDao,
    private val embeddingQueue: EmbeddingQueue,
    private val geminiClient: GeminiClient    // ★ Stage2 用
) {
    companion object {
        private const val TAG = "WebScraper"
        private const val MIN_TEXT_LENGTH = 100
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        // Stage2 で LLM に渡すHTMLの最大文字数（トークン制限対策）
        private const val MAX_HTML_FOR_LLM = 15000
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class ScrapeResult(
        val entryId: String,
        val title: String,
        val fullText: String,
        val success: Boolean,
        val error: String? = null,
        val scraperUsed: String = "okhttp+jsoup",
        val deduplicated: Boolean = false
    )

    suspend fun scrapeAndSave(url: String): ScrapeResult {
        return withContext(Dispatchers.IO) {
            try {
                entryDao.findBySourceUrl(url)?.let { existing ->
                    entryDao.touch(existing.id)
                    return@withContext ScrapeResult(
                        existing.id, existing.title, "", true,
                        scraperUsed = "dedup", deduplicated = true
                    )
                }
                val domain = try { URI(url).host ?: url } catch (_: Exception) { url }
                val html = fetchHtml(url)
                    ?: return@withContext ScrapeResult("", "", "", false, "HTMLの取得に失敗しました")

                val doc = Jsoup.parse(html, url)

                // ノイズ要素の除去
                doc.select(
                    "script, style, nav, footer, header, aside, " +
                            ".ad, .ads, .sidebar, .menu, .cookie, .popup, " +
                            ".navigation, .breadcrumb, .social-share, .comments"
                ).remove()

                // Stage 1: Jsoup による本文抽出
                val title = extractTitle(doc)
                var fullText = extractMainContent(doc)
                var scraperUsed = "okhttp+jsoup"

                // Stage 2: 本文が閾値未満の場合、LLM にフォールバック（§12.2）
                if (fullText.length < MIN_TEXT_LENGTH && geminiClient.isConfigured()) {
                    Log.i(TAG, "Stage1 text too short (${fullText.length} chars), trying LLM extraction")
                    val llmText = extractWithLlm(html, url)
                    if (llmText != null && llmText.length > fullText.length) {
                        fullText = llmText
                        scraperUsed = "ai_extract"
                    }
                }

                // DB保存
                val entryId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                entryDao.insert(
                    EntryEntity(
                        id = entryId,
                        type = "webpage",
                        title = title,
                        sourceUrl = url,
                        createdAt = now,
                        updatedAt = now,
                        accessedAt = now
                    )
                )
                extensionDao.insertWebpage(
                    EntryWebpageEntity(
                        entryId = entryId,
                        url = url,
                        domain = domain,
                        scrapedAt = now,
                        fullText = fullText.take(50000),
                        readingTimeS = (fullText.length / 400).coerceAtLeast(1),
                        scraperUsed = scraperUsed
                    )
                )

                // 検索インデックス + Embedding キューへ
                embeddingQueue.enqueue(entryId)

                ScrapeResult(entryId, title, fullText, true, scraperUsed = scraperUsed)
            } catch (e: Exception) {
                Log.e(TAG, "Scrape failed: $url", e)
                ScrapeResult("", "", "", false, e.message ?: "不明なエラー")
            }
        }
    }

    private fun fetchHtml(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ja,en;q=0.9")
            .build()
        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP ${response.code} for $url")
                return null
            }
            response.body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "HTTP fetch failed for $url", e)
            null
        }
    }

    private fun extractTitle(doc: Document): String {
        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")
        if (!ogTitle.isNullOrBlank()) return ogTitle.take(200)

        val title = doc.title()
        if (title.isNotBlank()) return title.take(200)

        val h1 = doc.selectFirst("h1")?.text()
        if (!h1.isNullOrBlank()) return h1.take(200)

        return "Untitled"
    }

    /**
     * Stage 1: Readability 相当の本文抽出。
     * <article> / <main> / [role=main] を優先し、
     * 段落・見出し・リスト・引用・コードブロックを連結。
     */
    private fun extractMainContent(doc: Document): String {
        val article = doc.selectFirst("article")
            ?: doc.selectFirst("main")
            ?: doc.selectFirst("[role=main]")
            ?: doc.selectFirst(".post-content, .entry-content, .article-body, #content")
            ?: doc.body()

        if (article == null) return ""

        val paragraphs = article.select("p, h1, h2, h3, h4, h5, li, blockquote, pre, td")
            .map { it.text().trim() }
            .filter { it.length > 20 }

        return paragraphs.joinToString("\n")
    }

    /**
     * Stage 2: Gemini LLM による本文抽出（§12.2）。
     * HTML のノイズを LLM に読ませて「本文のみ」を返させる。
     * 要約はしない（全文抽出が目的）。
     */
    private suspend fun extractWithLlm(html: String, url: String): String? {
        // HTML をテキスト化してトークン節約
        val doc = Jsoup.parse(html)
        doc.select("script, style, svg, noscript").remove()
        val bodyText = doc.body()?.text()?.take(MAX_HTML_FOR_LLM) ?: return null

        if (bodyText.length < MIN_TEXT_LENGTH) return null

        val prompt = """
            以下のWebページ（$url）のテキストから、記事の本文のみを抽出してください。

            指示:
            - ナビゲーション、フッター、広告、SNSボタン、コメント欄は除外
            - 要約しないでください。本文をそのまま全文で返してください
            - 見出し構造（# ## ###）を維持してください
            - 本文が見つからない場合は「NO_CONTENT」とだけ返してください

            --- テキスト開始 ---
            $bodyText
            --- テキスト終了 ---
        """.trimIndent()

        return try {
            val result = geminiClient.generate(prompt)
            if (result != null && !result.contains("NO_CONTENT") && result.length >= MIN_TEXT_LENGTH) {
                result.trim()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "LLM extraction failed", e)
            null
        }
    }
}