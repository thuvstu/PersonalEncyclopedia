package com.thuvstu.personalencyclopedia.ui.component

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs

/**
 * §11.7 リッチテキスト描画。
 * WebView + marked.js + KaTeX で Markdown / 数式 / ルビ / wiki-link を描画。
 *
 * marked.js は assets/ に同梱（オフラインでも Markdown と wiki-link が描画される）。
 * KaTeX は CDN（数式はオンライン時のみ。失敗しても本文は残る）。
 * MaterialTheme の色を CSS 変数で注入し、ダークモードで白い箱にならないようにする。
 * autoHeight=true のとき scrollHeight に合わせて高さを内容に合わせ、外側スクロールとの競合を避ける。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichContentView(
    content: String,
    onWikiLinkClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    autoHeight: Boolean = true
) {
    if (content.isBlank()) {
        Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("内容がありません")
        }
        return
    }

    val scheme = MaterialTheme.colorScheme
    val palette = remember(scheme) {
        RichPalette(
            fg = scheme.onSurface.toCss(),
            bg = "transparent",
            link = scheme.primary.toCss(),
            muted = scheme.onSurfaceVariant.toCss(),
            codeBg = scheme.surfaceVariant.toCss(),
            quoteBorder = scheme.outline.toCss()
        )
    }
    val html = remember(content, palette) {
        try { buildHtml(content, palette) } catch (_: Exception) {
            "<html><body><pre>${content.take(2000)}</pre></body></html>"
        }
    }

    var contentHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val heightMod = if (autoHeight) {
        val minPx = with(density) { 40.dp.roundToPx() }
        val h = contentHeightPx.coerceAtLeast(minPx)
        Modifier.fillMaxWidth().height(with(density) { h.toDp() })
    } else Modifier

    AndroidView(
        modifier = modifier.then(heightMod),
        factory = { context ->
            try {
                WebView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    if (Build.VERSION.SDK_INT >= 33) {
                        settings.isAlgorithmicDarkeningAllowed = false
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        settings.forceDark = WebSettings.FORCE_DARK_OFF
                    }
                    isVerticalScrollBarEnabled = !autoHeight
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = android.view.View.OVER_SCROLL_NEVER
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            url?.let {
                                if (it.startsWith("wiki://")) {
                                    // ★wt46: href は buildHtml で Uri.encode 済み。WebView(Chromium)は非ASCIIを
                                    // %エンコードして渡してくるため、復号しないと日本語タイトルが findByTitle に一致しない
                                    val title = try { Uri.decode(it.removePrefix("wiki://")) } catch (_: Exception) { it.removePrefix("wiki://") }
                                    try { onWikiLinkClick(title) } catch (_: Exception) {}
                                    return true
                                }
                                if (it.startsWith("http://") || it.startsWith("https://")) {
                                    return false
                                }
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (autoHeight) view?.let { reportContentHeight(it) { px ->
                                if (px > 0 && abs(px - contentHeightPx) > 2) contentHeightPx = px
                            } }
                        }

                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            // KaTeX CDN 失敗などは無視。marked.js は assets から読む
                        }
                    }
                    loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
                }
            } catch (e: Exception) {
                WebView(context).apply {
                    loadData("<html><body><pre>表示エラー: ${e.message}</pre></body></html>", "text/html", "UTF-8")
                }
            }
        },
        update = { webView ->
            try {
                webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
            } catch (_: Exception) {
                // 更新失敗は無視
            }
        }
    )
}

private fun reportContentHeight(view: WebView, onPx: (Int) -> Unit) {
    val js = """
        (function(){
          var b = document.body, e = document.documentElement, c = document.getElementById('content');
          if (b) b.style.height = 'auto';
          if (e) e.style.height = 'auto';
          return Math.max(
            b ? b.scrollHeight : 0,
            e ? e.scrollHeight : 0,
            c ? c.scrollHeight : 0
          );
        })()
    """.trimIndent()
    view.post {
        view.evaluateJavascript(js) { value ->
            val px = value?.trim('"')?.toDoubleOrNull()?.toInt() ?: return@evaluateJavascript
            onPx(px)
        }
    }
    // KaTeX が後から高さを増やす場合に備え、短い遅延でもう一度測る
    view.postDelayed({
        view.evaluateJavascript(js) { value ->
            val px = value?.trim('"')?.toDoubleOrNull()?.toInt() ?: return@evaluateJavascript
            onPx(px)
        }
    }, 350)
}

private data class RichPalette(
    val fg: String,
    val bg: String,
    val link: String,
    val muted: String,
    val codeBg: String,
    val quoteBorder: String
)

private fun Color.toCss(): String {
    val argb = toArgb()
    val a = (argb ushr 24) and 0xFF
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    return if (a >= 255) "#%02x%02x%02x".format(r, g, b)
    else "rgba($r,$g,$b,${"%.3f".format(a / 255f)})"
}

private fun buildHtml(markdown: String, palette: RichPalette): String {
    if (markdown.isBlank()) return "<html><body><p>内容がありません</p></body></html>"
    // wiki-link → <a href="wiki://title">
    val wikiLinked = try {
        markdown.replace(Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?]]""")) { m ->
            val title = m.groupValues[1].trim().take(100)
            val display = m.groupValues[2].ifEmpty { title }.take(100)
            // ★wt46: タイトルはURLエンコードして埋め込む(空白・"・<・#・?・日本語を含んでも href が壊れない)。
            // クリック側(shouldOverrideUrlLoading)が Uri.decode して元のタイトルに戻す
            val escTitle = Uri.encode(title)
            val escDisplay = display.replace("<", "&lt;").replace(">", "&gt;")
            """<a href="wiki://$escTitle">$escDisplay</a>"""
        }
    } catch (_: Exception) { markdown }

    // ルビ {漢字|よみ} → <ruby>
    val rubyApplied = try {
        wikiLinked.replace(Regex("""\{([^{}|]+)\|([^{}]+)}""")) { m ->
            val kanji = m.groupValues[1].take(50).replace("<", "&lt;")
            val yomi = m.groupValues[2].take(50).replace("<", "&lt;")
            """<ruby>$kanji<rt>$yomi</rt></ruby>"""
        }
    } catch (_: Exception) { wikiLinked }

    // JS文字列として安全に埋め込むため、escapeJsでクォート済み文字列を生成
    val jsString = escapeJs(rubyApplied)

    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <script src="marked.min.js"></script>
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
        <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
        <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"></script>
        <style>
          :root {
            --fg: ${palette.fg};
            --bg: ${palette.bg};
            --link: ${palette.link};
            --muted: ${palette.muted};
            --code-bg: ${palette.codeBg};
            --quote-border: ${palette.quoteBorder};
          }
          html, body { margin: 0; padding: 0; background: var(--bg); color: var(--fg); }
          body { font-family: sans-serif; padding: 0; line-height: 1.7; color: var(--fg); }
          ruby rt { font-size: 0.5em; color: var(--muted); }
          a { color: var(--link); text-decoration: none; }
          a:hover { text-decoration: underline; }
          pre { background: var(--code-bg); padding: 8px; border-radius: 4px; overflow-x: auto; }
          code { background: var(--code-bg); padding: 2px 4px; border-radius: 3px; }
          blockquote { border-left: 4px solid var(--quote-border); margin: 8px 0; padding-left: 12px; color: var(--muted); }
          img { max-width: 100%; }
        </style>
        </head>
        <body>
        <div id="content"></div>
        <script>
          const raw = $jsString;
          const el = document.getElementById('content');
          try {
            if (typeof marked !== 'undefined') {
              if (marked.setOptions) marked.setOptions({ gfm: true, breaks: true });
              el.innerHTML = marked.parse(raw);
            } else {
              // marked 未読込でも wiki-link/ruby は既に HTML。innerText だと生タグが見える
              el.innerHTML = raw;
            }
            if (typeof renderMathInElement !== 'undefined') {
              renderMathInElement(el, {
                delimiters: [
                  {left: '$$', right: '$$', display: true},
                  {left: '$', right: '$', display: false}
                ]
              });
            }
          } catch (e) {
            el.innerHTML = raw;
          }
        </script>
        </body>
        </html>
    """.trimIndent()
}

private fun escapeJs(s: String): String {
    // JS文字列リテラルとして安全に埋め込むため、JSON的なエスケープを行う
    // 制御文字やクォートをエスケープし、全体を "..." で囲む
    val esc = s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("<", "\\u003c")
        .take(20000) // 長すぎるMarkdownは切り詰めてWebViewのメモリを保護
    return "\"$esc\""
}
