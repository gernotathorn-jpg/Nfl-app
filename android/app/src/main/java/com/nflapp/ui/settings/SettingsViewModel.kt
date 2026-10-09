package com.nflapp.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nflapp.data.NflRepository
import com.nflapp.data.SettingsRepository
import com.nflapp.data.local.SyncMetaEntity
import com.nflapp.data.remote.ModelDto
import com.nflapp.ui.refreshWithMessage
import com.nflapp.work.SyncSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class SettingsState(
    val wifiOnly: Boolean = true,
    val predictions: SyncMetaEntity? = null,
    val stats: SyncMetaEntity? = null,
    val model: ModelDto? = null,
    val nextSlot: Instant = SyncSchedule.nextSlot(Instant.now()),
)

class SettingsViewModel(
    private val app: Application,
    private val repository: NflRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(
        settings.wifiOnly,
        repository.predictionsMeta,
        repository.statsMeta,
        repository.modelInfo,
    ) { wifi, p, s, m -> SettingsState(wifi, p, s, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch {
            settings.setWifiOnly(value)
            SyncSchedule.schedule(app, value)
        }
    }

    fun refreshNow() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = repository.refreshWithMessage() ?: "Daten sind aktuell."
            _busy.value = false
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
