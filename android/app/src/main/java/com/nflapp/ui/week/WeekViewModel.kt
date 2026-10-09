package com.nflapp.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nflapp.data.NflRepository
import com.nflapp.data.WeekInfo
import com.nflapp.data.local.GameEntity
import com.nflapp.data.local.TeamEntity
import com.nflapp.ui.refreshWithMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeekUiState(
    val loaded: Boolean = false,
    val info: WeekInfo? = null,
    val games: List<GameEntity> = emptyList(),
    val teams: Map<String, TeamEntity> = emptyMap(),
    val lastCheckedMs: Long? = null,
)

class WeekViewModel(private val repository: NflRepository) : ViewModel() {

    val state: StateFlow<WeekUiState> = combine(
        repository.weekInfo,
        repository.games,
        repository.teams,
        repository.predictionsMeta,
    ) { info, games, teams, meta ->
        WeekUiState(true, info, games, teams.associateBy { it.abbr }, meta?.lastCheckedMs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        // First start: nothing cached yet, so try to load once. Afterwards the
        // app runs from Room and WorkManager keeps it fresh.
        viewModelScope.launch {
            if (repository.lastCheckedMs() == null) refresh()
        }
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _message.value = repository.refreshWithMessage()
            _refreshing.value = false
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
