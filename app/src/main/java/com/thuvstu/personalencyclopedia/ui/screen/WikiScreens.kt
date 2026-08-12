package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.ui.component.RichContentView
import com.thuvstu.personalencyclopedia.viewmodel.WikiViewModel
import java.text.SimpleDateFormat
import java.util.*

// ── 記事一覧 ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiListScreen(
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onNewArticle: () -> Unit,
    viewModel: WikiViewModel = hiltViewModel()
) {
    val articles by viewModel.articles.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📚 Wikipediaビルダー") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewArticle) {
                Icon(Icons.Default.Add, contentDescription = "新規記事")
            }
        }
    ) { padding ->
        if (articles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("📚", style = MaterialTheme.typography.displayLarge)
                    Text("まだ記事がありません", style = MaterialTheme.typography.titleMedium)
                    Text("右下の＋から作成、またはエントリー詳細の「記事化」から生成できます",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(articles, key = { it.id }) { article ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenArticle(article.id) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(article.title, style = MaterialTheme.typography.titleMedium)
                            article.summary?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "更新: " + SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                                    .format(Date(article.updatedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 記事閲覧（RichContentViewで描画）─────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiArticleScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onNavigateToEntry: (String) -> Unit = {},
    viewModel: WikiViewModel = hiltViewModel()
) {
    val article by viewModel.article.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(article?.title ?: "記事") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    article?.let { a ->
                        IconButton(onClick = { onEdit(a.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "編集")
                        }
                    }
                }
            )
        }
    ) { padding ->
        article?.let { a ->
            RichContentView(
                content = a.contentMd,
                onWikiLinkClick = { title ->
                    // wiki-link → タイトルで記事を検索して遷移（実装はViewModel連携）
                },
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
            )
        }
    }
}

// ── 記事編集（Markdown入力 + プレビュー）─────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiEditScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: WikiViewModel = hiltViewModel()
) {
    val article by viewModel.article.collectAsState()
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }

    LaunchedEffect(article) {
        article?.let {
            title = it.title
            content = it.contentMd
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "新規記事" else "記事を編集") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    TextButton(onClick = { preview = !preview }) {
                        Text(if (preview) "編集" else "プレビュー")
                    }
                    TextButton(
                        onClick = {
                            viewModel.save(title, content)
                            onSaved(viewModel.articleId ?: "")
                        },
                        enabled = title.isNotBlank()
                    ) { Text("保存") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("記事タイトル *") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (preview) {
                RichContentView(
                    content = content,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    label = { Text("本文（Markdown / [[wiki-link]] / {漢字|よみ} / \$数式\$）") },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}