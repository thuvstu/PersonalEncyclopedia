// ui/screen/WhiteboardScreen.kt (新規)
package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeColor
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon
import com.thuvstu.personalencyclopedia.viewmodel.BoardNode
import com.thuvstu.personalencyclopedia.viewmodel.WhiteboardViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteboardScreen(
    onBack: () -> Unit,
    onNavigateToEntry: (String) -> Unit,
    viewModel: WhiteboardViewModel = hiltViewModel()
) {
    val board by viewModel.board.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    // ── ビューポート状態（スクリーン空間で手動変換）──
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var connectMode by remember { mutableStateOf(false) }
    var connectSource by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addQuery by remember { mutableStateOf("") }

    val density = LocalDensity.current
    val nodeW = with(density) { 170.dp.toPx() }
    val nodeH = with(density) { 64.dp.toPx() }

    fun toScreen(wx: Float, wy: Float) = Offset(wx * scale + pan.x, wy * scale + pan.y)
    fun toWorld(sx: Float, sy: Float) = Offset((sx - pan.x) / scale, (sy - pan.y) / scale)
    fun nodeCenter(n: BoardNode) = toScreen(n.node.x, n.node.y) + Offset(nodeW * scale / 2, nodeH * scale / 2)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🗒️ ホワイトボード") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    // 接続モードトグル
                    IconButton(onClick = {
                        connectMode = !connectMode
                        connectSource = null
                    }) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = "接続モード",
                            tint = if (connectMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.searchEntries("")
                showAddDialog = true
            }) { Icon(Icons.Default.Add, contentDescription = "エントリーを追加") }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                // 背景: パン/ズーム（何もない場所のドラッグ）
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, panDelta, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.25f, 3f)
                        val worldCentroid = (centroid - pan) / scale
                        pan = (centroid - worldCentroid * newScale) + panDelta
                        scale = newScale
                    }
                }
        ) {
            // ── 接続線（スクリーン空間のCanvas）──
            Canvas(modifier = Modifier.fillMaxSize()) {
                val posByEntry = board.nodes.associate { it.entry.id to nodeCenter(it) }
                board.edges.forEach { e ->
                    val a = posByEntry[e.entryAId] ?: return@forEach
                    val b = posByEntry[e.entryBId] ?: return@forEach
                    val midX = (a.x + b.x) / 2
                    val path = Path().apply {
                        moveTo(a.x, a.y)
                        cubicTo(midX, a.y, midX, b.y, b.x, b.y)
                    }
                    drawPath(
                        path,
                        color = entryTypeColor(e.relationType).copy(alpha = 0.55f),
                        style = Stroke(width = 2.5f * scale.coerceIn(0.6f, 2f))
                    )
                }
            }

            // ── ノード ──
            board.nodes.forEach { n ->
                val isConnectSrc = connectSource == n.entry.id
                Box(
                    modifier = Modifier
                        .offset { IntOffset(toScreen(n.node.x, n.node.y).x.roundToInt(),
                            toScreen(n.node.x, n.node.y).y.roundToInt()) }
                        // ノードのドラッグ（接続モードでないとき）
                        .pointerInput(n.node.entryId, connectMode) {
                            if (!connectMode) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    // drag はスクリーンpx → ワールドに変換
                                    viewModel.moveNode(
                                        n.node.entryId,
                                        n.node.x + drag.x / scale,
                                        n.node.y + drag.y / scale
                                    )
                                }
                            }
                        }
                        .width(with(density) { nodeW.toDp() })
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isConnectSrc) entryTypeColor(n.entry.type).copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable {
                            if (connectMode) {
                                if (connectSource == null) connectSource = n.entry.id
                                else if (connectSource != n.entry.id) {
                                    viewModel.connect(connectSource!!, n.entry.id)
                                    connectSource = null
                                    connectMode = false
                                }
                            } else {
                                onNavigateToEntry(n.entry.id)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(entryTypeIcon(n.entry.type), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            n.entry.title,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                        // ボードから削除（エントリー自体は消えない）
                        Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .clickable { viewModel.removeNode(n.entry.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "削除",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // 接続モードのガイド表示
            if (connectMode) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        if (connectSource == null) "接続元をタップ" else "接続先をタップ",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // 空の状態
            if (board.nodes.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🗒️", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("右下の＋からエントリーを置いて", style = MaterialTheme.typography.bodyMedium)
                    Text("知識を空間的に並べましょう", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    // ── 追加ダイアログ ──
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("ボードに追加") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = addQuery,
                        onValueChange = { addQuery = it; viewModel.searchEntries(it) },
                        label = { Text("検索") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(Modifier.heightIn(max = 280.dp)) {
                        searchResults.forEach { e ->
                            TextButton(
                                onClick = {
                                    // ビューポート中央のワールド座標に配置
                                    val center = toWorld(600f, 800f)
                                    viewModel.addEntry(e.id, center.x, center.y)
                                    showAddDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${entryTypeIcon(e.type)} ${e.title}",
                                    maxLines = 1, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("閉じる") } }
        )
    }
}