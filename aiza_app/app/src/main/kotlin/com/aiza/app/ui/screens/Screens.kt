package com.aiza.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Placeholder screens. Each lists the SRS requirements it must satisfy.

/** All tags with status and battery (FR-STA-01, FR-STA-02). */
@Composable
fun HomeScreen(onOpenTag: (String) -> Unit, onAddTag: () -> Unit, onSettings: () -> Unit, onResearcher: () -> Unit) {
    ScreenScaffold("AIZA") {
        Text("No tags yet.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddTag) { Text("Add tag") }
        OutlinedButton(onClick = onSettings) { Text("Settings") }
        OutlinedButton(onClick = onResearcher) { Text("Researcher mode") }
    }
}

/** CompanionDeviceManager pairing, then name + icon (FR-SET-01, FR-SET-02). */
@Composable
fun AddTagScreen(onDone: () -> Unit) {
    ScreenScaffold("Add tag", onBack = onDone) {
        Text("TODO: CompanionDeviceManager association + LE Secure Connections bonding.")
    }
}

/** Status, battery, last-seen map, alert switch; Ring, Find, Unpair (FR-RNG-01, FR-SEP-03, FR-SET-03). */
@Composable
fun TagDetailScreen(address: String, onFind: () -> Unit, onBack: () -> Unit) {
    ScreenScaffold("Tag $address", onBack = onBack) {
        Button(onClick = { /* TODO ring */ }) { Text("Ring") }
        Button(onClick = onFind) { Text("Find") }
        OutlinedButton(onClick = { /* TODO unpair */ }) { Text("Unpair") }
    }
}

/** Hot/cold meter, 120° wedge or "keep walking", Ring always visible (FR-HC-*, FR-DIR-*, FR-SPN-01). */
@Composable
fun FindScreen(address: String, onStop: () -> Unit) {
    ScreenScaffold("Finding $address", onBack = onStop) {
        Text("Keep walking…")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { /* TODO ring */ }) { Text("Ring") }
        OutlinedButton(onClick = { /* TODO spin */ }) { Text("Turn slowly") }
    }
}

/** Alert threshold, sound, help. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    ScreenScaffold("Settings", onBack = onBack) { Text("TODO") }
}

/** Trial setup, ground truth, log list, export, delete (FR-TRL-01..05). */
@Composable
fun ResearcherScreen(onBack: () -> Unit) {
    ScreenScaffold("Researcher mode", onBack = onBack) { Text("TODO") }
}
