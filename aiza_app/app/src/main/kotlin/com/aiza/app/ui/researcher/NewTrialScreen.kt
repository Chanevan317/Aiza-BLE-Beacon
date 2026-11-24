@file:OptIn(ExperimentalLayoutApi::class)

package com.aiza.app.ui.researcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiza.app.AizaApplication
import com.aiza.app.data.TrialDraft
import com.aiza.app.ui.components.AizaCard
import com.aiza.app.ui.components.HSpace
import com.aiza.app.ui.components.SectionLabel
import com.aiza.app.ui.components.VSpace
import com.aiza.core.model.HeadingSample
import com.aiza.data.trial.LineOfSight
import com.aiza.data.trial.SearchStyle
import kotlinx.coroutines.flow.filterIsInstance
import kotlin.math.roundToInt

/** Trial setup (FR-TRL-01) and ground truth (FR-TRL-02). */
@Composable
fun NewTrialScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    val container = (LocalContext.current.applicationContext as AizaApplication).container
    val tags by container.tags.tags.collectAsStateWithLifecycle(emptyList())
    val defaultCode = remember { container.trialStore.nextVolunteerCode() }

    var tagAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var volunteer by rememberSaveable { mutableStateOf(defaultCode) }
    var place by rememberSaveable { mutableStateOf("") }
    var room by rememberSaveable { mutableStateOf("") }
    var distance by rememberSaveable { mutableFloatStateOf(3f) }
    var bearing by rememberSaveable { mutableStateOf<Float?>(null) }
    var los by rememberSaveable { mutableStateOf(LineOfSight.CLEAR) }
    var style by rememberSaveable { mutableStateOf(SearchStyle.WALKING) }
    var wedge by rememberSaveable { mutableStateOf(true) }
    var notes by rememberSaveable { mutableStateOf("") }

    // Live compass for capturing the bearing from the start point to the tag.
    var heading by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        container.motionSensors.samples().filterIsInstance<HeadingSample>().collect { heading = it.headingDeg }
    }

    if (tagAddress == null && tags.isNotEmpty()) tagAddress = tags.first().address
    val ready = tagAddress != null && volunteer.isNotBlank() && room.isNotBlank()

    ResearchScaffold(
        "New trial", onBack,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = {
                        val tag = tags.first { it.address == tagAddress }
                        container.trialRecorder.draft = TrialDraft(
                            tagAddress = tag.address, tagSerial = tag.serial, tagName = tag.name,
                            volunteerCode = volunteer.trim(), place = place.trim(), room = room.trim(),
                            tagDistanceM = distance, tagBearingDeg = bearing,
                            lineOfSight = los, searchStyle = style, wedgeShown = wedge, notes = notes.trim(),
                        )
                        onContinue()
                    },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Continue to consent") }
            }
        },
    ) {
        item {
            SectionLabel("Tag")
            if (tags.isEmpty()) Text("Pair a tag first.", color = MaterialTheme.colorScheme.error)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { t -> FilterChip(t.address == tagAddress, { tagAddress = t.address }, { Text(t.name) }) }
            }
        }
        item {
            SectionLabel("Volunteer and place")
            AizaCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    volunteer, { volunteer = it.uppercase().take(8) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Volunteer code") }, supportingText = { Text("A code, never a name") },
                )
                OutlinedTextField(place, { place = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Place (e.g. Home 1, Office)") })
                VSpace(4.dp)
                OutlinedTextField(room, { room = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Room") })
            }
        }
        item {
            SectionLabel("Ground truth")
            AizaCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Distance start → tag", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text("%.1f m".format(distance), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(distance, { distance = (it * 2).roundToInt() / 2f }, valueRange = 0.5f..10f)
                Text("Measure with a tape from where the volunteer starts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                VSpace(16.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bearing to tag", style = MaterialTheme.typography.titleSmall)
                        Text(
                            bearing?.let { "${it.roundToInt()}° from north" } ?: "Not set",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (bearing != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Stand at the start, point the top of the phone at the tag, then capture." +
                                (heading?.let { "  Now: ${it.roundToInt()}°" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HSpace(8.dp)
                    Column(horizontalAlignment = Alignment.End) {
                        FilledTonalButton({ bearing = heading }, enabled = heading != null) {
                            Icon(Icons.Rounded.Explore, null); HSpace(6.dp); Text("Capture")
                        }
                        if (bearing != null) TextButton({ bearing = null }) { Text("Clear") }
                    }
                }
            }
        }
        item {
            SectionLabel("Conditions")
            AizaCard(Modifier.fillMaxWidth()) {
                Text("Line of sight", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineOfSight.entries.forEach { FilterChip(los == it, { los = it }, { Text(it.label) }) }
                }
                VSpace(10.dp)
                Text("Search style", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchStyle.entries.forEach { FilterChip(style == it, { style = it }, { Text(it.label) }) }
                }
                VSpace(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show direction wedge", style = MaterialTheme.typography.titleSmall)
                        Text("Turn off for the without-wedge comparison", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(wedge, { wedge = it })
                }
            }
        }
        item {
            SectionLabel("Notes")
            OutlinedTextField(notes, { notes = it.take(200) }, Modifier.fillMaxWidth(), minLines = 2, placeholder = { Text("Optional") }, shape = MaterialTheme.shapes.medium)
        }
    }
}
