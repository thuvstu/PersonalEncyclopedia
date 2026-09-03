package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.viewmodel.WhiteboardViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteboardListScreen(
    onBack: () -> Unit,
    onOpenBoard: (String) -> Unit,
    viewModel: WhiteboardViewModel = hiltViewModel()
) {
    val boards by viewModel.boards.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ホワイトボード") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "新規作成")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (boards.isEmpty()) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("ボードがありません", style = MaterialTheme.typography.titleMedium)
                        Text("思考を地図のように広げてみましょう", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            boards.forEach { board ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    onClick = { onOpenBoard(board.id) }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📌", modifier = Modifier.padding(8.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(board.title, style = MaterialTheme.typography.titleMedium)
                            board.summary?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp).then(Modifier), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; newTitle = "" },
            title = { Text("新規ボード") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("タイトル") },
                    placeholder = { Text("例: 歴史探求ボード") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createBoard(newTitle.trim())
                        newTitle = ""
                        showCreateDialog = false
                    },
                    enabled = newTitle.isNotBlank()
                ) { Text("作成") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newTitle = "" }) { Text("キャンセル") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteboardBoardScreen(
    onBack: () -> Unit,
    onNavigateToEntry: (String) -> Unit,
    viewModel: WhiteboardViewModel = hiltViewModel()
) {
    val nodes by viewModel.nodes.collectAsState()
    val resolvedTitles by viewModel.resolvedTitles.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val currentBoard by viewModel.currentBoard.collectAsState()
    val entryResults by viewModel.entryResults.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showEntryDialog by remember { mutableStateOf(false) }
    var entryQueryText by remember { mutableStateOf("") }
    val density = LocalDensity.current
    var scale by remember { mutableFloatStateOf(1f) }
    var canvasOffset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentBoard?.title ?: "ボード詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        entryQueryText = ""
                        viewModel.setEntryQuery("")
                        showEntryDialog = true
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "エントリーを配置")
                    }
                    Text("${nodes.size}件", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 12.dp))
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "メモ追加")
            }
        }
    ) { padding ->
        // Unit キーのまま最新ノード配置を参照するためのスナップショット
        val latestNodes by rememberUpdatedState(nodes)
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)
                // ジェスチャ振り分け: 開始点がノード上なら子(ノードドラッグ)に譲り、
                // 空白開始のときだけ親がパン/ズームを処理する(奪い合い解消)。
                // 中身は detectTransformGestures 相当(パン+ズーム。回転は従来通り無視)。
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val touch = (down.position - canvasOffset) / scale
                        val onNode = latestNodes.any { n ->
                            touch.x >= n.x && touch.x <= n.x + n.width &&
                                touch.y >= n.y && touch.y <= n.y + n.height
                        }
                        if (onNode) {
                            // 子に譲る: 消費せず指が離れるまで待つのみ
                            var waiting = true
                            while (waiting) {
                                val event = awaitPointerEvent()
                                waiting = event.changes.any { it.pressed }
                            }
                        } else {
                            var zoomAcc = 1f
                            var panAcc = Offset.Zero
                            var pastTouchSlop = false
                            val touchSlop = viewConfiguration.touchSlop
                            var active = true
                            while (active) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.isConsumed }) {
                                    val zoomChange =
                                        if (event.changes.size > 1) event.calculateZoom() else 1f
                                    val panChange = event.calculatePan()
                                    if (!pastTouchSlop) {
                                        zoomAcc *= zoomChange
                                        panAcc += panChange
                                        val centroidSize =
                                            event.calculateCentroidSize(useCurrent = false)
                                        val zoomMotion = abs(1 - zoomAcc) * centroidSize
                                        val panMotion = panAcc.getDistance()
                                        if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                            pastTouchSlop = true
                                        }
                                    }
                                    if (pastTouchSlop) {
                                        // ピンチ中心基準ズーム: 指の下の内容点を固定する
                                        val centroid = event.calculateCentroid(useCurrent = false)
                                        val oldScale = scale
                                        val newScale =
                                            (oldScale * zoomChange).coerceIn(0.3f, 3f)
                                        val factor = newScale / oldScale
                                        canvasOffset =
                                            (canvasOffset - centroid) * factor + centroid + panChange
                                        scale = newScale
                                        // slop超過後は全移動を親のものとして消費する
                                        // (consume/isConsumed は公開API。移動有無の判定は不要)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                active = event.changes.any { it.pressed }
                            }
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = canvasOffset.x, translationY = canvasOffset.y
                    )
            ) {
            // グリッド背景
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 40.dp.toPx()
                val w = size.width
                val h = size.height
                for (x in 0..(w / step).toInt()) {
                    drawLine(Color(0x11000000), start = Offset(x * step, 0f), end = Offset(x * step, h), strokeWidth = 1f)
                }
                for (y in 0..(h / step).toInt()) {
                    drawLine(Color(0x11000000), start = Offset(0f, y * step), end = Offset(w, y * step), strokeWidth = 1f)
                }
            }
            // セクション（背景の枠）
            sections.forEach { section ->
                Box(
                    modifier = Modifier
                        .offset { IntOffset(with(density) { section.x.toDp().roundToPx() }, with(density) { section.y.toDp().roundToPx() }) }
                        .size(with(density) { section.width.toDp() }, with(density) { section.height.toDp() })
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                ) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp).align(Alignment.TopStart)
                    )
                }
            }
            // ノード
            nodes.forEach { node ->
                var dragOffset by remember(node.id) { mutableStateOf(Offset(node.x, node.y)) }
                LaunchedEffect(node.x, node.y) { dragOffset = Offset(node.x, node.y) }
                ElevatedCard(
                    modifier = Modifier
                        .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                        .size(with(density) { node.width.toDp() }, with(density) { node.height.toDp() })
                        // WB-1: キャンバスは graphicsLayer(scale) で描画のみ拡大されるため、
                        // 指の移動量(画面px)をそのまま足すと scale 倍に飛ぶ。/scale で内容座標に戻す。
                        // scale をキーに含めないとクロージャが古い倍率を掴むため (node.id, scale) で再登録する。
                        .pointerInput(node.id, scale) {
                            detectDragGestures(
                                onDragStart = { dragOffset = Offset(node.x, node.y) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount / scale
                                },
                                onDragEnd = {
                                    viewModel.moveNode(node.id, dragOffset.x, dragOffset.y)
                                }
                            )
                        }
                        .clickable(enabled = node.entryId != null) { node.entryId?.let { onNavigateToEntry(it) } },
                    shape = RoundedCornerShape(10.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
                ) {
                    Box(Modifier.fillMaxSize().padding(10.dp)) {
                        Column(Modifier.fillMaxSize()) {
                            Text(
                                node.noteId?.let { "📝 メモ" } ?: "📄 エントリー",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                resolvedTitles[node.id] ?: "…",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3
                            )
                        }
                        IconButton(
                            onClick = { viewModel.deleteNode(node.id) },
                            modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "削除", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (nodes.isEmpty() && sections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))) {
                        Text("＋でメモを追加、エントリーをドラッグして配置", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            }
        }
    }

    if (showAddDialog) {
        var noteContent by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("メモを追加") },
            text = {
                OutlinedTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    label = { Text("内容") },
                    placeholder = { Text("例: この2つの概念は…") },
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addFreeNote(noteContent.trim())
                        showAddDialog = false
                    },
                    enabled = noteContent.isNotBlank()
                ) { Text("追加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("キャンセル") }
            }
        )
    }

    if (showEntryDialog) {
        AlertDialog(
            onDismissRequest = { showEntryDialog = false },
            title = { Text("エントリーを配置") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    OutlinedTextField(
                        value = entryQueryText,
                        onValueChange = {
                            entryQueryText = it
                            viewModel.setEntryQuery(it)
                        },
                        label = { Text("検索（空欄=最近20件）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (entryResults.isEmpty()) {
                            Text(
                                "見つかりません",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        entryResults.forEach { e ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        viewModel.addEntry(e.id)
                                        showEntryDialog = false
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(e.title, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    Text(
                                        e.type,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showEntryDialog = false }) { Text("閉じる") }
            }
        )
    }
}
