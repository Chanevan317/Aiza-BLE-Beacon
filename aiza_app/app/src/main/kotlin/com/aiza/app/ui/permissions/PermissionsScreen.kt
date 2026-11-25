@file:OptIn(ExperimentalMaterial3Api::class)

package com.aiza.app.ui.permissions

import androidx.compose.ui.graphics.Color
import com.aiza.app.ui.components.topBarBlur
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiza.app.ui.components.VSpace

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val controller = rememberPermissionController()
    val haze = rememberHazeState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.topBarBlur(haze),
                title = { Text("Permissions", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onBack, Modifier.padding(start = 8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().hazeSource(haze).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            Text(
                if (controller.state.allReady) "Everything is set up. AIZA can find and connect to your tags."
                else "AIZA needs these to find and connect to your tags. You can change them any time in Android settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VSpace(16.dp)
            PermissionList(controller)
            VSpace(24.dp)
        }
    }
}
