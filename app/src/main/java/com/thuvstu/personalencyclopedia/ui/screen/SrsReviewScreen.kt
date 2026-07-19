package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.ui.theme.entryTypeColor
import com.thuvstu.personalencyclopedia.viewmodel.SrsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SrsReviewScreen(
    onBack: () -> Unit,
    viewModel: SrsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("単語帳復習") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                is SrsViewModel.SrsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is SrsViewModel.SrsUiState.Empty -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎉", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "復習完了！",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            "現在、復習期限のカードはありません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        FilledTonalButton(onClick = { viewModel.loadDueCards() }) {
                            Text("再読み込み")
                        }
                    }
                }

                is SrsViewModel.SrsUiState.Reviewing -> {
                    val card = state.cards[state.currentIndex]
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Progress
                        LinearProgressIndicator(
                            progress = {
                                state.currentIndex.toFloat() / state.cards.size
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${state.currentIndex + 1} / ${state.cards.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = entryTypeColor("definition").copy(alpha = 0.06f)
                            ),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Term
                                Text(
                                    text = card.term,
                                    style = MaterialTheme.typography.headlineLarge,
                                    textAlign = TextAlign.Center
                                )
                                if (!card.reading.isNullOrBlank()) {
                                    Text(
                                        text = card.reading,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!card.field.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(card.field) }
                                    )
                                }

                                // Answer (revealed)
                                AnimatedVisibility(
                                    visible = state.isAnswerRevealed,
                                    enter = fadeIn() + expandVertically()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(top = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                        Text(
                                            text = card.definition,
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action buttons
                        if (!state.isAnswerRevealed) {
                            Button(
                                onClick = { viewModel.revealAnswer() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("解答を表示", style = MaterialTheme.typography.titleMedium)
                            }
                        } else {
                            // SM-2 grade buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GradeButton("😵\n忘却", 1, MaterialTheme.colorScheme.error, Modifier.weight(1f)) {
                                    viewModel.gradeCard(it)
                                }
                                GradeButton("🤔\n難しい", 3, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)) {
                                    viewModel.gradeCard(it)
                                }
                                GradeButton("🙂\n正解", 4, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                                    viewModel.gradeCard(it)
                                }
                                GradeButton("⚡\n完璧", 5, MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) {
                                    viewModel.gradeCard(it)
                                }
                            }
                        }
                    }
                }

                is SrsViewModel.SrsUiState.Completed -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("✅", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "セッション完了！",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            "${state.reviewedCount} 枚のカードを復習しました",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { viewModel.loadDueCards() }) {
                                Text("もう一度")
                            }
                            Button(onClick = onBack) {
                                Text("終了")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeButton(
    label: String,
    grade: Int,
    color: Color,
    modifier: Modifier = Modifier,
    onGrade: (Int) -> Unit
) {
    Button(
        onClick = { onGrade(grade) },
        modifier = modifier.height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = color
        )
    }
}

private typealias Color = androidx.compose.ui.graphics.Color