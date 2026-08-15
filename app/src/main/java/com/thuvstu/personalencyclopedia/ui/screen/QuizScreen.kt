package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.viewmodel.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    onBack: () -> Unit,
    onNavigateToQuizNew: () -> Unit,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var answerInput by remember { mutableStateOf("") }
    var enumerateInput by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToQuizNew) {
                Icon(Icons.Default.Add, contentDescription = "クイズを作成")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("クイズ演習") },
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
                is QuizViewModel.QuizUiState.SelectMode -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎯", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("演習モードを選択", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.startSession() },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) { Text("📖 通常演習") }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.startSurvivalSession() },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) { Text("💀 サバイバル（1問ミスで終了）") }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.startEnumerateChallenge() },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) { Text("⏱ プレッシャーテスト（全列挙）") }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "サバイバル: 連続正解数がスコア。\nプレッシャー: 制限時間内に同じ分野の用語を列挙",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is QuizViewModel.QuizUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is QuizViewModel.QuizUiState.Empty -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📝", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("問題がありません", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "単語帳から自動生成しますか？",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.generateQuizzes() }) {
                            Text("問題を生成する")
                        }
                    }
                }

                is QuizViewModel.QuizUiState.Question -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Progress
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "問 ${state.questionNumber} / ${state.totalQuestions}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (state.mode == QuizViewModel.SessionMode.SURVIVAL) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("💀 サバイバル 連続${state.questionNumber - 1}問正解") }
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = {
                                state.questionNumber.toFloat() / state.totalQuestions
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Question
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                // Quiz type badge
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            when (state.quiz.quizType) {
                                                "qa" -> "記述式"
                                                "mcq" -> "選択式"
                                                "fill_blank" -> "穴埋め"
                                                else -> state.quiz.quizType
                                            }
                                        )
                                    }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = state.quiz.question,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Hints
                        if (state.hints.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.revealHint() }) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = "ヒント")
                                }
                                Text(
                                    "ヒント (${state.hintsRevealed}/${state.hints.size})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            state.hints.take(state.hintsRevealed).forEach { hint ->
                                Text(
                                    text = "💡 $hint",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Answer input
                        when (state.quiz.quizType) {
                            "mcq" -> {
                                state.choices.forEach { choice ->
                                    OutlinedButton(
                                        onClick = {
                                            answerInput = choice
                                            viewModel.submitAnswer(choice)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(choice, modifier = Modifier.padding(8.dp))
                                    }
                                }
                            }
                            else -> {
                                OutlinedTextField(
                                    value = answerInput,
                                    onValueChange = { answerInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("解答を入力") },
                                    minLines = 2
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.submitAnswer(answerInput)
                                            answerInput = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = answerInput.isNotBlank()
                                    ) {
                                        Text("回答")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.markUnlearned()
                                            answerInput = ""
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("未習")
                                    }
                                }
                            }
                        }
                    }
                }

                is QuizViewModel.QuizUiState.Answered -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Result icon
                        val (icon, color, label) = when (state.isCorrect) {
                            true -> Triple("⭕", MaterialTheme.colorScheme.primary, "正解！")
                            false -> Triple("❌", MaterialTheme.colorScheme.error, "不正解")
                            null -> Triple("⏭️", MaterialTheme.colorScheme.onSurfaceVariant, "未習として記録")
                        }
                        Text(icon, style = MaterialTheme.typography.displayLarge)
                        Text(
                            label,
                            style = MaterialTheme.typography.headlineMedium,
                            color = color
                        )
                        Text(
                            "スコア: ${String.format("%.1f", state.score)} (${state.gradingMethod})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Correct answer
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("正解", style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    state.quiz.answer,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        // ★新採点システム(試作): rubric採点の根拠
                        if (!state.rubricRationale.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("採点の根拠", style = MaterialTheme.typography.labelLarge)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        state.rubricRationale,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        // Explanation
                        if (!state.quiz.explanation.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("解説", style = MaterialTheme.typography.labelLarge)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        state.quiz.explanation,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                viewModel.nextQuestion()
                                answerInput = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text("次の問題へ")
                        }
                    }
                }

                // §8.7.2 プレッシャーテスト
                is QuizViewModel.QuizUiState.EnumerateQuestion -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "⏱ プレッシャーテスト",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f)
                            )
                            val seconds = state.timeLeftMs / 1000
                            Surface(
                                color = if (seconds <= 10) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Timer, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "${seconds}秒",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "「${state.fieldLabel}」に分類される用語をできるだけ多く挙げてください",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${state.matched.size} / ${state.correctSet.size} 見つけました",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.matched.size.toFloat() / state.correctSet.size },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = enumerateInput,
                            onValueChange = { enumerateInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("用語を1つ入力して追加") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.submitEnumerateAnswer(enumerateInput)
                                    enumerateInput = ""
                                },
                                enabled = enumerateInput.isNotBlank()
                            ) { Text("追加") }
                            OutlinedButton(
                                onClick = { viewModel.startEnumerateChallenge() }
                            ) { Text("やり直す") }
                        }

                        if (state.matched.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("見つけた用語:", style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            state.matched.forEach { term ->
                                Text("✅ $term", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                is QuizViewModel.QuizUiState.EnumerateComplete -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("⏱", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("時間切れ！", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${state.matchedCount} / ${state.totalCount} 個を列挙しました",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (state.missed.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("見つけられなかった用語:", style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            state.missed.forEach { term ->
                                Text("・$term", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { viewModel.startEnumerateChallenge() }) {
                                Text("もう一度")
                            }
                            Button(onClick = {
                                viewModel.startSession()
                                answerInput = ""
                            }) { Text("通常演習へ") }
                        }
                    }
                }

                is QuizViewModel.QuizUiState.SessionComplete -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (state.survivalStreak != null) "💀" else "🏆",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (state.survivalStreak != null) "サバイバル終了！" else "セッション完了！",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (state.survivalStreak != null) {
                            Text(
                                "連続正解: ${state.survivalStreak} 問",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                if (state.survivalStreak >= 5) "🔥 好調です！自己ベストを更新しましょう"
                                else "次はもっと伸ばしましょう！",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "正解: ${state.correctCount} / ${state.totalAnswered}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "合計スコア: ${String.format("%.1f", state.totalScore)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = {
                                if (state.survivalStreak != null) viewModel.startSurvivalSession()
                                else viewModel.startSession()
                            }) {
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