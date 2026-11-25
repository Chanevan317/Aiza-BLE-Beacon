@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.aiza.app.ui.pairing

import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiza.app.data.DiscoveredTag
import com.aiza.app.ui.aizaViewModel
import com.aiza.app.ui.components.AizaCard
import com.aiza.app.ui.components.HSpace
import com.aiza.app.ui.components.PulseRings
import com.aiza.app.ui.components.SignalBars
import com.aiza.app.ui.components.TagAvatar
import com.aiza.app.ui.components.VSpace
import com.aiza.app.ui.components.accent
import com.aiza.app.ui.components.label
import com.aiza.app.ui.components.vector
import com.aiza.app.ui.theme.AizaTheme
import com.aiza.data.tags.TagIcon

/** The "Add tag" popup: instructions → search → name & icon → done. */
@Composable
fun PairingSheet(onDismiss: () -> Unit, onPaired: (String) -> Unit) {
    val vm = aizaViewModel(key = "pairing") { PairingViewModel(it) }
    val step by vm.step.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        AnimatedContent(
            step,
            contentKey = { it::class },
            transitionSpec = {
                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(180)))
            },
            label = "pairing-step",
        ) { s ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (s) {
                    PairingStep.Intro -> IntroStep(onNext = vm::startSearch, onCancel = onDismiss)
                    is PairingStep.Searching -> SearchingStep(s, onPick = vm::select, onBack = vm::back)
                    is PairingStep.Connecting -> ConnectingStep(onBack = vm::back)
                    is PairingStep.Failed -> FailedStep(s.message, onRetry = vm::back)
                    is PairingStep.Naming -> NamingStep(s.tag, onSave = { name, icon -> vm.save(s.tag, name, icon) }, onBack = vm::back)
                    is PairingStep.Done -> DoneStep(s, onDone = { onPaired(s.address) })
                }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    VSpace(8.dp)
    Text(
        body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun IntroStep(onNext: () -> Unit, onCancel: () -> Unit) {
    VSpace(8.dp)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(170.dp)) {
        PulseRings(MaterialTheme.colorScheme.primary, Modifier.size(170.dp), periodMs = 2000, minRadiusFraction = 0.45f, filled = true)
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(AizaTheme.extra.brandGradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.TouchApp, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
    VSpace(12.dp)
    StepTitle(
        "Get your tag ready",
        "Press and hold the button on your AIZA tag for 2 seconds until it beeps. Keep it close to your phone.",
    )
    VSpace(28.dp)
    Button(onNext, Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large) {
        Text("It's beeping — continue")
    }
    TextButton(onCancel) { Text("Not now") }
}

@Composable
private fun SearchingStep(s: PairingStep.Searching, onPick: (DiscoveredTag) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) CompanionPairing.result(r.data)?.let(onPick)
    }
    BackRow(onBack)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        PulseRings(MaterialTheme.colorScheme.primary, Modifier.size(150.dp), periodMs = 1500, minRadiusFraction = 0.35f)
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.BluetoothSearching, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
    VSpace(8.dp)
    if (s.demo) {
        StepTitle(
            if (s.found.isEmpty()) "Looking for tags…" else "Tags found",
            if (s.found.isEmpty()) "Make sure the tag is beeping and within a metre of your phone." else "Tap the tag you want to add.",
        )
        VSpace(20.dp)
        s.found.forEachIndexed { i, tag ->
            AnimatedVisibility(true, enter = slideInVertically { it / 2 } + fadeIn()) {
                AizaCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = { onPick(tag) }, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TagAvatar(TagIcon.TAG, size = 44.dp)
                        HSpace(14.dp)
                        Column(Modifier.weight(1f)) {
                            Text("AIZA Tag ${i + 1}", style = MaterialTheme.typography.titleSmall)
                            Text(tag.serial, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SignalBars(tag.rssiDbm)
                    }
                }
            }
        }
        VSpace(8.dp)
    } else {
        StepTitle("Pair with your phone", "Android will show a list of nearby AIZA tags. Choose yours to pair it securely.")
        VSpace(24.dp)
        Button(
            onClick = {
                error = null
                CompanionPairing.start(
                    context,
                    onIntentSender = { launcher.launch(IntentSenderRequest.Builder(it).build()) },
                    onError = { error = it },
                )
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Find my tag") }
        error?.let {
            VSpace(12.dp)
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        VSpace(8.dp)
    }
}

@Composable
private fun ConnectingStep(onBack: () -> Unit) {
    BackRow(onBack)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        PulseRings(MaterialTheme.colorScheme.primary, Modifier.size(150.dp), periodMs = 1200, minRadiusFraction = 0.35f)
        CircularProgressIndicator(Modifier.size(56.dp))
    }
    VSpace(8.dp)
    StepTitle("Pairing…", "Securing the connection and reading the tag's ID. Keep it close to the phone.")
    VSpace(24.dp)
}

@Composable
private fun FailedStep(message: String, onRetry: () -> Unit) {
    VSpace(24.dp)
    Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
    VSpace(14.dp)
    StepTitle("Couldn't add the tag", message)
    VSpace(24.dp)
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large) { Text("Try again") }
    VSpace(8.dp)
}

@Composable
private fun NamingStep(tag: DiscoveredTag, onSave: (String, TagIcon) -> Unit, onBack: () -> Unit) {
    var icon by rememberSaveable { mutableStateOf(TagIcon.KEYS) }
    var name by rememberSaveable { mutableStateOf("") }
    BackRow(onBack)
    TagAvatar(icon, size = 76.dp)
    VSpace(14.dp)
    StepTitle("What is it attached to?", "Pick an icon and give it a name. You can change these later.")
    VSpace(20.dp)
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(24) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Name") },
        placeholder = { Text(icon.label) },
        shape = MaterialTheme.shapes.medium,
    )
    VSpace(16.dp)
    IconPicker(icon, onPick = { icon = it })
    VSpace(24.dp)
    Button(onClick = { onSave(name.ifBlank { icon.label }, icon) }, Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large) {
        Text("Add ${name.ifBlank { icon.label }}")
    }
    VSpace(8.dp)
    Text(tag.serial, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
}

/** Grid of item types; reused when editing a tag. */
@Composable
fun IconPicker(current: TagIcon, onPick: (TagIcon) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TagIcon.entries.forEach { option ->
            val isSel = option == current
            val (a, _) = option.accent
            Surface(
                onClick = { onPick(option) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSel) a.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (isSel) BorderStroke(2.dp, a) else null,
                modifier = Modifier.size(52.dp).semantics { selected = isSel; role = Role.RadioButton },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        option.vector, contentDescription = option.label,
                        tint = if (isSel) a else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DoneStep(s: PairingStep.Done, onDone: () -> Unit) {
    VSpace(16.dp)
    AnimatedVisibility(true, enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) {
        Box(contentAlignment = Alignment.Center) {
            PulseRings(AizaTheme.extra.success, Modifier.size(160.dp), rings = 2, minRadiusFraction = 0.5f)
            Surface(shape = CircleShape, color = AizaTheme.extra.success, modifier = Modifier.size(84.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }
        }
    }
    VSpace(8.dp)
    StepTitle("${s.name} is ready", "Your tag is paired. Open it any time to ring it or start a search.")
    VSpace(28.dp)
    Button(onDone, Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large) { Text("Open ${s.name}") }
    VSpace(8.dp)
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
    }
}
