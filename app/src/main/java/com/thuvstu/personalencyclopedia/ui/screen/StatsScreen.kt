package com.thuvstu.personalencyclopedia.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thuvstu.personalencyclopedia.viewmodel.StatsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel()
) {
    val heatmap by viewModel.heatmap.collectAsState()
    val studyDayCount by viewModel.studyDayCount.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val quizCount by viewModel.quizCount.collectAsState()
    val dueCount by viewModel.dueCount.collectAsState()
    val weakPointAnalysis by viewModel.weakPointAnalysis.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("学習統計") })
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
            // Top stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("継続日数", "$streak 日", Modifier.weight(1f))
                StatCard("学習日数", "$studyDayCount 日", Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("クイズ総数", "$quizCount 問", Modifier.weight(1f))
                StatCard("復習期限", "$dueCount 枚", Modifier.weight(1f))
            }

            // Heatmap (last 12 weeks)
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📅 アクティビティ（直近12週間）", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    HeatmapGrid(heatmap)
                }
            }

            // Weak point analysis
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🎯 弱点分析", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    weakPointAnalysis?.let { analysis ->
                        Text(analysis, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = { viewModel.analyzeWeakPoints() },
                        enabled = !isAnalyzing
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (weakPointAnalysis == null) "分析する" else "再分析")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun HeatmapGrid(heatmap: List<com.thuvstu.personalencyclopedia.db.dao.DailyActivityCount>) {
    val countByDay = heatmap.associate { it.day to it.count }
    val maxCount = heatmap.maxOfOrNull { it.count } ?: 1

    val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calendar = Calendar.getInstance()
    // Start from 84 days ago (12 weeks), aligned to Sunday
    calendar.add(Calendar.DAY_OF_YEAR, -83)
    while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }

    val weeks = mutableListOf<List<Pair<String, Int>>>()
    var currentWeek = mutableListOf<Pair<String, Int>>()

    while (!calendar.time.after(Date())) {
        val dayStr = format.format(calendar.time)
        currentWeek.add(dayStr to (countByDay[dayStr] ?: 0))
        if (currentWeek.size == 7) {
            weeks.add(currentWeek)
            currentWeek = mutableListOf()
        }
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    if (currentWeek.isNotEmpty()) weeks.add(currentWeek)

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { (_, count) ->
                    val intensity = if (count == 0) 0f else (count.toFloat() / maxCount).coerceIn(0.2f, 1f)
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (count == 0) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.primary.copy(alpha = intensity)
                            )
                    )
                }
            }
        }
    }
}