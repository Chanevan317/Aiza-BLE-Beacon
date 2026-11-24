package com.aiza.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiza.app.ui.components.AizaMark
import com.aiza.app.ui.components.HSpace
import com.aiza.app.ui.components.PulseRings
import com.aiza.app.ui.components.VSpace
import com.aiza.app.ui.permissions.PermissionList
import com.aiza.app.ui.permissions.rememberPermissionController
import com.aiza.app.ui.theme.AizaTheme
import com.aiza.app.ui.theme.Brand
import kotlinx.coroutines.launch

private const val PAGES = 4

/** First-launch "Get started": what AIZA does, how search works, privacy, then permissions. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val permissions = rememberPermissionController()
    val last = pager.currentPage == PAGES - 1

    // Surface supplies the onBackground content colour, so plain Text is readable in light and dark.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            if (!last) TextButton({ scope.launch { pager.animateScrollToPage(PAGES - 1) } }) { Text("Skip") }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            when (page) {
                0 -> InfoPage(
                    art = { WelcomeArt() },
                    title = "Welcome to AIZA",
                    body = "Attach a small AIZA tag to your keys, wallet or bag. When something goes missing, your phone helps you find it.",
                )
                1 -> InfoPage(
                    art = { SearchArt() },
                    title = "Warmer, warmer… found it",
                    body = "Walk around and AIZA tells you if you're getting warmer or colder. When it's confident, it points you in the right direction. Or just ring the tag.",
                )
                2 -> InfoPage(
                    art = { PrivacyArt() },
                    title = "Private by design",
                    body = "No account, no cloud. Everything stays on your phone, and your tag keeps changing its Bluetooth identity so strangers can't follow it.",
                )
                else -> PermissionsPage(permissions)
            }
        }
        Dots(pager.currentPage)
        VSpace(20.dp)
        Column(Modifier.padding(horizontal = 24.dp)) {
            AnimatedContent(
                when {
                    !last -> 0
                    permissions.state.allGranted -> 2
                    else -> 1
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "cta",
            ) { mode ->
                Button(
                    onClick = {
                        when (mode) {
                            0 -> scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                            1 -> permissions.requestAll()
                            else -> onFinished()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) { Text(when (mode) { 0 -> "Next"; 1 -> "Allow access"; else -> "Get started" }) }
            }
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                if (last && !permissions.state.allGranted) TextButton(onFinished) { Text("Not now") }
            }
        }
        VSpace(8.dp)
    }
    }
}

@Composable
private fun InfoPage(art: @Composable BoxScope.() -> Unit, title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) { art() }
        VSpace(28.dp)
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        VSpace(12.dp)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PermissionsPage(permissions: com.aiza.app.ui.permissions.PermissionController) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        VSpace(8.dp)
        Text("A few permissions", style = MaterialTheme.typography.headlineMedium)
        VSpace(8.dp)
        Text(
            "AIZA only uses these to find your tags. Nothing is shared or uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(20.dp)
        PermissionList(permissions)
        VSpace(16.dp)
    }
}

@Composable
private fun Dots(current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(PAGES) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot-w")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "dot-c",
            )
            Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(width).background(color, CircleShape))
        }
    }
}

// --- Illustrations, built from the app's own visual language ---

@Composable
private fun BoxScope.WelcomeArt() {
    PulseRings(MaterialTheme.colorScheme.primary, Modifier.size(260.dp), periodMs = 2600, minRadiusFraction = 0.4f)
    AizaMark(size = 112.dp)
}

@Composable
private fun BoxScope.SearchArt() {
    val heat = AizaTheme.extra.heatScale
    PulseRings(Brand.Hot, Modifier.size(260.dp), periodMs = 1400, minRadiusFraction = 0.45f, filled = true)
    Box(
        Modifier.size(130.dp).clip(CircleShape).background(Brush.linearGradient(listOf(heat[2], heat[3]))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Warmer ↑", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Close", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun BoxScope.PrivacyArt() {
    Box(
        Modifier.size(150.dp).clip(RoundedCornerShape(48.dp)).background(Brush.linearGradient(AizaTheme.extra.brandGradient)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Shield, null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(110.dp))
        Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(52.dp))
    }
    Row(Modifier.align(Alignment.BottomCenter)) {
        Chip("No account")
        HSpace(8.dp)
        Chip("No cloud")
    }
}

@Composable
private fun Chip(text: String) {
    Box(
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, style = MaterialTheme.typography.labelMedium) }
}
