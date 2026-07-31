package com.thuvstu.personalencyclopedia.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.thuvstu.personalencyclopedia.importer.AutoLinker

data class WikiLinkRange(val start: Int, val end: Int, val title: String)
data class AutoLinkRange(val start: Int, val end: Int, val entryId: String)

/**
 * Markdown描画 + [[wiki-link]] + AutoLinker自動リンク(§12.5)。
 * 解析は remember で1回のみ実行（高速化）。
 */
@Composable
fun MarkdownText(
    text: String,
    onWikiLinkClick: ((String) -> Unit)? = null,
    onAutoLinkClick: ((String) -> Unit)? = null,
    autoLinker: AutoLinker? = null,
    modifier: Modifier = Modifier
) {
    val bodyColor = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    val (annotated, wikiRanges, autoRanges) = remember(
        text, bodyColor, linkColor, mutedColor, autoLinker
    ) {
        buildMarkdownAnnotated(text, bodyColor, linkColor, mutedColor, autoLinker)
    }

    ClickableText(
        text = annotated,
        modifier = modifier,
        onClick = { offset ->
            // [[wiki-link]] 優先
            val wiki = wikiRanges.firstOrNull { offset in it.start..it.end }
            if (wiki != null) {
                onWikiLinkClick?.invoke(wiki.title)
                return@ClickableText
            }
            // AutoLinker 自動リンク
            val auto = autoRanges.firstOrNull { offset in it.start..it.end }
            if (auto != null) onAutoLinkClick?.invoke(auto.entryId)
        }
    )
}

private val INLINE_TOKENS = Regex(
    """(\*\*(.+?)\*\*)|(\*([^*]+?)\*)|(`([^`]+?)`)|(\[\[([^\]|]+)(?:\|([^\]]+))?]])"""
)

private fun buildMarkdownAnnotated(
    source: String,
    bodyColor: Color,
    linkColor: Color,
    mutedColor: Color,
    autoLinker: AutoLinker?
): Triple<AnnotatedString, List<WikiLinkRange>, List<AutoLinkRange>> {
    val b = AnnotatedString.Builder()
    val wikiRanges = mutableListOf<WikiLinkRange>()
    var inCodeBlock = false

    source.lines().forEachIndexed { idx, raw ->
        if (idx > 0) b.append("\n")
        val line = raw.trimEnd()

        if (line.trimStart().startsWith("```")) {
            inCodeBlock = !inCodeBlock
            return@forEachIndexed
        }
        if (inCodeBlock) {
            b.withStyle(SpanStyle(
                color = mutedColor, fontFamily = FontFamily.Monospace, fontSize = 13.sp
            )) { append(line) }
            return@forEachIndexed
        }
        when {
            line.startsWith("#### ") -> appendInline(b, line.removePrefix("#### "),
                SpanStyle(bodyColor, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                linkColor, mutedColor, wikiRanges)
            line.startsWith("### ") -> appendInline(b, line.removePrefix("### "),
                SpanStyle(bodyColor, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                linkColor, mutedColor, wikiRanges)
            line.startsWith("## ") -> appendInline(b, line.removePrefix("## "),
                SpanStyle(bodyColor, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                linkColor, mutedColor, wikiRanges)
            line.startsWith("# ") -> appendInline(b, line.removePrefix("# "),
                SpanStyle(bodyColor, fontSize = 19.sp, fontWeight = FontWeight.Bold),
                linkColor, mutedColor, wikiRanges)
            line.startsWith("- ") || line.startsWith("* ") -> {
                b.append("• ")
                appendInline(b, line.drop(2), SpanStyle(bodyColor, fontSize = 14.sp),
                    linkColor, mutedColor, wikiRanges)
            }
            line.startsWith("> ") -> appendInline(b, line.removePrefix("> "),
                SpanStyle(mutedColor, fontSize = 14.sp, fontStyle = FontStyle.Italic),
                linkColor, mutedColor, wikiRanges)
            else -> appendInline(b, line, SpanStyle(bodyColor, fontSize = 14.sp),
                linkColor, mutedColor, wikiRanges)
        }
    }

    // ── AutoLinker: 完成テキストからタイトルを検出しスタイル後付け ──
    val autoRanges = mutableListOf<AutoLinkRange>()
    if (autoLinker != null) {
        val finalText = b.toAnnotatedString().text
        val linkStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
        for (m in autoLinker.findMatches(finalText)) {
            val overlapsWiki = wikiRanges.any { w ->
                m.range.first <= w.end && m.range.last >= w.start
            }
            if (!overlapsWiki) {
                b.addStyle(linkStyle, m.range.first, m.range.last + 1)
                autoRanges.add(AutoLinkRange(m.range.first, m.range.last, m.entryId))
            }
        }
    }

    return Triple(b.toAnnotatedString(), wikiRanges, autoRanges)
}

private fun appendInline(
    b: AnnotatedString.Builder, text: String, base: SpanStyle,
    linkColor: Color, mutedColor: Color, wikiRanges: MutableList<WikiLinkRange>
) {
    var cursor = 0
    INLINE_TOKENS.findAll(text).forEach { m ->
        if (m.range.first > cursor) {
            b.withStyle(base) { append(text.substring(cursor, m.range.first)) }
        }
        when {
            m.groupValues[2].isNotEmpty() ->
                b.withStyle(base.copy(fontWeight = FontWeight.Bold)) { append(m.groupValues[2]) }
            m.groupValues[4].isNotEmpty() ->
                b.withStyle(base.copy(fontStyle = FontStyle.Italic)) { append(m.groupValues[4]) }
            m.groupValues[6].isNotEmpty() ->
                b.withStyle(base.copy(
                    fontFamily = FontFamily.Monospace, color = mutedColor, fontSize = 13.sp
                )) { append(m.groupValues[6]) }
            m.groupValues[8].isNotEmpty() -> {
                val title = m.groupValues[8].trim()
                val display = m.groupValues[9].ifEmpty { title }
                val start = b.length
                b.withStyle(base.copy(
                    color = linkColor, textDecoration = TextDecoration.Underline
                )) { append(display) }
                wikiRanges.add(WikiLinkRange(start, b.length - 1, title))
            }
        }
        cursor = m.range.last + 1
    }
    if (cursor < text.length) {
        b.withStyle(base) { append(text.substring(cursor)) }
    }
}

/** ルビ表示（MVP: 語全体の上に読み） */
@Composable
fun RubyText(
    text: String,
    reading: String?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.headlineLarge
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        if (!reading.isNullOrBlank()) {
            Text(
                text = reading,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(text = text, style = textStyle)
    }
}