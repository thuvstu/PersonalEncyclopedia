package com.thuvstu.personalencyclopedia.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thuvstu.personalencyclopedia.db.entity.*
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeColor
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.HorizontalDivider


/**
 * 全13 Entry Type の型固有プロパティ表示カード(§11.4)。
 * EntryDetailScreen から type と extension を渡して呼び出す。
 */
@Composable
fun EntryTypeSection(
    type: String,
    extension: Any?,
    thought: EntryThoughtEntity? = null,
    definition: EntryDefinitionEntity? = null,
    onInternalLink: ((String) -> Unit)? = null

) {
    when (type) {
        "thought" -> thought?.let { ThoughtSection(it) }
        "definition" -> definition?.let { DefinitionSection(it) }
        "webpage" -> (extension as? EntryWebpageEntity)?.let { WebpageSection(it) }
        "book" -> (extension as? EntryBookEntity)?.let { BookSection(it) }
        "video" -> (extension as? EntryVideoEntity)?.let { VideoSection(it) }
        "document" -> (extension as? EntryDocumentEntity)?.let { DocumentSection(it) }
        "media" -> (extension as? EntryMediaEntity)?.let { MediaSection(it) }
        "person" -> (extension as? EntryPersonEntity)?.let { PersonSection(it) }
        "org" -> (extension as? EntryOrgEntity)?.let { OrgSection(it) }
        "place" -> (extension as? EntryPlaceEntity)?.let { PlaceSection(it) }
        "event" -> (extension as? EntryEventEntity)?.let { EventSection(it) }
        "liked" -> (extension as? EntryLikedEntity)?.let { LikedSection(it) }
        "ai_conv" -> (extension as? EntryAiConvEntity)?.let { AiConvSection(it) }
        "definition" -> definition?.let { DefinitionSection(it, onInternalLink) }
    }
}

// ── 個別セクション ────────────────────────────────────────────

@Composable
private fun ThoughtSection(t: EntryThoughtEntity) {
    if (t.mood.isNullOrBlank() && t.context.isNullOrBlank()) return
    SectionCard("メモ") {
        InfoRow("気分", t.mood)
        InfoRow("コンテキスト", t.context)
        if (t.isDraft) InfoRow("状態", "下書き")
    }
}

