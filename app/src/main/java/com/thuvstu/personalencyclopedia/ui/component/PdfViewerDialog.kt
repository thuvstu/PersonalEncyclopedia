package com.thuvstu.personalencyclopedia.ui.component

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ★wt43 (mismatch §1.3): アプリ内PDFビューア。
 * OS標準の `android.graphics.pdf.PdfRenderer`(API 21+)で1ページずつビットマップ化する。
 * 外部ライブラリ・ネットワーク不要。ページ送り＋ピンチズーム(1〜4倍)＋ドラッグ。
 * 大きなPDFでも1ページ分しかメモリに持たない(画面幅にフィットする解像度で描画)。
 */
@Composable
fun PdfViewerDialog(file: File, title: String, onDismiss: () -> Unit) {
    var pageIndex by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // 画面幅に合わせた解像度で描画(拡大時の粗さを抑えるため1.5倍、上限2048px)
    val targetWidthPx = with(density) { (configuration.screenWidthDp * 1.5f).dp.roundToPx() }.coerceIn(480, 2048)

    LaunchedEffect(file, pageIndex) {
        val result = withContext(Dispatchers.IO) {
            runCatching { renderPage(file, pageIndex, targetWidthPx) }
        }
        result.onSuccess { (bmp, count) ->
            bitmap = bmp; pageCount = count; error = null
            scale = 1f; offset = Offset.Zero
        }.onFailure { e ->
            error = e.message ?: "PDFを描画できませんでした"
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ヘッダ: タイトル + 閉じる
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "閉じる") }
                }

                // ページ本体
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = newScale
                                offset = if (newScale == 1f) Offset.Zero else offset + pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = bitmap
                    when {
                        error != null -> Text(
                            "⚠ $error", color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(24.dp)
                        )
                        bmp == null -> CircularProgressIndicator()
                        else -> Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "PDF ${pageIndex + 1}ページ目",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale, scaleY = scale,
                                    translationX = offset.x, translationY = offset.y
                                )
                        )
                    }
                }

                // フッタ: ページ送り
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { if (pageIndex > 0) pageIndex-- }, enabled = pageIndex > 0) { Text("◀ 前") }
                    Text(
                        if (pageCount > 0) "${pageIndex + 1} / $pageCount" else "–",
                        style = MaterialTheme.typography.labelLarge
                    )
                    TextButton(
                        onClick = { if (pageIndex < pageCount - 1) pageIndex++ },
                        enabled = pageIndex < pageCount - 1
                    ) { Text("次 ▶") }
                }
            }
        }
    }
}

/** 指定ページを画面幅相当のビットマップに描画して (bitmap, 総ページ数) を返す。IOスレッドで呼ぶこと */
private fun renderPage(file: File, index: Int, targetWidthPx: Int): Pair<Bitmap, Int> {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        val renderer = PdfRenderer(pfd)
        try {
            val count = renderer.pageCount
            require(count > 0) { "ページがありません" }
            val page = renderer.openPage(index.coerceIn(0, count - 1))
            try {
                val ratio = targetWidthPx.toFloat() / page.width.coerceAtLeast(1)
                val w = targetWidthPx
                val h = (page.height * ratio).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp to count
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
        }
    }
}
