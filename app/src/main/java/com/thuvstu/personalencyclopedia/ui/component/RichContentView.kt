package com.thuvstu.personalencyclopedia.ui.component

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * §11.7 リッチテキスト描画。
 * WebView + marked.js + KaTeX で Markdown / 数式 / ルビ / wiki-link を描画。
 * marked.js と KaTeX は CDN 読み込み（オンライン時）。オフライン時は簡易表示にフォールバック。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichContentView(
    content: String,
    onWikiLinkClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val html = remember(content) { buildHtml(content) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?, url: String?
                    ): Boolean {
                        url?.let {
                            if (it.startsWith("wiki://")) {
                                onWikiLinkClick(it.removePrefix("wiki://"))
                                return true
                            }
                        }
                        return false
                    }
                }
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    )
}

private fun buildHtml(markdown: String): String {
    // wiki-link → <a href="wiki://title">
    val wikiLinked = markdown.replace(Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?]]""")) { m ->
        val title = m.groupValues[1].trim()
        val display = m.groupValues[2].ifEmpty { title }
        """<a href="wiki://$title">$display</a>"""
    }
    // ルビ {漢字|よみ} → <ruby>
    val rubyApplied = wikiLinked.replace(Regex("""\{([^{}|]+)\|([^{}]+)}""")) { m ->
        """<ruby>${m.groupValues[1]}<rt>${m.groupValues[2]}</rt></ruby>"""
    }

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
          body { font-family: sans-serif; padding: 8px; line-height: 1.7; }
          ruby rt { font-size: 0.5em; }
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
          const raw = ${'$'}{JSON.stringify(${escapeJs(rubyApplied)})};
          try {
            document.getElementById('content').innerHTML = marked.parse(raw);
            renderMathInElement(document.getElementById('content'), {
              delimiters: [
                {left: '${'$'}${'$'}', right: '${'$'}${'$'}', display: true},
                {left: '${'$'}', right: '${'$'}', display: false}
              ]
            });
          } catch (e) {
            document.getElementById('content').innerText = raw;
          }
        </script>
        </body>
        </html>
    """.trimIndent()
}

private fun escapeJs(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r") + "\""