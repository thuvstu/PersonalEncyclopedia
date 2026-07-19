package com.thuvstu.personalencyclopedia.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.thuvstu.personalencyclopedia.ui.screen.*

object Routes {
    const val DASHBOARD = "dashboard"
    const val SEARCH = "search"
    const val SRS_REVIEW = "srs_review"
    const val QUIZ = "quiz"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val THOUGHT_NEW = "thought/new"
    const val THOUGHT_EDIT = "thought/edit/{entryId}"
    const val DEFINITION_NEW = "definition/new"
    const val DEFINITION_EDIT = "definition/edit/{entryId}"
    const val ENTRY_DETAIL = "entry/{entryId}"
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToEntry = { id -> navController.navigate("entry/$id") },
                onNavigateToNewThought = { navController.navigate(Routes.THOUGHT_NEW) },
                onNavigateToNewDefinition = { navController.navigate(Routes.DEFINITION_NEW) },
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToSrs = { navController.navigate(Routes.SRS_REVIEW) },
                onNavigateToQuiz = { navController.navigate(Routes.QUIZ) },
                onNavigateToImport = { navController.navigate(Routes.IMPORT) }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onNavigateToEntry = { id -> navController.navigate("entry/$id") }
            )
        }

        composable(Routes.SRS_REVIEW) {
            SrsReviewScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.QUIZ) {
            QuizScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.IMPORT) {
            ImportScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.THOUGHT_NEW) {
            ThoughtEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate("entry/$id")
                }
            )
        }

        composable(
            route = Routes.THOUGHT_EDIT,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) {
            ThoughtEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(Routes.DEFINITION_NEW) {
            DefinitionEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate("entry/$id")
                }
            )
        }

        composable(
            route = Routes.DEFINITION_EDIT,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) {
            DefinitionEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ENTRY_DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) {
            EntryDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { type, entryId ->
                    when (type) {
                        "thought" -> navController.navigate("thought/edit/$entryId")
                        "definition" -> navController.navigate("definition/edit/$entryId")
                    }
                }
            )
        }
    }
}