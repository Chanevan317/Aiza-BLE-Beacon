package com.aiza.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aiza.app.ui.screens.AddTagScreen
import com.aiza.app.ui.screens.FindScreen
import com.aiza.app.ui.screens.HomeScreen
import com.aiza.app.ui.screens.ResearcherScreen
import com.aiza.app.ui.screens.SettingsScreen
import com.aiza.app.ui.screens.TagDetailScreen

/** The six screens of SRS §4.1. */
object Routes {
    const val HOME = "home"
    const val ADD_TAG = "add_tag"
    const val TAG_DETAIL = "tag/{address}"
    const val FIND = "find/{address}"
    const val SETTINGS = "settings"
    const val RESEARCHER = "researcher"
    fun tagDetail(address: String) = "tag/$address"
    fun find(address: String) = "find/$address"
}

@Composable
fun AizaNavHost() {
    val nav = rememberNavController()
    val addressArg = listOf(navArgument("address") { type = NavType.StringType })
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenTag = { nav.navigate(Routes.tagDetail(it)) },
                onAddTag = { nav.navigate(Routes.ADD_TAG) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onResearcher = { nav.navigate(Routes.RESEARCHER) },
            )
        }
        composable(Routes.ADD_TAG) { AddTagScreen(onDone = { nav.popBackStack() }) }
        composable(Routes.TAG_DETAIL, addressArg) { entry ->
            val address = entry.arguments?.getString("address").orEmpty()
            TagDetailScreen(address, onFind = { nav.navigate(Routes.find(address)) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.FIND, addressArg) { entry ->
            FindScreen(entry.arguments?.getString("address").orEmpty(), onStop = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.RESEARCHER) { ResearcherScreen(onBack = { nav.popBackStack() }) }
    }
}
