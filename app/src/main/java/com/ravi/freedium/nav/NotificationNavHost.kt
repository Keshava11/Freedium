package com.ravi.freedium.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ravi.freedium.ui.composable.CleanupLogScreen
import com.ravi.freedium.ui.composable.NotificationDetailScreen
import com.ravi.freedium.ui.composable.NotificationListScreen
import com.ravi.freedium.viewmodel.NotificationViewModel


/**
 * Only two in-app destinations remain. Articles are never rendered here - they go to a
 * Chrome Custom Tab, so there is no reader route to navigate to.
 */
@Composable
fun NotificationNavHost(viewModel: NotificationViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "list") {
        // ListScreen
        composable("list") {
            TitledScreen(title = "Freedium") { innerPadding ->
                NotificationListScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = { id -> navController.navigate("detail/$id") },
                    onNavigateToCleanupLog = { navController.navigate("cleanup") },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        // Weekly retention sweep audit trail
        composable("cleanup") {
            TitledScreen(
                title = "Sweep log",
                onBack = { navController.popBackStack() }
            ) { innerPadding ->
                CleanupLogScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        // Raw notification dump
        composable(
            route = "detail/{id}",
            arguments = listOf(navArgument(name = "id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            TitledScreen(
                title = "Raw notification",
                onBack = { navController.popBackStack() }
            ) { innerPadding ->
                NotificationDetailScreen(
                    viewModel = viewModel,
                    id = id,
                    modifier = Modifier.padding(innerPadding)
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
            CenterAlignedTopAppBar(
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
                }
            )
        },
        content = content
    )
}
