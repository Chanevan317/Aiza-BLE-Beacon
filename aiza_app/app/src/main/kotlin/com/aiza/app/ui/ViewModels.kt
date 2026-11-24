package com.aiza.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiza.app.AizaApplication
import com.aiza.app.data.AppContainer

/** Creates a ViewModel with access to the app container (no DI framework needed yet). */
@Composable
inline fun <reified VM : ViewModel> aizaViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as AizaApplication).container
    return viewModel(key = key) { create(container) }
}
