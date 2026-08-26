package com.ravi.freedium.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ravi.freedium.ui.composable.CleanupLogScreen
import com.ravi.freedium.ui.composable.NotificationDetailScreen
import com.ravi.freedium.ui.screens.HomeScreen
import com.ravi.freedium.ui.screens.SettingsScreen
import com.ravi.freedium.viewmodel.NotificationViewModel

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SWEEP_LOG = "sweep-log"
    const val DETAIL = "detail/{id}"

    fun detail(id: Long) = "detail/$id"
}

/**
 * Home is the article list and nothing else. Settings sits behind the gear in the top app
 * bar - Material 3 reserves the bottom navigation bar for three to five destinations, and
 * with only two a persistent bar would cost screen height for no navigational gain.
 */
@Composable
fun NotificationNavHost(viewModel: NotificationViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onInspect = { id -> navController.navigate(Routes.detail(id)) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSweepLog = { navController.navigate(Routes.SWEEP_LOG) }
            )
        }

        composable(Routes.SWEEP_LOG) {
            TitledScreen(title = "Sweep log", onBack = { navController.popBackStack() }) { padding ->
                CleanupLogScreen(viewModel = viewModel, modifier = Modifier.padding(padding))
            }
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            TitledScreen(
                title = "Raw notification",
                onBack = { navController.popBackStack() }
            ) { padding ->
                NotificationDetailScreen(
                    viewModel = viewModel,
                    id = id,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitledScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        content = content
    )
}
