package com.telemetria.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.telemetria.app.ui.dashboard.DashboardScreen
import com.telemetria.app.ui.dashboard.DashboardViewModel
import com.telemetria.app.ui.history.HistoryScreen
import com.telemetria.app.ui.history.HistoryViewModel
import com.telemetria.app.ui.history.SessionDetailScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object History : Screen("history")
    object SessionDetail : Screen("session_detail/{sessionId}") {
        fun createRoute(sessionId: String) = "session_detail/$sessionId"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    dashboardViewModel: DashboardViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onSessionFinished = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = historyViewModel,
                onBack = { navController.popBackStack() },
                onSelectSession = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                }
            )
        }

        composable(
            route = Screen.SessionDetail.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            SessionDetailScreen(
                sessionId = sessionId,
                viewModel = historyViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

