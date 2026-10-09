package com.nflapp.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.nflapp.NflApp
import com.nflapp.data.NflRepository
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** Creates a ViewModel with access to the manual DI container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (app: NflApp, handle: SavedStateHandle) -> VM,
): VM = viewModel(
    factory = viewModelFactory {
        initializer { create(this[APPLICATION_KEY] as NflApp, createSavedStateHandle()) }
    },
)

/** Runs a manual refresh and returns a user-facing error message, or null on success. */
suspend fun NflRepository.refreshWithMessage(): String? = try {
    refresh()
    null
} catch (e: IOException) {
    "Keine Verbindung – es werden die gespeicherten Daten angezeigt."
} catch (e: HttpException) {
    if (e.code() == 404) "Daten nicht gefunden (HTTP 404). Ist die Pages-URL korrekt?"
    else "Serverfehler (HTTP ${e.code()})."
} catch (e: SerializationException) {
    "Daten konnten nicht gelesen werden."
}

/** Screens sit inside the outer Scaffold that already handles the navigation bar inset. */
val NoInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