@Composable
private fun DefinitionSection(d: EntryDefinitionEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = entryTypeColor("definition").copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(d.term, style = MaterialTheme.typography.headlineMedium)
            if (!d.reading.isNullOrBlank())
                Text(d.reading, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!d.field.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                SuggestionChip(onClick = {}, label = { Text(d.field) },
                    modifier = Modifier.height(28.dp))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(d.definition, style = MaterialTheme.typography.bodyLarge)
            parseList(d.examplesJson).takeIf { it.isNotEmpty() }?.let { ex ->
                Spacer(Modifier.height(8.dp))
                Text("例", style = MaterialTheme.typography.labelLarge)
                ex.forEach { Text("・$it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun WebpageSection(w: EntryWebpageEntity) {
    SectionCard("Webページ") {
        InfoRow("URL", w.url)
        InfoRow("ドメイン", w.domain)
        InfoRow("著者", w.author)
        InfoRow("公開日", w.publishedAt?.let { fmtDate(it) })
        InfoRow("推定読了時間", w.readingTimeS?.let { "約${(it / 60).coerceAtLeast(1)}分" })
        InfoRow("取得方式", w.scraperUsed)
        ExpandableText("スクレイピング本文", w.fullText)
    }
}

@Composable
private fun BookSection(b: EntryBookEntity) {
    SectionCard("書籍") {
        parseList(b.authorsJson).takeIf { it.isNotEmpty() }?.let {
            InfoRow("著者", it.joinToString(" / "))
        }
        InfoRow("ISBN", b.isbn)
        InfoRow("出版社", b.publisher)
        InfoRow("出版年", b.publishedYear?.toString())
        InfoRow("ページ数", b.totalPages?.let { "${it}p" })
        InfoRow("ステータス", when (b.readStatus) {
            "reading" -> "読書中"; "done" -> "読了"; "dropped" -> "中断"; else -> "未読"
        })
        b.rating?.takeIf { it > 0 }?.let { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("評価", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text("★".repeat(r) + "☆".repeat((5 - r).coerceAtLeast(0)),
                    color = entryTypeColor("book"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VideoSection(v: EntryVideoEntity) {
    SectionCard("動画") {
        InfoRow("プラットフォーム", v.platform)
        InfoRow("チャンネル", v.channelName)
        InfoRow("再生時間", v.durationS?.let { fmtDuration(it) })
        InfoRow("視聴日", v.watchedAt?.let { fmtDate(it) })
        InfoRow("進捗", v.watchProgress?.let { "${(it * 100).toInt()}%" })
        ExpandableText("文字起こし", v.transcript)
    }
}

@Composable
private fun DocumentSection(d: EntryDocumentEntity) {
    SectionCard("ドキュメント") {
        InfoRow("種別", d.docType.uppercase())
        InfoRow("MIME", d.mimeType)
        InfoRow("サイズ", d.fileSizeBytes?.let { fmtBytes(it) })
        InfoRow("ページ数", d.pageCount?.let { "${it}p" })
        InfoRow("抽出方式", d.extractionMethod)
        ExpandableText("抽出テキスト", d.extractedText)
    }
}

@Composable
private fun MediaSection(m: EntryMediaEntity) {
    SectionCard("メディア") {
        InfoRow("種別", m.mediaType)
        if (m.widthPx != null && m.heightPx != null)
            InfoRow("解像度", "${m.widthPx}×${m.heightPx}")
        InfoRow("長さ", m.durationS?.let { fmtDuration(it.toInt()) })  // Float.toInt() は OK
        InfoRow("キャプション", m.caption)
        ExpandableText("OCRテキスト", m.ocrText)
    }
}

@Composable
private fun PersonSection(p: EntryPersonEntity) {
    SectionCard("人物") {
        parseList(p.aliasesJson).takeIf { it.isNotEmpty() }?.let {
            InfoRow("別名", it.joinToString(", "))
        }
        val years = listOfNotNull(
            p.birthYear?.toString(), p.deathYear?.toString()
        ).joinToString(" – ").takeIf { it.isNotEmpty() }
        InfoRow("生没年", years)
        InfoRow("国籍", p.nationality)
        parseList(p.occupationsJson).takeIf { it.isNotEmpty() }?.let {
            InfoRow("職業", it.joinToString(", "))
        }
        ExpandableText("略歴", p.biography)
    }
}

@Composable
private fun OrgSection(o: EntryOrgEntity) {
    SectionCard("組織") {
        InfoRow("正式名称", o.officialName)
        InfoRow("種別", o.orgType)
        InfoRow("設立年", o.foundedYear?.toString())
        InfoRow("国", o.country)
        InfoRow("Webサイト", o.websiteUrl)
        ExpandableText("説明", o.description)
    }
}

@Composable
private fun PlaceSection(p: EntryPlaceEntity) {
    SectionCard("場所") {
        InfoRow("タイプ", p.placeType)
        InfoRow("住所", p.address)
        if (p.latitude != null && p.longitude != null)
            InfoRow("座標", "%.4f, %.4f".format(p.latitude, p.longitude))
        parseList(p.visitedDatesJson).takeIf { it.isNotEmpty() }?.let {
            InfoRow("訪問回数", "${it.size}回")
        }
    }
}

@Composable
private fun EventSection(e: EntryEventEntity) {
    SectionCard("イベント") {
        InfoRow("開始", fmtDateTime(e.startedAt))
        InfoRow("終了", e.endedAt?.let { fmtDateTime(it) })
        InfoRow("開催地", e.locationText)
        parseList(e.participantsJson).takeIf { it.isNotEmpty() }?.let {
            InfoRow("参加者", it.joinToString(", "))
        }
        InfoRow("種別", if (e.isPersonal) "個人" else "公式")
    }
}

@Composable
private fun LikedSection(l: EntryLikedEntity) {
    SectionCard("いいね") {
        InfoRow("プラットフォーム", l.platform)
        InfoRow("作者", l.authorName)
        InfoRow("種類", l.contentType)
        InfoRow("いいね日", l.likedAt?.let { fmtDate(it) })
        ExpandableText("本文", l.fullText)
    }
}

@Composable
private fun AiConvSection(a: EntryAiConvEntity) {
    SectionCard("AI会話") {
        InfoRow("モデル", a.model)
        InfoRow("プロバイダ", a.provider)
        InfoRow("トピック", a.topic)
        InfoRow("トークン数", a.tokenCount?.toString())
        val messages = try {
            Json.parseToJsonElement(a.messagesJson).jsonArray.map {
                (it.jsonObject["role"]?.jsonPrimitive?.content ?: "?") to
                        (it.jsonObject["content"]?.jsonPrimitive?.content ?: "")
            }
        } catch (_: Exception) { emptyList() }
        if (messages.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("メッセージ", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            messages.forEach { (role, content) ->
                Surface(
                    tonalElevation = if (role == "user") 2.dp else 0.dp,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(role.uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = entryTypeColor("ai_conv"))
                        Text(content, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ── 共通部品 ─────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📌 $title", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ExpandableText(label: String, text: String?, maxLines: Int = 4) {
    if (text.isNullOrBlank()) return
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall,
            maxLines = if (expanded) Int.MAX_VALUE else maxLines)
        if (text.length > 120) {
            TextButton(onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp), modifier = Modifier.height(28.dp)) {
                Text(if (expanded) "閉じる" else "全文を表示")
            }
        }
    }
}
@Composable
private fun DefinitionSection(d: EntryDefinitionEntity, onInternalLink: ((String) -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = entryTypeColor("definition").copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(d.term, style = MaterialTheme.typography.headlineMedium)
            if (!d.reading.isNullOrBlank())
                Text(d.reading, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!d.field.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                SuggestionChip(onClick = {}, label = { Text(d.field) }, modifier = Modifier.height(28.dp))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            // ★ 定義文をリッチ描画（Markdown+KaTeX+ルビ+[[wiki]]+自動リンク）
            RichContentView(
                content = d.definition,
                onWikiLinkClick = { onInternalLink?.invoke(it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp, max = 300.dp)
            )
            parseList(d.examplesJson).takeIf { it.isNotEmpty() }?.let { ex ->
                Spacer(Modifier.height(8.dp))
                Text("例", style = MaterialTheme.typography.labelLarge)
                ex.forEach { Text("・$it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
private fun parseList(json: String?): List<String> = try {
    Json.parseToJsonElement(json ?: "[]").jsonArray.map { it.jsonPrimitive.content }
} catch (_: Exception) { emptyList() }

private val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
private val dateTimeFmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
private fun fmtDate(ms: Long) = dateFmt.format(Date(ms))
private fun fmtDateTime(ms: Long) = dateTimeFmt.format(Date(ms))
private fun fmtDuration(s: Int) = "%d:%02d".format(s / 60, s % 60)
private fun fmtBytes(b: Long) = when {
    b < 1024 -> "${b}B"
    b < 1024 * 1024 -> "%.1fKB".format(b / 1024.0)
    else -> "%.1fMB".format(b / (1024.0 * 1024.0))
}