package com.thuvstu.personalencyclopedia.ui.screen

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeColor
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeIcon
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeLabelJa
import com.thuvstu.personalencyclopedia.viewmodel.EntryDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryDetailScreen(
    onBack: () -> Unit,
    onEdit: (type: String, entryId: String) -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel()
) {
    val entry by viewModel.entry.collectAsState()
    val thought by viewModel.thought.collectAsState()
    val definition by viewModel.definition.collectAsState()
    val tags by viewModel.tags.collectAsState()

    var showTagDialog by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

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
                    IconButton(onClick = {
                        viewModel.softDelete()
                        onBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Type badge + title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(entryTypeColor(e.type).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entryTypeIcon(e.type),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = e.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
            }

            // Metadata
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            Text(
                text = "作成: ${sdf.format(Date(e.createdAt))} | 更新: ${sdf.format(Date(e.updatedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Content (user notes)
            if (!e.content.isNullOrBlank()) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📝 メモ", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(e.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Type-specific sections
            when (e.type) {
                "thought" -> thought?.let { t ->
                    if (!t.mood.isNullOrBlank()) {
                        Row {
                            Text("気分: ", style = MaterialTheme.typography.labelMedium)
                            Text(t.mood, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!t.context.isNullOrBlank()) {
                        Row {
                            Text("文脈: ", style = MaterialTheme.typography.labelMedium)
                            Text(t.context, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                "definition" -> definition?.let { d ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = entryTypeColor("definition").copy(alpha = 0.08f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = d.term,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            if (!d.reading.isNullOrBlank()) {
                                Text(
                                    text = d.reading,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!d.field.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(d.field) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            Text(
                                text = d.definition,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // Tags
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏷️ タグ", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { showTagDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "タグを追加",
                                modifier = Modifier.size(18.dp)
                            )
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
        }
    }

    // Add tag dialog
    if (showTagDialog) {
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("タグを追加") },
            text = {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("タグ名") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addTag(tagInput)
                        tagInput = ""
                        showTagDialog = false
                    },
                    enabled = tagInput.isNotBlank()
                ) { Text("追加") }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) { Text("キャンセル") }
            }
        )
    }
}