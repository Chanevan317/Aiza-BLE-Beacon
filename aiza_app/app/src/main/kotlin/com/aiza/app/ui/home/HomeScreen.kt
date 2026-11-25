@file:OptIn(ExperimentalMaterial3Api::class)

package com.aiza.app.ui.home

import com.aiza.data.tags.Presence
import com.aiza.app.ui.components.fadeTopEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.rounded.Shield
import com.aiza.app.ui.permissions.rememberPermissionController
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.aiza.data.tags.HomeLayout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiza.app.ui.aizaViewModel
import com.aiza.app.ui.components.AizaCard
import com.aiza.app.ui.components.AizaLogoLockup
import com.aiza.app.ui.components.AizaMark
import com.aiza.app.ui.components.BatteryPill
import com.aiza.app.ui.components.HSpace
import com.aiza.app.ui.components.PulseRings
import com.aiza.app.ui.components.SignalBars
import com.aiza.app.ui.components.StatusDot
import com.aiza.app.ui.components.TagAvatar
import com.aiza.app.ui.components.VSpace
import com.aiza.app.ui.components.relativeTime
import com.aiza.app.ui.pairing.PairingSheet
import com.aiza.app.ui.theme.AizaTheme
import com.aiza.data.tags.Tag
import com.aiza.data.tags.ThemeMode

@Composable
fun HomeScreen(onOpenTag: (String) -> Unit, onSettings: () -> Unit, onPermissions: () -> Unit) {
    val vm = aizaViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var pairing by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyGridState()
    val cardsState = rememberLazyGridState()
    val fabExpanded by remember {
        derivedStateOf {
            val active = if (state.settings.homeLayout == HomeLayout.CARDS) cardsState else listState
            active.firstVisibleItemIndex == 0
        }
    }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { AizaLogoLockup(markSize = 32.dp) },
                actions = {
                    ThemeButton(state.settings.themeMode, onClick = vm::cycleTheme)
                    FilledTonalIconButton(onClick = onSettings, colors = toolbarButtonColors()) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                    HSpace(8.dp)
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            if (state.tags.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { pairing = true },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Add tag") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large,
                )
            }
        },
    ) { padding ->
        if (!state.loading && state.tags.isEmpty()) {
            EmptyState(Modifier.padding(padding), onAdd = { pairing = true })
        } else {
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding() + 4.dp)) {
                // Header stays put; only the tag container cross-fades when switching list <-> cards.
                Header(state, onToggleLayout = vm::toggleLayout, modifier = Modifier.padding(horizontal = 20.dp))
                PermissionBanner(onPermissions, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
                AnimatedContent(
                    state.settings.homeLayout,
                    transitionSpec = {
                        // Old layout gently shrinks and fades; new items stagger in on their own (see appear()).
                        fadeIn(tween(1)) togetherWith
                            (fadeOut(tween(200, easing = FastOutLinearInEasing)) + scaleOut(tween(200), targetScale = 0.97f)) using null
                    },
                    contentAlignment = Alignment.TopCenter,
                    label = "home-layout",
                ) { layout ->
                    val cards = layout == HomeLayout.CARDS
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (cards) 2 else 1),
                        state = if (cards) cardsState else listState,
                        contentPadding = PaddingValues(
                            start = 20.dp, end = 20.dp, top = 20.dp,
                            bottom = padding.calculateBottomPadding() + 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize().fadeTopEdge(),
                    ) {
                        itemsIndexed(state.tags, key = { _, t -> t.address }) { i, tag ->
                            val m = Modifier.animateItem().appear(i)
                            if (cards) TagTile(tag, state.nowMs, onClick = { onOpenTag(tag.address) }, modifier = m)
                            else TagCard(tag, state.nowMs, onClick = { onOpenTag(tag.address) }, modifier = m)
                        }
                    }
                }
            }
        }
    }

    if (pairing) PairingSheet(onDismiss = { pairing = false }, onPaired = { pairing = false; onOpenTag(it) })
}

