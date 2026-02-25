package com.ravi.freedium.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ravi.freedium.ui.composable.NotificationListScreen
import com.ravi.freedium.ui.composable.WebViewScreen
import com.ravi.freedium.viewmodel.NotificationViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


@Composable
fun NotificationNavHost(viewModel: NotificationViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "list") {
        // ListScreen
        composable("list") {
            NotificationListScreen(
                viewModel = viewModel, onNavigateToWeb = { url ->
                    // encode the URL to safely pass it as an argument
                    val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                    navController.navigate("webview/$encodedUrl")
                })
        }

        // WebViewScreen (with url param)
        composable(
            route = "webview/{url}",
            arguments = listOf(navArgument(name = "url") { type = NavType.StringType })
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            WebViewScreen(url = url)
        }
    }
}


