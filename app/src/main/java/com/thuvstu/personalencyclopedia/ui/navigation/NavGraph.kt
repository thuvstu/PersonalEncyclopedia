package com.thuvstu.personalencyclopedia.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.thuvstu.personalencyclopedia.ui.screen.*
import com.thuvstu.personalencyclopedia.util.timed

object Routes {
    const val DASHBOARD = "dashboard"
    const val SEARCH = "search"
    const val SRS_REVIEW = "srs_review"
    const val QUIZ = "quiz"
    const val STATS = "stats"
    const val CONNECTION_CANDIDATES = "connection_candidates"
    const val CONNECTIONS = "connections"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val DB_MANAGEMENT = "db_management"
    const val THOUGHT_NEW = "thought/new"
    const val THOUGHT_EDIT = "thought/edit/{entryId}"
    const val DEFINITION_NEW = "definition/new"
    const val DEFINITION_EDIT = "definition/edit/{entryId}"
    const val ENTRY_DETAIL = "entry/{entryId}"
    const val ENTRY_NEW = "entry/new/{type}"
    const val ENTRY_EDIT_GENERIC = "entry/edit/{type}/{entryId}"
    const val QUIZ_NEW = "quiz/new"
    const val QUIZ_EDIT = "quiz/edit/{quizId}"
    // ★v12.0 追加
    const val QUIZ_LIST = "quiz_list"
    const val WHITEBOARD_LIST = "whiteboard_list"
    const val WHITEBOARD_BOARD = "whiteboard/{boardId}"
    const val WIKI_LIST = "wiki_list"
    const val WIKI_ARTICLE = "wiki/{articleId}"
    const val WIKI_NEW = "wiki_new"
    // ★v15.0 §11.11 / §11.12
    const val TODO = "todo"
    const val SQL_EXPLORER = "sql_explorer"
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        // ★v12.0: 遷移アニメーション高速化（のっそり感対策）
        enterTransition = { fadeIn(animationSpec = tween(120)) },
        exitTransition = { fadeOut(animationSpec = tween(90)) },
        popEnterTransition = { fadeIn(animationSpec = tween(120)) },
        popExitTransition = { fadeOut(animationSpec = tween(90)) }
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToEntry = { id -> timed("Nav", "entry:$id") { navController.navigate("entry/$id") } },
                onNavigateToNewThought = { navController.navigate(Routes.THOUGHT_NEW) },
                onNavigateToNewDefinition = { navController.navigate(Routes.DEFINITION_NEW) },
                onNavigateToNewEntry = { type -> navController.navigate("entry/new/$type") },
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToSrs = { navController.navigate(Routes.SRS_REVIEW) },
                onNavigateToQuiz = { navController.navigate(Routes.QUIZ) },
                onNavigateToQuizNew = { navController.navigate(Routes.QUIZ_NEW) },
                onNavigateToImport = { navController.navigate(Routes.IMPORT) },
                onNavigateToConnectionCandidates = { navController.navigate(Routes.CONNECTION_CANDIDATES) },
                onNavigateToConnections = { navController.navigate(Routes.CONNECTIONS) },
                // ★v12.0 追加
                onNavigateToQuizList = { navController.navigate(Routes.QUIZ_LIST) },
                onNavigateToWhiteboard = { navController.navigate(Routes.WHITEBOARD_LIST) },
                onNavigateToWiki = { navController.navigate(Routes.WIKI_LIST) },
                // ★v15.0 §11.11
                onNavigateToTodo = { navController.navigate(Routes.TODO) }
            )
        }
        // ★v15.0 §11.11: タスク（ToDo）
        composable(Routes.TODO) {
            ToDoScreen(onBack = { navController.popBackStack() })
        }
        // ★v15.0 §11.12: SQL Explorer（読み取り専用）
        composable(Routes.SQL_EXPLORER) {
            SqlExplorerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onNavigateToEntry = { id -> timed("Nav", "entry:$id") { navController.navigate("entry/$id") } }
            )
        }
        composable(Routes.SRS_REVIEW) {
            SrsReviewScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.QUIZ) {
            QuizScreen(
                onBack = { navController.popBackStack() },
                onNavigateToQuizNew = { navController.navigate(Routes.QUIZ_NEW) }
            )
        }
        // ★v12.0: クイズ一覧
        composable(Routes.QUIZ_LIST) {
            QuizListScreen(
                onBack = { navController.popBackStack() },
                onEditQuiz = { id -> navController.navigate("quiz/edit/$id") },
                onNewQuiz = { navController.navigate(Routes.QUIZ_NEW) }
            )
        }
        composable(Routes.QUIZ_NEW) {
            QuizEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.QUIZ_EDIT,
            arguments = listOf(navArgument("quizId") { type = NavType.StringType })
        ) {
            QuizEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable(Routes.STATS) { StatsScreen() }
        composable(Routes.CONNECTION_CANDIDATES) {
            ConnectionCandidatesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CONNECTIONS) {
            ConnectionsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToEntry = { id -> timed("Nav", "entry:$id") { navController.navigate("entry/$id") } }
            )
        }
        // ★v12.0: ホワイトボード（Heptabase型）
        composable(Routes.WHITEBOARD_LIST) {
            WhiteboardListScreen(
                onBack = { navController.popBackStack() },
                onOpenBoard = { boardId -> navController.navigate("whiteboard/$boardId") }
            )
        }
        composable(
            route = Routes.WHITEBOARD_BOARD,
            arguments = listOf(navArgument("boardId") { type = NavType.StringType })
        ) {
            WhiteboardBoardScreen(
                onBack = { navController.popBackStack() },
                onNavigateToEntry = { id -> timed("Nav", "entry:$id") { navController.navigate("entry/$id") } }
            )
        }
        // ★v12.0: Wikipediaビルダー
        composable(Routes.WIKI_LIST) {
            WikiListScreen(
                onBack = { navController.popBackStack() },
                onOpenArticle = { id -> navController.navigate("wiki/$id") },
                onNewArticle = { navController.navigate(Routes.WIKI_NEW) }
            )
        }
        composable(
            route = Routes.WIKI_ARTICLE,
            arguments = listOf(navArgument("articleId") { type = NavType.StringType })
        ) {
            WikiArticleScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> timed("Nav", "wiki:$id") { navController.navigate("wiki/$id") } },
                onNavigateToEntry = { id -> timed("Nav", "entry:$id") { navController.navigate("entry/$id") } } // ★追加
            )
        }
        composable(Routes.WIKI_NEW) {
            WikiEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate("wiki/$id")
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDbManagement = { navController.navigate(Routes.DB_MANAGEMENT) }
            )
        }
        composable(Routes.IMPORT) {
            ImportScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DB_MANAGEMENT) {
            DatabaseManagementScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSqlExplorer = { navController.navigate(Routes.SQL_EXPLORER) }
            )
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
            route = Routes.ENTRY_NEW,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) {
            EntryEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate("entry/$id")
                }
            )
        }
        composable(
            route = Routes.ENTRY_EDIT_GENERIC,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("entryId") { type = NavType.StringType }
            )
        ) {
            EntryEditScreen(
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
                    timed("Nav", "edit:$type/$entryId") {
                        when (type) {
                            "thought" -> navController.navigate("thought/edit/$entryId")
                            "definition" -> navController.navigate("definition/edit/$entryId")
                            else -> navController.navigate("entry/edit/$type/$entryId")
                        }
                    }
                },
                onNavigateToEntry = { id -> timed("Nav", "entry:$id") { navController.navigate("entry/$id") } },
                onNavigateToWiki = { id -> timed("Nav", "wiki:$id") { navController.navigate("wiki/$id") } }
            )
        }
    }
}