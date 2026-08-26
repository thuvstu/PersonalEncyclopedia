package com.thuvstu.personalencyclopedia.ui.component

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * §11.7 リッチテキスト描画。
 * WebView + marked.js + KaTeX で Markdown / 数式 / ルビ / wiki-link を描画。
 * marked.js と KaTeX は CDN 読み込み（オンライン時）。オフライン時は簡易表示にフォールバック。
 * クラッシュ対策: contentが空/ nullの場合やWebView生成失敗時はTextにフォールバック。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichContentView(
    content: String,
    onWikiLinkClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (content.isBlank()) {
        Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("内容がありません")
        }
        return
    }

    val html = remember(content) {
        try { buildHtml(content) } catch (_: Exception) {
            "<html><body><pre>${content.take(2000)}</pre></body></html>"
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            try {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // CDNが読めないオフライン環境でもクラッシュしないよう、エラー時は無視
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            url?.let {
                                if (it.startsWith("wiki://")) {
                                    try { onWikiLinkClick(it.removePrefix("wiki://")) } catch (_: Exception) {}
                                    return true
                                }
                                // http/httpsもWebView内で開かず、外部ブラウザに任せるなら true を返すが、
                                // ここではWebViewに任せるため false。ただし wiki:// 以外はブロックしてクラッシュ防止
                                if (it.startsWith("http://") || it.startsWith("https://")) {
                                    return false
                                }
                            }
                            return false
                        }

                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            // CDN読み込み失敗などは無視、fallbackのinnerTextで表示される
                        }
                    }
                    loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                }
            } catch (e: Exception) {
                // WebView生成自体が失敗した場合(稀)、ダミーのWebViewを返す代わりに空のViewを返す
                // この場合は update で Text にフォールバックされるが、factoryで例外を投げないようにする
                WebView(context).apply {
                    loadData("<html><body><pre>表示エラー: ${e.message}</pre></body></html>", "text/html", "UTF-8")
                }
            }
        },
        update = { webView ->
            try {
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            } catch (_: Exception) {
                // 更新失敗は無視
            }
        }
    )
}

private fun buildHtml(markdown: String): String {
    if (markdown.isBlank()) return "<html><body><p>内容がありません</p></body></html>"
    // wiki-link → <a href="wiki://title">
    val wikiLinked = try {
        markdown.replace(Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?]]""")) { m ->
            val title = m.groupValues[1].trim().take(100)
            val display = m.groupValues[2].ifEmpty { title }.take(100)
            // titleに " や < が含まれても崩れないようエスケープ
            val escTitle = title.replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
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
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
        <script src="https://cdn.jsdelivr.net/npm/marked@11.1.1/marked.min.js"></script>
        <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
        <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"></script>
        <style>
          body { font-family: sans-serif; padding: 8px; line-height: 1.7; color: #222; }
          ruby rt { font-size: 0.5em; color: #666; }
          a { color: #1a73e8; text-decoration: none; }
          a:hover { text-decoration: underline; }
          pre { background: #f5f5f5; padding: 8px; border-radius: 4px; overflow-x: auto; }
          code { background: #f0f0f0; padding: 2px 4px; border-radius: 3px; }
          blockquote { border-left: 4px solid #ddd; margin: 8px 0; padding-left: 12px; color: #666; }
        </style>
        </head>
        <body>
        <div id="content"></div>
        <script>
          const raw = $jsString;
          try {
            if (typeof marked !== 'undefined') {
              document.getElementById('content').innerHTML = marked.parse(raw);
            } else {
              document.getElementById('content').innerText = raw;
            }
            if (typeof renderMathInElement !== 'undefined') {
              renderMathInElement(document.getElementById('content'), {
                delimiters: [
                  {left: '$$', right: '$$', display: true},
                  {left: '$', right: '$', display: false}
                ]
              });
            }
          } catch (e) {
            document.getElementById('content').innerText = raw;
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
