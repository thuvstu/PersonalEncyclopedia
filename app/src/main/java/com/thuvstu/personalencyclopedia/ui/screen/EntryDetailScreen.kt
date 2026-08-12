package com.thuvstu.personalencyclopedia.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.ui.component.AttachmentSection
import com.thuvstu.personalencyclopedia.ui.component.ConnectionSection
import com.thuvstu.personalencyclopedia.ui.component.EntryTypeSection
import com.thuvstu.personalencyclopedia.ui.component.RichContentView
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeColor
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeLabelJa
import com.thuvstu.personalencyclopedia.viewmodel.EntryDetailViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryDetailScreen(
    onBack: () -> Unit,
    onEdit: (type: String, entryId: String) -> Unit,
    onNavigateToEntry: (String) -> Unit,
    onNavigateToWiki: (String) -> Unit,          // ★追加
    viewModel: EntryDetailViewModel = hiltViewModel()
) {
    val entry by viewModel.entry.collectAsState()
    val thought by viewModel.thought.collectAsState()
    val definition by viewModel.definition.collectAsState()
    val extension by viewModel.extension.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val connections by viewModel.connections.collectAsState()
    val connectionTypeDefs by viewModel.connectionTypeDefs.collectAsState()
    val relatedEntries by viewModel.relatedEntries.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val tagSuggestions by viewModel.tagSuggestions.collectAsState()

    var showConnectionDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTargetEntryId by remember { mutableStateOf<String?>(null) }
    var selectedRelationType by remember { mutableStateOf("related") }
    var connectionNote by remember { mutableStateOf("") }
    var connectionStrength by remember { mutableStateOf(0.5f) }
    var showTagDialog by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> uris.forEach { viewModel.addAttachment(it) } }

    LaunchedEffect(Unit) {
        viewModel.message.collectLatest {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    val e = entry
    if (e == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entryTypeLabelJa(e.type)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (e.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "お気に入り",
                            tint = if (e.isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onEdit(e.type, e.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = "編集")
                    }
                    IconButton(onClick = { viewModel.softDelete(); onBack() }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 型バッジ + タイトル
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(entryTypeColor(e.type).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(entryTypeIcon(e.type), style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    e.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
            }

            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            Text(
                "作成: ${sdf.format(Date(e.createdAt))} | 更新: ${sdf.format(Date(e.updatedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ★本文を RichContentView（Markdown+KaTeX+ルビ+リンク）で描画
            if (!e.content.isNullOrBlank()) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📝 メモ", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        RichContentView(
                            content = e.content,
                            onWikiLinkClick = { target ->
                                val title = target.removePrefix("wiki/")
                                viewModel.resolveWikiLink(title) { id ->
                                    id?.let { onNavigateToEntry(it) }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp, max = 400.dp)
                        )
                    }
                }
            }

            // 型固有セクション（全13型）
            EntryTypeSection(
                type = e.type,
                extension = extension,
                thought = thought,
                definition = definition
            )

            // 添付画像
            AttachmentSection(
                attachments = attachments,
                onPickImage = { imagePicker.launch("image/*") },
                onRemove = { viewModel.removeAttachment(it) }
            )

            // クイズ生成
            OutlinedButton(
                onClick = { viewModel.generateQuizzesFromThisEntry() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("このエントリーからクイズを自動生成")
            }

            // ★記事化ボタン
            OutlinedButton(
                onClick = {
                    viewModel.draftArticleFromEntry { articleId ->
                        if (articleId.isNotBlank()) onNavigateToWiki(articleId)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("📚 このエントリーを記事化する")
            }

            // タグ
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏷️ タグ", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { showTagDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "タグ追加", modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (tags.isEmpty()) {
                        Text(
                            "タグなし",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tags.forEach { tag ->
                                InputChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text(tag.name) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { viewModel.removeTag(tag.id) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "削除",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 接続
            ConnectionSection(
                connections = connections,
                typeDefs = connectionTypeDefs,
                onRemoveConnection = { viewModel.removeConnection(it) },
                onNavigateToEntry = { id -> onNavigateToEntry(id) },
                onAddConnection = { showConnectionDialog = true }
            )

            // 関連エントリー
            if (relatedEntries.isNotEmpty()) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🔗 関連エントリー", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        relatedEntries.forEach { rel ->
                            TextButton(
                                onClick = { onNavigateToEntry(rel.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${entryTypeIcon(rel.type)} ${rel.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 接続追加ダイアログ
    if (showConnectionDialog) {
        AlertDialog(
            onDismissRequest = {
                showConnectionDialog = false; searchQuery = ""
                selectedTargetEntryId = null; connectionNote = ""; connectionStrength = 0.5f
            },
            title = { Text("エントリーを接続") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.searchEntriesForConnection(it)
                        },
                        label = { Text("接続先を検索") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val candidates = if (searchQuery.isBlank()) relatedEntries else searchResults
                    if (candidates.isNotEmpty()) {
                        Text(
                            if (searchQuery.isBlank()) "関連から選ぶ:" else "検索結果:",
                            style = MaterialTheme.typography.labelSmall
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            candidates.take(6).forEach { item ->
                                FilterChip(
                                    selected = selectedTargetEntryId == item.id,
                                    onClick = { selectedTargetEntryId = item.id },
                                    label = {
                                        Text(
                                            "${entryTypeIcon(item.type)} ${item.title.take(12)}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Text("関係タイプ:", style = MaterialTheme.typography.labelSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        connectionTypeDefs.forEach { typeDef ->
                            FilterChip(
                                selected = selectedRelationType == typeDef.name,
                                onClick = { selectedRelationType = typeDef.name },
                                label = { Text(typeDef.labelJa, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Text(
                        "強度: %.0f%%".format(connectionStrength * 100),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        value = connectionStrength,
                        onValueChange = { connectionStrength = it },
                        valueRange = 0.1f..1f
                    )
                    OutlinedTextField(
                        value = connectionNote,
                        onValueChange = { connectionNote = it },
                        label = { Text("メモ（任意）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetId = selectedTargetEntryId
                        if (targetId != null) {
                            viewModel.addConnection(
                                targetId, selectedRelationType, connectionStrength,
                                connectionNote.takeIf { it.isNotBlank() }
                            )
                            showConnectionDialog = false
                            searchQuery = ""; selectedTargetEntryId = null
                            connectionNote = ""; connectionStrength = 0.5f
                        }
                    },
                    enabled = selectedTargetEntryId != null
                ) { Text("接続") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConnectionDialog = false; searchQuery = ""
                    selectedTargetEntryId = null; connectionNote = ""; connectionStrength = 0.5f
                }) { Text("キャンセル") }
            }
        )
    }

    // タグ追加ダイアログ
    if (showTagDialog) {
        AlertDialog(
            onDismissRequest = { showTagDialog = false; viewModel.onTagInputChange("") },
            title = { Text("タグ追加") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = {
                            tagInput = it
                            viewModel.onTagInputChange(it)
                        },
                        label = { Text("タグ名") },
                        singleLine = true
                    )
                    if (tagSuggestions.isNotEmpty()) {
                        Text("既存の類似タグ（表記揺れ？）:", style = MaterialTheme.typography.labelSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            tagSuggestions.take(4).forEach { s ->
                                FilterChip(
                                    selected = false,
                                    onClick = { tagInput = s.existingTag.name },
                                    label = {
                                        Text(
                                            "${s.existingTag.name} (${"%.0f".format(s.similarity * 100)}%)",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addTag(tagInput)
                        tagInput = ""; showTagDialog = false
                        viewModel.onTagInputChange("")
                    },
                    enabled = tagInput.isNotBlank()
                ) { Text("追加") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTagDialog = false; viewModel.onTagInputChange("")
                }) { Text("キャンセル") }
            }
        )
    }
}