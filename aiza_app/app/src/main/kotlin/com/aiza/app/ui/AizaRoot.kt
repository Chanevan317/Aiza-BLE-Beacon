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
import androidx.compose.runtime.LaunchedEffect
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
import com.aiza.app.ui.researcher.ConsentScreen
import com.aiza.app.ui.researcher.MotionTestScreen
import com.aiza.app.ui.researcher.NewTrialScreen
import com.aiza.app.ui.researcher.ResearcherScreen
import com.aiza.app.ui.researcher.TrialDetailScreen
import com.aiza.app.ui.search.SearchScreen
import com.aiza.app.ui.settings.SettingsScreen
import com.aiza.app.ui.splash.AizaSplash
import com.aiza.app.ui.tag.TagScreen
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiza.app.ui.onboarding.OnboardingScreen
import com.aiza.app.ui.permissions.PermissionsScreen
import com.aiza.app.ui.ring.PhoneRingScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.remember
import com.aiza.app.ring.PhoneRing

@Serializable object HomeRoute
@Serializable data class TagRoute(val address: String)
@Serializable data class SearchRoute(val address: String)
@Serializable object SettingsRoute
@Serializable object ResearcherRoute
@Serializable object PermissionsRoute
@Serializable object NewTrialRoute
@Serializable object MotionTestRoute
@Serializable object ConsentRoute
@Serializable data class TrialRoute(val id: String)

@Composable
fun AizaRoot(container: AppContainer) {
    var splashDone by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val settings by container.settings.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val stage = when {
            !splashDone -> 0
            settings?.onboardingDone == false -> 1
            else -> 2
        }
        Crossfade(stage, animationSpec = tween(450), label = "root") { s ->
            when (s) {
                0 -> AizaSplash(onFinished = { splashDone = true })
                1 -> OnboardingScreen(onFinished = { scope.launch { container.settingsRepository.setOnboardingDone(true) } })
                else -> AizaNavHost(container)
            }
        }
        // The tag is ringing the phone: cover everything (FR-RNG-02).
        val ring by container.phoneRinger.ringing.collectAsStateWithLifecycle()
        var shown by remember { mutableStateOf<PhoneRing?>(null) }
        if (ring != null) shown = ring
        AnimatedVisibility(ring != null, enter = fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 1.05f), exit = fadeOut(tween(300))) {
            shown?.let { PhoneRingScreen(it, onStop = container.phoneRinger::stop) }
        }
    }
}

@Composable
private fun AizaNavHost(container: AppContainer) {
    val nav = rememberNavController()
    // Opened from a left-behind notification.
    val openTag by container.openTagRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openTag) {
        openTag?.let { nav.navigate(TagRoute(it)) { launchSingleTop = true }; container.openTagRequest.value = null }
    }
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
                onPermissions = { nav.navigate(PermissionsRoute) },
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
            SearchScreen(
                address = entry.toRoute<SearchRoute>().address,
                onClose = { nav.popBackStack() },
                onTrialFinished = { id ->
                    if (id != null) nav.navigate(TrialRoute(id)) { popUpTo<ResearcherRoute>() }
                    else nav.popBackStack<ResearcherRoute>(inclusive = false)
                },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onResearcher = { nav.navigate(ResearcherRoute) },
                onPermissions = { nav.navigate(PermissionsRoute) },
            )
        }
        composable<ResearcherRoute> {
            ResearcherScreen(
                onBack = { nav.popBackStack() },
                onNewTrial = { nav.navigate(NewTrialRoute) },
                onOpenTrial = { nav.navigate(TrialRoute(it)) },
                onMotionTest = { nav.navigate(MotionTestRoute) },
            )
        }
        composable<MotionTestRoute> { MotionTestScreen(onBack = { nav.popBackStack() }) }
        composable<NewTrialRoute> { NewTrialScreen(onBack = { nav.popBackStack() }, onContinue = { nav.navigate(ConsentRoute) }) }
        composable<ConsentRoute> {
            ConsentScreen(
                onBack = { nav.popBackStack() },
                onStarted = { address -> nav.navigate(SearchRoute(address)) { popUpTo<ResearcherRoute>() } },
            )
        }
        composable<TrialRoute> { entry -> TrialDetailScreen(entry.toRoute<TrialRoute>().id, onBack = { nav.popBackStack() }) }
        composable<PermissionsRoute> { PermissionsScreen(onBack = { nav.popBackStack() }) }
    }
}
