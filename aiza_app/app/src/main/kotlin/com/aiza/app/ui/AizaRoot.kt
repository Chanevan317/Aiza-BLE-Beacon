package com.aiza.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.aiza.app.data.AppContainer
import com.aiza.app.ui.home.HomeScreen
import com.aiza.app.ui.researcher.ResearcherScreen
import com.aiza.app.ui.search.SearchScreen
import com.aiza.app.ui.settings.SettingsScreen
import com.aiza.app.ui.splash.AizaSplash
import com.aiza.app.ui.tag.TagScreen
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable data class TagRoute(val address: String)
@Serializable data class SearchRoute(val address: String)
@Serializable object SettingsRoute
@Serializable object ResearcherRoute

@Composable
fun AizaRoot(container: AppContainer) {
    var splashDone by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Crossfade(splashDone, animationSpec = tween(450), label = "splash") { done ->
            if (done) AizaNavHost() else AizaSplash(onFinished = { splashDone = true })
        }
    }
}

@Composable
private fun AizaNavHost() {
    val nav = rememberNavController()
    val dur = 380
    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(dur), initialOffset = { it / 4 }) + fadeIn(tween(dur)) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(dur), targetOffset = { it / 8 }) + fadeOut(tween(dur / 2)) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(dur), initialOffset = { it / 8 }) + fadeIn(tween(dur)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(dur), targetOffset = { it / 4 }) + fadeOut(tween(dur / 2)) },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenTag = { nav.navigate(TagRoute(it)) },
                onSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<TagRoute> { entry ->
            val address = entry.toRoute<TagRoute>().address
            TagScreen(
                address = address,
                onBack = { nav.popBackStack() },
                onSearch = { nav.navigate(SearchRoute(address)) },
            )
        }
        composable<SearchRoute>(
            enterTransition = { scaleIn(tween(dur), initialScale = 0.92f) + fadeIn(tween(dur)) },
            popExitTransition = { scaleOut(tween(dur), targetScale = 0.92f) + fadeOut(tween(dur)) },
        ) { entry ->
            SearchScreen(address = entry.toRoute<SearchRoute>().address, onClose = { nav.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { nav.popBackStack() }, onResearcher = { nav.navigate(ResearcherRoute) })
        }
        composable<ResearcherRoute> { ResearcherScreen(onBack = { nav.popBackStack() }) }
    }
}
