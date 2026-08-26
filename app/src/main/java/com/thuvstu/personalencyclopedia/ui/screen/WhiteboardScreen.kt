package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.viewmodel.WhiteboardViewModel
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
                Text("ボードがありません", modifier = Modifier.padding(16.dp))
            }
            boards.forEach { board ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = { onOpenBoard(board.id) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(board.title, style = MaterialTheme.typography.titleMedium)
                        board.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
                    label = { Text("タイトル") }
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
    val sections by viewModel.sections.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ボード詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "メモ追加")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))) {
            // Sections — pxをdpに正しく変換
            sections.forEach { section ->
                Box(
                    modifier = Modifier
                        .offset { IntOffset(with(density) { section.x.toDp().roundToPx() }, with(density) { section.y.toDp().roundToPx() }) }
                        .size(with(density) { section.width.toDp() }, with(density) { section.height.toDp() })
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                ) {
                    Text(section.title, modifier = Modifier.padding(8.dp))
                }
            }
            // Nodes — ドラッグは蓄積し、終了時にDBへコミット。クリックでエントリーへ遷移
            nodes.forEach { node ->
                var dragOffset by remember(node.id) { mutableStateOf(androidx.compose.ui.geometry.Offset(node.x, node.y)) }
                // node.x/yが外部から更新されたら追従
                LaunchedEffect(node.x, node.y) { dragOffset = androidx.compose.ui.geometry.Offset(node.x, node.y) }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                        .size(with(density) { node.width.toDp() }, with(density) { node.height.toDp() })
                        .pointerInput(node.id) {
                            detectDragGestures(
                                onDragStart = { dragOffset = androidx.compose.ui.geometry.Offset(node.x, node.y) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount
                                },
                                onDragEnd = {
                                    viewModel.moveNode(node.id, dragOffset.x, dragOffset.y)
                                }
                            )
                        }
                        .clickable(enabled = node.entryId != null) { node.entryId?.let { onNavigateToEntry(it) } }
                ) {
                    Card(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                            Text(node.noteId?.let { "📝 メモ" } ?: node.entryId?.let { "📄 エントリー" } ?: "?")
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
                    label = { Text("内容") }
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
}
