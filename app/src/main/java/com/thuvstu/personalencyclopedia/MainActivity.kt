package com.thuvstu.personalencyclopedia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thuvstu.personalencyclopedia.ui.navigation.AppNavGraph
import com.thuvstu.personalencyclopedia.ui.navigation.Routes
import com.thuvstu.personalencyclopedia.ui.theme.EncyclopediaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EncyclopediaTheme {
                MainContent()
            }
        }
    }
}

@Composable
private fun MainContent() {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val topLevelRoutes = listOf(
        Routes.DASHBOARD, Routes.SEARCH, Routes.SRS_REVIEW, Routes.QUIZ
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
                                navController.navigate(Routes.SEARCH) {
                                    popUpTo(Routes.DASHBOARD)
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
                                navController.navigate(Routes.SRS_REVIEW) {
                                    popUpTo(Routes.DASHBOARD)
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                        label = { Text("クイズ") },
                        selected = currentRoute == Routes.QUIZ,
                        onClick = {
                            if (currentRoute != Routes.QUIZ) {
                                navController.navigate(Routes.QUIZ) {
                                    popUpTo(Routes.DASHBOARD)
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