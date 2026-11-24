package com.aiza.app.ui.researcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aiza.app.AizaApplication
import com.aiza.app.ui.components.AizaCard
import com.aiza.app.ui.components.HSpace
import com.aiza.app.ui.components.VSpace

private val RECORDED = listOf(
    "Bluetooth signal strength from the hidden tag",
    "Your phone's motion sensors and compass while you walk",
    "When you tap buttons in the app, and when you find the tag",
)
private val PROMISES = listOf(
    "No name, photo or GPS position is recorded. You are only a code.",
    "Data stays on this phone and the researcher's computer. Nothing is uploaded.",
    "You can stop at any time, and ask for all your data to be deleted later using your code.",
)

/** Shown to the volunteer before every trial (FR-TRL-05, NFR-PRV-02). */
@Composable
fun ConsentScreen(onBack: () -> Unit, onStarted: (tagAddress: String) -> Unit) {
    val container = (LocalContext.current.applicationContext as AizaApplication).container
    val draft = container.trialRecorder.draft
    var agreed by rememberSaveable { mutableStateOf(false) }
    if (draft == null) { onBack(); return }

    ResearchScaffold(
        "Before you start", onBack,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Button(
                        onClick = {
                            val demo = container.settings.value?.demoMode ?: true
                            container.trialRecorder.start(draft, consentEpochMs = System.currentTimeMillis(), simulated = demo)
                            onStarted(draft.tagAddress)
                        },
                        enabled = agreed,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.large,
                    ) { Text("I agree — start the trial") }
                    TextButton(onBack, Modifier.fillMaxWidth()) { Text("I don't want to take part") }
                }
            }
        },
    ) {
        item {
            Text(
                "Thank you for helping test AIZA. You'll search for a hidden tag using this phone while it records data for the study.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item {
            AizaCard(Modifier.fillMaxWidth()) {
                Text("Your volunteer code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(draft.volunteerCode, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Text("Keep it if you may want your data deleted later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Bullets("What is recorded", RECORDED) }
        item { Bullets("Your privacy", PROMISES) }
        item {
            AizaCard(Modifier.fillMaxWidth().toggleable(agreed, role = Role.Checkbox) { agreed = it }, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(agreed, null)
                    HSpace(8.dp)
                    Text("I have read this and agree to take part.", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun Bullets(title: String, lines: List<String>) {
    AizaCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        VSpace(8.dp)
        lines.forEach { line ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                HSpace(10.dp)
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