@Composable
private fun toolbarButtonColors() = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun ThemeButton(mode: ThemeMode, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, colors = toolbarButtonColors()) {
        AnimatedContent(
            mode,
            transitionSpec = { (scaleIn(tween(250), 0.6f) + fadeIn(tween(250))) togetherWith fadeOut(tween(150)) },
            label = "theme",
        ) { m ->
            val (icon, label) = when (m) {
                ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto to "Theme: follow system. Tap for light"
                ThemeMode.LIGHT -> Icons.Rounded.LightMode to "Theme: light. Tap for dark"
                ThemeMode.DARK -> Icons.Rounded.DarkMode to "Theme: dark. Tap to follow system"
            }
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun Header(state: HomeState, onToggleLayout: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your tags", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            if (state.tags.isNotEmpty()) LayoutToggle(state.settings.homeLayout, onToggleLayout)
        }
        VSpace(4.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val n = state.tags.size
            Text(
                "$n ${if (n == 1) "tag" else "tags"} · ${state.nearbyCount} connected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.settings.demoMode) {
                HSpace(10.dp)
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Science, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        HSpace(4.dp)
                        Text("Demo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

/**
 * Staggered entrance: each item fades in, rises 16 dp and grows from 96 % — one after another.
 * Runs when an item is first composed (screen open, list <-> cards switch). Capped so long lists don't drag.
 */
@Composable
private fun Modifier.appear(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120L + 50L * index.coerceAtMost(8))
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 16.dp.toPx()
        val sc = 0.96f + 0.04f * p
        scaleX = sc
        scaleY = sc
    }
}

/** Shown while Bluetooth/location permissions or switches are missing; tapping opens the Permissions screen. */
@Composable
private fun PermissionBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val controller = rememberPermissionController()
    AnimatedVisibility(!controller.state.allReady, modifier, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null)
                HSpace(12.dp)
                Column(Modifier.weight(1f)) {
                    Text("Finish setting up", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (!controller.state.allGranted) "Allow access so AIZA can reach your tags."
                        else "Turn on Bluetooth and location to reach your tags.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null)
            }
        }
    }
}

/** Two-option switch: list rows or card grid. */
@Composable
private fun LayoutToggle(layout: HomeLayout, onToggle: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(3.dp)) {
            listOf(HomeLayout.LIST to Icons.AutoMirrored.Rounded.ViewList, HomeLayout.CARDS to Icons.Rounded.GridView).forEach { (option, icon) ->
                val sel = option == layout
                Surface(
                    onClick = { if (!sel) onToggle() },
                    shape = CircleShape,
                    color = if (sel) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
                    contentColor = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shadowElevation = if (sel) 1.dp else 0.dp,
                    modifier = Modifier.size(width = 44.dp, height = 34.dp)
                        .semantics { selected = sel; role = Role.RadioButton },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = if (option == HomeLayout.LIST) "List view" else "Card view", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** Card view: square-ish tile, two per row. */
@Composable
private fun TagTile(tag: Tag, nowMs: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val presence = tag.presence(nowMs)
    val nearby = presence.heard
    AizaCard(modifier.fillMaxWidth(), onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            TagAvatar(tag.icon, size = 56.dp, dimmed = !nearby)
            Box(Modifier.weight(1f))
            SignalBars(if (nearby) tag.rssiDbm else null, height = 13.dp)
        }
        VSpace(18.dp)
        Text(tag.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        VSpace(4.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(presenceColor(presence))
            HSpace(6.dp)
            Text(
                if (nearby) presence.label else tag.lastSeenEpochMs?.let { relativeTime(it, nowMs) } ?: "Not seen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        VSpace(12.dp)
        BatteryPill(tag.batteryLevel)
    }
}

@Composable
private fun TagCard(tag: Tag, nowMs: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val presence = tag.presence(nowMs)
    val nearby = presence.heard
    AizaCard(modifier.fillMaxWidth(), onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                TagAvatar(tag.icon, dimmed = !nearby)
            }
            HSpace(10.dp)
            Column(Modifier.weight(1f)) {
                Text(tag.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                VSpace(4.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(presenceColor(presence))
                    HSpace(6.dp)
                    Text(
                        statusLine(tag, nowMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            HSpace(8.dp)
            Column(horizontalAlignment = Alignment.End) {
                BatteryPill(tag.batteryLevel)
                VSpace(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SignalBars(if (nearby) tag.rssiDbm else null, height = 13.dp)
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Green when connected, grey when not. */
@Composable
internal fun presenceColor(p: Presence): Color =
    if (p == Presence.CONNECTED) AizaTheme.extra.success else MaterialTheme.colorScheme.outline

internal fun statusLine(tag: Tag, nowMs: Long): String = when {
    tag.presence(nowMs).heard -> tag.presence(nowMs).label + (tag.lastSeenPlace?.let { " · $it" } ?: "")
    tag.lastSeenEpochMs != null -> "Seen ${relativeTime(tag.lastSeenEpochMs!!, nowMs)}" + (tag.lastSeenPlace?.let { " · $it" } ?: "")
    else -> "Not seen yet"
}

@Composable
private fun EmptyState(modifier: Modifier, onAdd: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            PulseRings(MaterialTheme.colorScheme.primary, Modifier.size(200.dp), minRadiusFraction = 0.35f)
            AizaMark(size = 72.dp)
        }
        VSpace(16.dp)
        Text("No tags yet", style = MaterialTheme.typography.headlineSmall)
        VSpace(8.dp)
        Text(
            "Attach an AIZA tag to your keys, wallet or bag, then pair it here to find it any time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        VSpace(28.dp)
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Pair your first tag") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = MaterialTheme.shapes.large,
        )
    }
}
