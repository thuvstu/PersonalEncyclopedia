package com.thuvstu.personalencyclopedia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thuvstu.personalencyclopedia.importer.ImportPipeline
import com.thuvstu.personalencyclopedia.importer.WebScraper
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import com.thuvstu.personalencyclopedia.repository.ThoughtDraft
import com.thuvstu.personalencyclopedia.ui.navigation.AppNavGraph
import com.thuvstu.personalencyclopedia.ui.navigation.Routes
import com.thuvstu.personalencyclopedia.ui.theme.EncyclopediaTheme
import com.thuvstu.personalencyclopedia.util.timed
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var webScraper: WebScraper
    @Inject lateinit var entryRepo: EntryRepository
    @Inject lateinit var incomingNavigation: IncomingNavigation
    @Inject lateinit var importPipeline: ImportPipeline

    companion object {
        const val EXTRA_SHORTCUT = "shortcut"
        const val SHORTCUT_NEW_MEMO = "new_memo"
        const val SHORTCUT_SEARCH = "search"
        const val SHORTCUT_REVIEW = "review"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EncyclopediaTheme { MainContent(incomingNavigation) } }
        if (savedInstanceState == null) handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.getStringExtra(EXTRA_SHORTCUT)) {
            SHORTCUT_NEW_MEMO -> { incomingNavigation.setPendingRoute(Routes.THOUGHT_NEW); return }
            SHORTCUT_SEARCH -> { incomingNavigation.setPendingRoute(Routes.SEARCH); return }
            SHORTCUT_REVIEW -> { incomingNavigation.setPendingRoute(Routes.SRS_REVIEW); return }
        }
        when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()
                if (text.isNullOrBlank()) return
                lifecycleScope.launch { saveSharedText(text) }
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                lifecycleScope.launch { handleShare(intent) }
            }
        }
    }

    private suspend fun handleShare(intent: Intent) {
        val type = intent.type ?: ""
        val uris = streamUris(intent)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val saved = mutableListOf<String>()
        if (uris.isNotEmpty()) {
            for (uri in uris) {
                val mime = contentResolver.getType(uri) ?: type
                val id = when {
                    mime.startsWith("image/") -> importSharedImage(uri)
                    mime == "application/pdf" || mime.contains("pdf") -> importSharedPdf(uri)
                    else -> null
                }
                if (id != null) saved += id
            }
        }
        if (saved.isEmpty() && !text.isNullOrBlank()) {
            saveSharedText(text)?.let { saved += it }
            incomingNavigation.setNotice(
                if (saved.isEmpty()) "保存できませんでした" else "保存しました",
                saved.lastOrNull()
            )
            return
        }
        val message = when {
            saved.isEmpty() -> "保存できませんでした"
            saved.size == 1 -> "保存しました"
            else -> "${saved.size}件保存しました"
        }
        incomingNavigation.setNotice(message, saved.lastOrNull())
    }

    /** URL ならスクレイプ、そうでなければメモ。成功時は entryId。 */
    private suspend fun saveSharedText(text: String): String? {
        val url = Regex("""https?://\S+""").find(text)?.value
        val scrapedId = url?.let {
            webScraper.scrapeAndSave(it).entryId.takeIf { id -> id.isNotEmpty() }
        }
        val fallbackTitle = text.take(80).ifBlank { "共有メモ" }
        return scrapedId
            ?: entryRepo.findByTitle(fallbackTitle)?.id
            ?: entryRepo.createThought(ThoughtDraft(title = fallbackTitle, content = text))
    }

    private suspend fun importSharedImage(uri: Uri): String? {
        return try {
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            val name = queryDisplayName(uri)
                ?.substringBeforeLast('.')
                ?.ifBlank { null }
                ?: "共有画像"
            val dirId = UUID.randomUUID().toString()
            val dir = File(filesDir, "blobs/media/$dirId").apply { mkdirs() }
            val file = File(dir, "shared.$ext")
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: run { dir.deleteRecursively(); return null }
            entryRepo.createMedia(
                title = name.take(80),
                content = null,
                mediaType = "image",
                blobPath = file.absolutePath,
                mimeType = mime
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun importSharedPdf(uri: Uri): String? {
        val name = queryDisplayName(uri) ?: "document.pdf"
        val result = importPipeline.importDocumentFile(uri, name)
        if (result.successCount == 0 && result.skipCount == 0) return null
        val title = name.substringBeforeLast('.').ifBlank { name }
        return entryRepo.findByTitle(title)?.id
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')
    } catch (_: Exception) {
        uri.lastPathSegment
    }

    @Suppress("DEPRECATION")
    private fun streamUris(intent: Intent): List<Uri> {
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            ?.filterNotNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { return listOf(it) }
        val cd = intent.clipData
        if (cd != null && cd.itemCount > 0) {
            return (0 until cd.itemCount).mapNotNull { cd.getItemAt(it).uri }
        }
        return emptyList()
    }
}

@Composable
private fun MainContent(incomingNavigation: IncomingNavigation) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    val pending by incomingNavigation.pendingEntryId.collectAsState()
    LaunchedEffect(pending) {
        pending?.let { id ->
            timed("Nav", "entry:$id") { navController.navigate("entry/$id") }
            incomingNavigation.clear()
        }
    }

    val pendingRoute by incomingNavigation.pendingRoute.collectAsState()
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            timed("Nav", "shortcut:$route") { navController.navigate(route) }
            incomingNavigation.clearRoute()
        }
    }

    val notice by incomingNavigation.pendingNotice.collectAsState()
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = n.message,
            actionLabel = if (n.entryId != null) "開く" else null,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            n.entryId?.let { incomingNavigation.setPendingEntry(it) }
        }
        incomingNavigation.clearNotice()
    }

    val topLevelRoutes = listOf(
        Routes.DASHBOARD, Routes.SEARCH, Routes.SRS_REVIEW, Routes.QUIZ, Routes.STATS
    )
    val showBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("ホーム") },
                        selected = currentRoute == Routes.DASHBOARD,
                        onClick = {
                            if (currentRoute != Routes.DASHBOARD) {
                                timed("Nav", "tab:dashboard") {
                                    navController.navigate(Routes.DASHBOARD) {
                                        popUpTo(Routes.DASHBOARD) { inclusive = true; saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
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
                                timed("Nav", "tab:search") {
                                    navController.navigate(Routes.SEARCH) {
                                        popUpTo(Routes.DASHBOARD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.School, contentDescription = null) },
                        label = { Text("復習") },
                        selected = currentRoute == Routes.SRS_REVIEW,
                        onClick = {
                            if (currentRoute != Routes.SRS_REVIEW) {
                                timed("Nav", "tab:srs_review") {
                                    navController.navigate(Routes.SRS_REVIEW) {
                                        popUpTo(Routes.DASHBOARD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                        label = { Text("クイズ") },
                        selected = currentRoute == Routes.QUIZ,
                        onClick = {
                            if (currentRoute != Routes.QUIZ) {
                                timed("Nav", "tab:quiz") {
                                    navController.navigate(Routes.QUIZ) {
                                        popUpTo(Routes.DASHBOARD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                        label = { Text("統計") },
                        selected = currentRoute == Routes.STATS,
                        onClick = {
                            if (currentRoute != Routes.STATS) {
                                timed("Nav", "tab:stats") {
                                    navController.navigate(Routes.STATS) {
                                        popUpTo(Routes.DASHBOARD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
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
