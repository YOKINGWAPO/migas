package com.jokin.calorias.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jokin.calorias.AppContainer
import com.jokin.calorias.CaloriasApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as CaloriasApp).container

/** ViewModel built from the app container, scoped to the current navigation entry. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, initializer = { create(container) })
}
