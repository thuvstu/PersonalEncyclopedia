package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.viewmodel.QuizListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizListScreen(
    onBack: () -> Unit,
    onEditQuiz: (String) -> Unit,
    onNewQuiz: () -> Unit,
    viewModel: QuizListViewModel = hiltViewModel()
) {
    val quizzes by viewModel.filteredQuizzes.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📝 クイズ一覧 (${quizzes.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewQuiz) {
                Icon(Icons.Default.Add, contentDescription = "新規クイズ")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 種別フィルタ
            Row(Modifier.horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(null to "すべて", "qa" to "記述", "mcq" to "4択",
                    "fill_blank" to "穴埋め").forEach { (type, label) ->
                    FilterChip(
                        selected = typeFilter == type,
                        onClick = { viewModel.setTypeFilter(type) },
                        label = { Text(label) }
                    )
                }
            }

            if (quizzes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("クイズがありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(quizzes, key = { it.id }) { quiz ->
                        Card(Modifier.fillMaxWidth().clickable { onEditQuiz(quiz.id) }) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SuggestionChip(onClick = {}, label = {
                                        Text(when (quiz.quizType) {
                                            "qa" -> "記述"; "mcq" -> "4択"
                                            "fill_blank" -> "穴埋め"; else -> quiz.quizType
                                        })
                                    })
                                    Spacer(Modifier.width(8.dp))
                                    Text("難易度 ${quiz.difficulty}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(quiz.question, style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2)
                                Text("正解: ${quiz.answer}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}