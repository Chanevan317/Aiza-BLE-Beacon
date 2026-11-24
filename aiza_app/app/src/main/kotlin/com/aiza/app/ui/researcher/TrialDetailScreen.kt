package com.aiza.app.ui.researcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiza.app.AizaApplication
import com.aiza.app.ui.components.AizaCard
import com.aiza.app.ui.components.HSpace
import com.aiza.app.ui.components.SectionLabel
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun TrialDetailScreen(trialId: String, onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as AizaApplication).container
    val trials by container.trialStore.trials.collectAsStateWithLifecycle()
    val t = trials.find { it.trialId == trialId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            context.contentResolver.openOutputStream(uri)?.use { container.trialStore.exportZip(listOf(trialId), it) }
            snackbar.showSnackbar("Exported $trialId")
        }
    }

    ResearchScaffold(trialId, onBack, snackbar) {
        if (t == null) { item { Text("Trial not found.") }; return@ResearchScaffold }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AizaCard(Modifier.weight(1f)) {
                    Text(t.durationMs?.let { formatDuration(it) } ?: "—", style = MaterialTheme.typography.headlineMedium)
                    Text(if (t.outcome.name == "FOUND") "Time to find" else "Duration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AizaCard(Modifier.weight(1f)) {
                    OutcomeChip(t.outcome)
                    Text("${t.checkpoints} checkpoint${if (t.checkpoints == 1) "" else "s"} · ${t.rowCount} rows", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionLabel("Setup")
            AizaCard(Modifier.fillMaxWidth()) {
                KeyValue("Volunteer", t.volunteerCode)
                KeyValue("Consent", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t.consentEpochMs)))
                KeyValue("Tag", "${t.tagName} (${t.tagSerial})")
                KeyValue("Place / room", listOf(t.place, t.room).filter { it.isNotBlank() }.joinToString(" / "))
                KeyValue("Distance", "%.1f m".format(t.tagDistanceM))
                KeyValue("Bearing", t.tagBearingDeg?.let { "${it.roundToInt()}°" } ?: "Not set")
                KeyValue("Line of sight", t.lineOfSight.label)
                KeyValue("Search style", t.searchStyle.label)
                KeyValue("Wedge shown", if (t.wedgeShown) "Yes" else "No")
                if (t.steps > 0) KeyValue("Walked", "${t.steps} steps · ${"%.1f".format(t.walkedM)} m (%.1f× straight line)".format(t.walkedM / t.tagDistanceM))
                if (t.notes.isNotBlank()) KeyValue("Notes", t.notes)
            }
        }
        item {
            SectionLabel("Device")
            AizaCard(Modifier.fillMaxWidth()) {
                KeyValue("Phone", t.phoneModel)
                KeyValue("Android", t.androidVersion)
                KeyValue("App", t.appVersion)
                KeyValue("Signal source", if (t.simulated) "Simulated (demo mode)" else "Real tag")
            }
        }
        item {
            Button({ export.launch("$trialId.zip") }, Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.large) {
                Icon(Icons.Rounded.Download, null); HSpace(8.dp); Text("Export (ZIP with CSV)")
            }
        }
        item {
            OutlinedButton(
                { confirmDelete = true }, Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Icon(Icons.Rounded.Delete, null); HSpace(8.dp); Text("Delete trial") }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete $trialId?") },
        text = { Text("The log and its details will be permanently removed from this phone.") },
        confirmButton = {
            TextButton(
                { confirmDelete = false; container.trialStore.delete(trialId); onBack() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
}
