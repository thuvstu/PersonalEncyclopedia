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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.db.entity.WhiteboardEdgeEntity
import com.thuvstu.personalencyclopedia.viewmodel.WhiteboardViewModel
import kotlinx.coroutines.flow.collectLatest
import android.widget.Toast
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
    val edges by viewModel.edges.collectAsState()
    val linkSourceNodeId by viewModel.linkSourceNodeId.collectAsState()
    val currentBoard by viewModel.currentBoard.collectAsState()
    val entryResults by viewModel.entryResults.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    // ★P3-1: エッジのラベル編集/削除ダイアログ
    var editEdge by remember { mutableStateOf<WhiteboardEdgeEntity?>(null) }
    var editEdgeLabel by remember { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.message.collectLatest { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    var showEntryDialog by remember { mutableStateOf(false) }
    var entryQueryText by remember { mutableStateOf("") }
    // ★P1-1: セクション作成・改名ダイアログ
    var showSectionDialog by remember { mutableStateOf(false) }
    var sectionTitleText by remember { mutableStateOf("") }
    var renameSectionId by remember { mutableStateOf<String?>(null) }
    var renameSectionText by remember { mutableStateOf("") }
    val density = LocalDensity.current
    var scale by remember { mutableFloatStateOf(1f) }
    var canvasOffset by remember { mutableStateOf(Offset.Zero) }
    // ★P3-1: ドラッグ中のノード位置（エッジ描画が指に追従するための共有状態。確定後は消す）
    val livePositions = remember { mutableStateMapOf<String, Offset>() }

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
                        sectionTitleText = ""
                        showSectionDialog = true
                    }) {
                        Icon(Icons.Default.Dashboard, contentDescription = "セクション追加")
                    }
                    IconButton(onClick = {
                        entryQueryText = ""
                        viewModel.setEntryQuery("")
                        showEntryDialog = true
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "エントリーを配置")
                    }
                    Text(
                        if (edges.isEmpty()) "${nodes.size}件" else "${nodes.size}件 · 🔗${edges.size}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "メモ追加")
            }
        }
    ) { padding ->
        // ★P3-1: 接続モードのバナー（起点選択後、次にタップしたカードへ線を張る）
        val linkSourceTitle = linkSourceNodeId?.let { resolvedTitles[it] }
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
            // ★P3-1: エッジ（接続線）。ノードの中心同士を結ぶ。
            // 座標系はノードと同じ内容px（node.x/y/width/height はpx値。offset{IntOffset}で直接使われている）。
            // ドラッグ中は livePositions を優先して線が指に追従する。
            val edgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            val nodeById = remember(nodes) { nodes.associateBy { it.id } }
            fun centerOf(nodeId: String): Offset? {
                val n = nodeById[nodeId] ?: return null
                val pos = livePositions[nodeId] ?: Offset(n.x, n.y)
                return Offset(pos.x + n.width / 2f, pos.y + n.height / 2f)
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                edges.forEach { edge ->
                    val start = centerOf(edge.sourceNodeId) ?: return@forEach
                    val end = centerOf(edge.targetNodeId) ?: return@forEach
                    drawLine(edgeColor, start = start, end = end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            // エッジ中点のチップ（ラベル表示＋タップで編集/削除）
            edges.forEach { edge ->
                val a = centerOf(edge.sourceNodeId) ?: return@forEach
                val b = centerOf(edge.targetNodeId) ?: return@forEach
                val mx = (a.x + b.x) / 2f
                val my = (a.y + b.y) / 2f
                Surface(
                    modifier = Modifier
                        .offset { IntOffset(mx.roundToInt() - 24, my.roundToInt() - 12) }
                        .clickable {
                            editEdge = edge
                            editEdgeLabel = edge.label ?: ""
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        edge.label?.takeIf { it.isNotBlank() } ?: "🔗",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            // セクション（背景の枠＋タイトル改名＋削除。★P1-1でCRUD開通）
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
                            .clickable {
                                renameSectionId = section.id
                                renameSectionText = section.title
                            }
                    )
                    IconButton(
                        onClick = { viewModel.deleteSection(section.id) },
                        modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "セクション削除", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // ノード
            nodes.forEach { node ->
                var dragOffset by remember(node.id) { mutableStateOf(Offset(node.x, node.y)) }
                LaunchedEffect(node.x, node.y) {
                    dragOffset = Offset(node.x, node.y)
                    livePositions.remove(node.id)   // ★P3-1: DB位置が追いついたら live 位置を破棄
                }
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
                                    livePositions[node.id] = dragOffset
                                },
                                onDragEnd = {
                                    // DB反映(node.x/y更新)まで live 位置を保持し、線が一瞬戻るのを防ぐ
                                    viewModel.moveNode(node.id, dragOffset.x, dragOffset.y)
                                },
                                onDragCancel = { livePositions.remove(node.id) }
                            )
                        }
                        // ★P3-1: 接続モード中はタップで接続先を確定。通常時は従来通りentryへ遷移
                        .clickable(enabled = linkSourceNodeId != null || node.entryId != null) {
                            if (linkSourceNodeId != null) viewModel.completeLink(node.id)
                            else node.entryId?.let { onNavigateToEntry(it) }
                        },
                    shape = RoundedCornerShape(10.dp),
                    colors = if (linkSourceNodeId == node.id)
                        CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    else CardDefaults.elevatedCardColors(),
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
                        // ★P3-1: 接続の起点にする（もう一度押すと解除）
                        IconButton(
                            onClick = {
                                if (linkSourceNodeId == node.id) viewModel.cancelLink() else viewModel.startLink(node.id)
                            },
                            modifier = Modifier.align(Alignment.BottomEnd).size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Link, contentDescription = "接続",
                                modifier = Modifier.size(14.dp),
                                tint = if (linkSourceNodeId == node.id) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (nodes.isEmpty() && sections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))) {
                        Text("＋でメモを追加、🔍でエントリーを配置。カードの🔗→別カードで線が引けます", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            }
            // ★P3-1: 接続モードのバナー（graphicsLayerの外＝ズームに追従しない固定UI）
            if (linkSourceNodeId != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "「${(linkSourceTitle ?: "…").take(14)}」→ 接続先のカードをタップ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = { viewModel.cancelLink() }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("取消", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    // ★P3-1: エッジのラベル編集／削除
    editEdge?.let { edge ->
        AlertDialog(
            onDismissRequest = { editEdge = null },
            title = { Text("接続線") },
            text = {
                Column {
                    Text(
                        "${(resolvedTitles[edge.sourceNodeId] ?: "…").take(16)} ↔ ${(resolvedTitles[edge.targetNodeId] ?: "…").take(16)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editEdgeLabel,
                        onValueChange = { editEdgeLabel = it },
                        label = { Text("ラベル（任意）") },
                        placeholder = { Text("例: 原因→結果") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setEdgeLabel(edge, editEdgeLabel)
                    editEdge = null
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.deleteEdge(edge)
                        editEdge = null
                    }) { Text("線を削除", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editEdge = null }) { Text("閉じる") }
                }
            }
        )
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

    // ★P1-1: セクション作成ダイアログ
    if (showSectionDialog) {
        AlertDialog(
            onDismissRequest = { showSectionDialog = false },
            title = { Text("セクションを追加") },
            text = {
                OutlinedTextField(
                    value = sectionTitleText,
                    onValueChange = { sectionTitleText = it },
                    label = { Text("タイトル") },
                    placeholder = { Text("例: 第1章の整理") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createSection(sectionTitleText.trim())
                        showSectionDialog = false
                    },
                    enabled = sectionTitleText.isNotBlank()
                ) { Text("追加") }
            },
            dismissButton = {
                TextButton(onClick = { showSectionDialog = false }) { Text("キャンセル") }
            }
        )
    }

    // ★P1-1: セクション改名ダイアログ
    if (renameSectionId != null) {
        AlertDialog(
            onDismissRequest = { renameSectionId = null },
            title = { Text("セクション名を変更") },
            text = {
                OutlinedTextField(
                    value = renameSectionText,
                    onValueChange = { renameSectionText = it },
                    label = { Text("タイトル") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameSectionId?.let { viewModel.renameSection(it, renameSectionText.trim()) }
                        renameSectionId = null
                    },
                    enabled = renameSectionText.isNotBlank()
                ) { Text("変更") }
            },
            dismissButton = {
                TextButton(onClick = { renameSectionId = null }) { Text("キャンセル") }
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
