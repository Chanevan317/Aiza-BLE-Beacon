package com.aiza.app.ui.researcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiza.app.AizaApplication
import com.aiza.app.ui.components.AizaCard
import com.aiza.app.ui.components.HSpace
import com.aiza.app.ui.components.SectionLabel
import com.aiza.app.ui.components.VSpace
import com.aiza.app.ui.components.relativeTime
import com.aiza.data.trial.TrialMeta
import com.aiza.data.trial.TrialOutcome
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Trial list, new trial, export all, delete a volunteer's data (FR-TRL-01, 04, 05). */
@Composable
fun ResearcherScreen(onBack: () -> Unit, onNewTrial: () -> Unit, onOpenTrial: (String) -> Unit) {
    val container = (LocalContext.current.applicationContext as AizaApplication).container
    val trials by container.trialStore.trials.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var deleteVolunteer by rememberSaveable { mutableStateOf(false) }

    val exportAll = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            context.contentResolver.openOutputStream(uri)?.use { container.trialStore.exportZip(trials.map { it.trialId }, it) }
            snackbar.showSnackbar("Exported ${trials.size} trials")
        }
    }

    ResearchScaffold(
        "Researcher mode", onBack, snackbar,
        actions = {
            Box {
                FilledTonalIconButton({ menu = true }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Icon(Icons.Rounded.MoreVert, "More")
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Export all (ZIP)") }, leadingIcon = { Icon(Icons.Rounded.Download, null) },
                        enabled = trials.isNotEmpty(),
                        onClick = { menu = false; exportAll.launch("aiza-trials-${stamp()}.zip") },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete a volunteer's data") }, leadingIcon = { Icon(Icons.Rounded.PersonRemove, null) },
                        enabled = trials.isNotEmpty(),
                        onClick = { menu = false; deleteVolunteer = true },
                    )
                }
            }
        },
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Trials", "${trials.size}", Modifier.weight(1f))
                Stat("Found", "${trials.count { it.outcome == TrialOutcome.FOUND }}", Modifier.weight(1f))
                Stat("Volunteers", "${trials.map { it.volunteerCode }.distinct().size}", Modifier.weight(1f))
            }
        }
        item {
            Button(onNewTrial, Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large) {
                Icon(Icons.Rounded.Add, null); HSpace(8.dp); Text("New trial")
            }
        }
        if (trials.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Science, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    VSpace(12.dp)
                    Text("No trials yet", style = MaterialTheme.typography.titleMedium)
                    VSpace(4.dp)
                    Text(
                        "Each trial records signal strength, motion sensors, heading and taps while a volunteer searches for a hidden tag.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            item { SectionLabel("Trials") }
            items(trials, key = { it.trialId }) { t -> TrialRow(t) { onOpenTrial(t.trialId) } }
        }
    }

    if (deleteVolunteer) {
        var code by rememberSaveable { mutableStateOf("") }
        val matches = trials.count { it.volunteerCode.equals(code.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { deleteVolunteer = false },
            icon = { Icon(Icons.Rounded.PersonRemove, null) },
            title = { Text("Delete a volunteer's data") },
            text = {
                Column {
                    Text("Enter the volunteer's code. All of their trials will be permanently deleted from this phone.")
                    VSpace(12.dp)
                    OutlinedTextField(code, { code = it.uppercase().take(8) }, singleLine = true, label = { Text("Volunteer code") }, placeholder = { Text("V01") })
                    if (code.isNotBlank()) { VSpace(6.dp); Text("$matches trial(s) match", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = matches > 0,
                    onClick = {
                        val n = container.trialStore.deleteVolunteer(code)
                        deleteVolunteer = false
                        scope.launch { snackbar.showSnackbar("Deleted $n trial(s) for ${code.trim()}") }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton({ deleteVolunteer = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    AizaCard(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrialRow(t: TrialMeta, onClick: () -> Unit) {
    AizaCard(Modifier.fillMaxWidth(), onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.trialId, style = MaterialTheme.typography.titleSmall)
                    HSpace(8.dp)
                    OutcomeChip(t.outcome)
                    if (t.simulated) { HSpace(6.dp); Text("SIM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }
                }
                VSpace(4.dp)
                Text(
                    "${t.volunteerCode} · ${t.room} · ${"%.1f".format(t.tagDistanceM)} m · ${t.lineOfSight.label}" +
                        (t.durationMs?.let { " · ${formatDuration(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(relativeTime(t.startEpochMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

internal fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
