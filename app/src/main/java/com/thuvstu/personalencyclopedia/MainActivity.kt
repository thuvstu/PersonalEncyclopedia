package com.thuvstu.personalencyclopedia

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thuvstu.personalencyclopedia.importer.WebScraper
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import com.thuvstu.personalencyclopedia.repository.ThoughtDraft
import com.thuvstu.personalencyclopedia.ui.navigation.AppNavGraph
import com.thuvstu.personalencyclopedia.ui.navigation.Routes
import com.thuvstu.personalencyclopedia.ui.theme.EncyclopediaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var webScraper: WebScraper
    @Inject lateinit var entryRepo: EntryRepository
    @Inject lateinit var incomingNavigation: IncomingNavigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EncyclopediaTheme { MainContent(incomingNavigation) } }
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        lifecycleScope.launch {
            val url = Regex("""https?://\S+""").find(text)?.value
            Toast.makeText(
                this@MainActivity,
                if (url != null) "Webページを取り込み中…" else "メモとして保存中…",
                Toast.LENGTH_SHORT
            ).show()
            val scrapedId = url?.let {
                webScraper.scrapeAndSave(it).entryId.takeIf { id -> id.isNotEmpty() }
            }
            // ★修正1: 同名メモの重複排除
            val fallbackTitle = text.take(80).ifBlank { "共有メモ" }
            val id = scrapedId
                ?: entryRepo.findByTitle(fallbackTitle)?.id
                ?: entryRepo.createThought(ThoughtDraft(title = fallbackTitle, content = text))
            // ★修正2: val再代入エラー → メソッド呼び出しに変更
            incomingNavigation.setPendingEntry(id)
        }
    }
}

@Composable
private fun MainContent(incomingNavigation: IncomingNavigation) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val pending by incomingNavigation.pendingEntryId.collectAsState()
    LaunchedEffect(pending) {
        pending?.let { id ->
            navController.navigate("entry/$id")
            // ★修正2: val再代入エラー → clear() に変更
            incomingNavigation.clear()
        }
    }

    val topLevelRoutes = listOf(
        Routes.DASHBOARD, Routes.SEARCH, Routes.SRS_REVIEW, Routes.QUIZ, Routes.STATS
    )
    val showBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("ホーム") },
                        selected = currentRoute == Routes.DASHBOARD,
                        onClick = {
                            if (currentRoute != Routes.DASHBOARD) {
                                navController.navigate(Routes.DASHBOARD) {
                                    popUpTo(Routes.DASHBOARD) { inclusive = true }
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("検索") },
                        selected = currentRoute == Routes.SEARCH,
                        onClick = {
                            if (currentRoute != Routes.SEARCH) {
                                navController.navigate(Routes.SEARCH) { popUpTo(Routes.DASHBOARD) }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.School, contentDescription = null) },
                        label = { Text("復習") },
                        selected = currentRoute == Routes.SRS_REVIEW,
                        onClick = {
                            if (currentRoute != Routes.SRS_REVIEW) {
                                navController.navigate(Routes.SRS_REVIEW) { popUpTo(Routes.DASHBOARD) }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                        label = { Text("クイズ") },
                        selected = currentRoute == Routes.QUIZ,
                        onClick = {
                            if (currentRoute != Routes.QUIZ) {
                                navController.navigate(Routes.QUIZ) { popUpTo(Routes.DASHBOARD) }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                        label = { Text("統計") },
                        selected = currentRoute == Routes.STATS,
                        onClick = {
                            if (currentRoute != Routes.STATS) {
                                navController.navigate(Routes.STATS) { popUpTo(Routes.DASHBOARD) }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            AppNavGraph(navController = navController)
        }
    }
}