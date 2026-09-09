package com.thuvstu.personalencyclopedia.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.ui.component.StickyNoteQuickAdd
import com.thuvstu.personalencyclopedia.brain.quiz.QuizFormatSupport
import com.thuvstu.personalencyclopedia.brain.quiz.QuizFormats
import com.thuvstu.personalencyclopedia.brain.quiz.QuizPlayKind
import com.thuvstu.personalencyclopedia.viewmodel.QuizViewModel

private fun quizTypeLabel(type: String): String = when (type) {
    "essay" -> "記述式"
    "custom" -> "カスタム"
    else -> QuizFormats.label(type).ifBlank { "クイズ" }
}

private fun gradingMethodLabel(method: String): String = when (method) {
    "exact" -> "完全一致"
    "normalized" -> "正規化一致"
    "fuzzy" -> "あいまい一致"
    "semantic" -> "意味的採点"
    "rubric" -> "ルーブリック採点"
    "multi_answer" -> "複数回答一致"
    "sequence" -> "順序一致"
    "set" -> "集合一致"
    "tf" -> "正誤一致"
    else -> method
}

private val NUM_MARKS = listOf("①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧")

private const val QUIZ_CARD_ELEVATION = 2

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuizScreen(
    onBack: () -> Unit,
    onNavigateToQuizNew: () -> Unit,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentStickyNotes by viewModel.currentStickyNotes.collectAsState()   // ★wt56
    var answerInput by remember { mutableStateOf("") }
    var enumerateInput by remember { mutableStateOf("") }
    var showExitConfirm by remember { mutableStateOf(false) }

    // ★最適化R4: セッション進行中は戻る操作で破棄確認を出す
    val inSession = uiState is QuizViewModel.QuizUiState.Question ||
        uiState is QuizViewModel.QuizUiState.Answered ||
        uiState is QuizViewModel.QuizUiState.EnumerateQuestion
    val requestExit: () -> Unit = {
        if (inSession) showExitConfirm = true else onBack()
    }
    BackHandler(enabled = inSession) { showExitConfirm = true }

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
                    IconButton(onClick = requestExit) {
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
            // ★最適化R4: 大画面・タブレットでは内容幅を制限して読みやすくする
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentAlignment = Alignment.Center
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
                                    label = { Text(quizTypeLabel(state.quiz.quizType)) }
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
                        key(state.quiz.id) {
                            val unlearnedLabel =
                                if (state.mode == QuizViewModel.SessionMode.SURVIVAL) "未習(終了)" else "未習"
                            val blanks = QuizFormatSupport.blankCount(state.quiz.question)
                            val fmt = QuizFormats.of(state.quiz.quizType)
                            val matchPairs = QuizFormatSupport.parseMatchPairs(state.choices)
                            when {
                                fmt?.play == QuizPlayKind.CHOICE -> {
                                    state.choices.forEachIndexed { i, choice ->
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
                                            Text(
                                                "${NUM_MARKS.getOrElse(i) { "${i + 1}." }} $choice",
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                }
                                fmt?.play == QuizPlayKind.SORT && state.choices.isNotEmpty() -> {
                                    SortAnswerPanel(
                                        items = state.choices,
                                        unlearnedLabel = unlearnedLabel,
                                        onSubmit = { viewModel.submitAnswer(it) },
                                        onUnlearned = { viewModel.markUnlearned() }
                                    )
                                }
                                fmt?.play == QuizPlayKind.TF -> {
                                    TfAnswerPanel(
                                        unlearnedLabel = unlearnedLabel,
                                        onSubmit = { viewModel.submitAnswer(it) },
                                        onUnlearned = { viewModel.markUnlearned() }
                                    )
                                }
                                fmt?.play == QuizPlayKind.MULTI && state.choices.isNotEmpty() -> {
                                    MultiAnswerPanel(
                                        items = state.choices,
                                        unlearnedLabel = unlearnedLabel,
                                        onSubmit = { viewModel.submitAnswer(it) },
                                        onUnlearned = { viewModel.markUnlearned() }
                                    )
                                }
                                fmt?.play == QuizPlayKind.MATCH && matchPairs.size >= 2 -> {
                                    MatchAnswerPanel(
                                        pairs = matchPairs,
                                        unlearnedLabel = unlearnedLabel,
                                        onSubmit = { viewModel.submitAnswer(it) },
                                        onUnlearned = { viewModel.markUnlearned() }
                                    )
                                }
                                state.quiz.quizType == "cloze" ||
                                    (state.quiz.quizType == "fill_blank" && blanks > 0) -> {
                                    ClozeAnswerPanel(
                                        blankCount = blanks.coerceAtLeast(1),
                                        unlearnedLabel = unlearnedLabel,
                                        onSubmit = { viewModel.submitAnswer(it) },
                                        onUnlearned = { viewModel.markUnlearned() }
                                    )
                                }
                                else -> {
                                    OutlinedTextField(
                                        value = answerInput,
                                        onValueChange = { answerInput = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("解答を入力") },
                                        minLines = 2,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    WrittenSubmitRow(
                                        enabled = answerInput.isNotBlank(),
                                        unlearnedLabel = unlearnedLabel,
                                        onSubmit = {
                                            viewModel.submitAnswer(answerInput)
                                            answerInput = ""
                                        },
                                        onUnlearned = {
                                            viewModel.markUnlearned()
                                            answerInput = ""
                                        }
                                    )
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
                        // 進捗（最適化R4: 答え合わせ画面にも進捗を表示）
                        Text(
                            "問 ${state.questionNumber} / ${state.totalQuestions}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

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
                            "スコア: ${String.format("%.1f", state.score)}（${gradingMethodLabel(state.gradingMethod)}）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // あなたの回答（最適化R4）
                        if (state.userAnswer.isNotBlank() && state.userAnswer != "__UNLEARNED__") {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("あなたの回答", style = MaterialTheme.typography.labelLarge)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        formatSequenceDisplay(state.quiz.quizType, state.userAnswer),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // ★最適化R4: MCQは全選択肢に正解・あなたの回答を強調表示
                        if (state.quiz.quizType == "mcq" && state.choices.isNotEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("選択肢", style = MaterialTheme.typography.labelLarge)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    state.choices.forEach { c ->
                                        val isCorrectChoice = c == state.quiz.answer
                                        val isUserChoice = c == state.userAnswer && state.userAnswer != "__UNLEARNED__"
                                        Surface(
                                            color = when {
                                                isCorrectChoice -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                isUserChoice -> MaterialTheme.colorScheme.errorContainer
                                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    c,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isCorrectChoice) {
                                                    Text(
                                                        "✓ 正解",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else if (isUserChoice) {
                                                    Text(
                                                        "あなたの回答",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

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
                                    formatSequenceDisplay(state.quiz.quizType, state.quiz.answer),
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

                        // ★wt56: 回答直後の「なぜ間違えた/次はこう覚える」を出典カードへ付箋
                        state.quiz.sourceEntryId?.let { srcId ->
                            Spacer(modifier = Modifier.height(12.dp))
                            StickyNoteQuickAdd(
                                onAdd = { text, color -> viewModel.sticky.add(srcId, text, color, contextId = state.quiz.id) },
                                existingCount = currentStickyNotes.size,
                                label = if (state.isCorrect == false) "💭 間違えた理由を付箋に" else "💭 思ったことを付箋に",
                                modifier = Modifier.fillMaxWidth()
                            )
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

    // ★最適化R4: セッション進行中の破棄確認
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("セッションを終了しますか？") },
            text = { Text("進行中のセッションは破棄されます。") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    onBack()
                }) { Text("終了") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("続ける") }
            }
        )
    }
}


private fun formatSequenceDisplay(quizType: String, raw: String): String {
    val fmt = QuizFormats.of(quizType)
    return when {
        fmt?.play == QuizPlayKind.MATCH -> {
            val parts = QuizFormatSupport.splitMatchSequence(raw)
            if (parts.size <= 1) raw.replace("=", " ⇔ ")
            else parts.joinToString("、") { it.replace("=", " ⇔ ") }
        }
        fmt?.setGrade == true -> {
            val parts = QuizFormatSupport.splitSequence(raw)
            if (parts.size <= 1) raw else parts.joinToString("、")
        }
        fmt?.sequenceGrade == true -> {
            val parts = QuizFormatSupport.splitSequence(raw)
            if (parts.size <= 1) raw else parts.joinToString(" → ")
        }
        else -> raw
    }
}

@Composable
private fun WrittenSubmitRow(
    enabled: Boolean,
    unlearnedLabel: String,
    onSubmit: () -> Unit,
    onUnlearned: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSubmit,
            modifier = Modifier.weight(1f),
            enabled = enabled
        ) { Text("回答") }
        OutlinedButton(
            onClick = onUnlearned,
            modifier = Modifier.weight(1f)
        ) { Text(unlearnedLabel) }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SortAnswerPanel(
    items: List<String>,
    unlearnedLabel: String,
    onSubmit: (String) -> Unit,
    onUnlearned: () -> Unit
) {
    var picked by remember { mutableStateOf(listOf<Int>()) }
    val remaining = items.indices.filter { it !in picked }
    Text(
        "タップして並べる（並べた語をタップで戻す）",
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(Modifier.height(8.dp))
    if (remaining.isNotEmpty()) {
        Text("残り", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            remaining.forEach { idx ->
                FilterChip(
                    selected = false,
                    onClick = { picked = picked + idx },
                    label = { Text(items[idx]) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (picked.isNotEmpty()) {
        Text("現在の順", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            picked.forEachIndexed { order, idx ->
                FilterChip(
                    selected = true,
                    onClick = { picked = picked.filterNot { it == idx } },
                    label = { Text("${order + 1}. ${items[idx]}") }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    WrittenSubmitRow(
        enabled = picked.size == items.size && items.isNotEmpty(),
        unlearnedLabel = unlearnedLabel,
        onSubmit = { onSubmit(QuizFormatSupport.joinSequence(picked.map { items[it] })) },
        onUnlearned = onUnlearned
    )
}

@Composable
private fun ClozeAnswerPanel(
    blankCount: Int,
    unlearnedLabel: String,
    onSubmit: (String) -> Unit,
    onUnlearned: () -> Unit
) {
    val n = blankCount.coerceAtLeast(1)
    var blanks by remember { mutableStateOf(List(n) { "" }) }
    Text(
        if (n == 1) "空欄を埋めよ" else "空欄を出現順に埋めよ",
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(Modifier.height(8.dp))
    blanks.forEachIndexed { i, value ->
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                blanks = blanks.toMutableList().also { it[i] = next }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            label = { Text(if (n == 1) "空欄" else "空欄 ${i + 1}") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = if (i == n - 1) ImeAction.Done else ImeAction.Next)
        )
    }
    Spacer(Modifier.height(12.dp))
    WrittenSubmitRow(
        enabled = blanks.all { it.isNotBlank() },
        unlearnedLabel = unlearnedLabel,
        onSubmit = { onSubmit(QuizFormatSupport.joinSequence(blanks.map { it.trim() })) },
        onUnlearned = onUnlearned
    )
}


@Composable
private fun TfAnswerPanel(
    unlearnedLabel: String,
    onSubmit: (String) -> Unit,
    onUnlearned: () -> Unit
) {
    Text("正しいか誤りか選べ", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onSubmit("正しい") },
            modifier = Modifier.weight(1f).height(52.dp)
        ) { Text("正しい") }
        OutlinedButton(
            onClick = { onSubmit("誤り") },
            modifier = Modifier.weight(1f).height(52.dp)
        ) { Text("誤り") }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onUnlearned, modifier = Modifier.fillMaxWidth()) {
        Text(unlearnedLabel)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MultiAnswerPanel(
    items: List<String>,
    unlearnedLabel: String,
    onSubmit: (String) -> Unit,
    onUnlearned: () -> Unit
) {
    var selected by remember { mutableStateOf(setOf<Int>()) }
    Text("当てはまるものをすべて選べ", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, c ->
            FilterChip(
                selected = i in selected,
                onClick = {
                    selected = if (i in selected) selected - i else selected + i
                },
                label = { Text(c) }
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    WrittenSubmitRow(
        enabled = selected.isNotEmpty(),
        unlearnedLabel = unlearnedLabel,
        onSubmit = { onSubmit(QuizFormatSupport.joinSequence(selected.sorted().map { items[it] })) },
        onUnlearned = onUnlearned
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MatchAnswerPanel(
    pairs: List<Pair<String, String>>,
    unlearnedLabel: String,
    onSubmit: (String) -> Unit,
    onUnlearned: () -> Unit
) {
    var leftRemain by remember { mutableStateOf(pairs.map { it.first }.shuffled()) }
    var rightRemain by remember { mutableStateOf(pairs.map { it.second }.shuffled()) }
    var matched by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var selL by remember { mutableStateOf<String?>(null) }
    var selR by remember { mutableStateOf<String?>(null) }

    fun pairIfReady() {
        val l = selL
        val r = selR
        if (l != null && r != null) {
            matched = matched + (l to r)
            leftRemain = leftRemain.toMutableList().also { it.remove(l) }
            rightRemain = rightRemain.toMutableList().also { it.remove(r) }
            selL = null
            selR = null
        }
    }

    Text("左右をタップして対応づけよ（組をタップで戻す）", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("左", style = MaterialTheme.typography.labelSmall)
            leftRemain.forEach { item ->
                FilterChip(
                    selected = selL == item,
                    onClick = { selL = if (selL == item) null else item; pairIfReady() },
                    label = { Text(item) }
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("右", style = MaterialTheme.typography.labelSmall)
            rightRemain.forEach { item ->
                FilterChip(
                    selected = selR == item,
                    onClick = { selR = if (selR == item) null else item; pairIfReady() },
                    label = { Text(item) }
                )
            }
        }
    }
    if (matched.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("組", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            matched.forEach { (l, r) ->
                FilterChip(
                    selected = true,
                    onClick = {
                        matched = matched.filterNot { it.first == l && it.second == r }
                        leftRemain = leftRemain + l
                        rightRemain = rightRemain + r
                    },
                    label = { Text("$l ⇔ $r") }
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    WrittenSubmitRow(
        enabled = leftRemain.isEmpty() && rightRemain.isEmpty() && matched.isNotEmpty(),
        unlearnedLabel = unlearnedLabel,
        onSubmit = { onSubmit(QuizFormatSupport.joinMatchAnswer(matched)) },
        onUnlearned = onUnlearned
    )
}
