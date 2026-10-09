package com.nflapp.ui.teams

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nflapp.data.NflRepository
import com.nflapp.data.local.EloPointEntity
import com.nflapp.data.local.GameEntity
import com.nflapp.data.local.TeamEntity
import com.nflapp.data.local.TeamSeasonEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class TeamSort { ELO, DIVISION }

data class TeamsState(val loaded: Boolean = false, val sort: TeamSort = TeamSort.ELO, val teams: List<TeamEntity> = emptyList())

class TeamsViewModel(repository: NflRepository) : ViewModel() {
    private val sort = MutableStateFlow(TeamSort.ELO)

    val state: StateFlow<TeamsState> = combine(repository.teams, sort) { teams, s ->
        val sorted = when (s) {
            TeamSort.ELO -> teams.sortedBy { it.eloRank }
            TeamSort.DIVISION -> teams.sortedWith(
                compareBy<TeamEntity>({ it.conference }, { it.division }, { -it.record.w }, { it.record.l }),
            )
        }
        TeamsState(true, s, sorted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamsState())

    fun setSort(value: TeamSort) {
        sort.value = value
    }
}

data class TeamDetailState(
    val loaded: Boolean = false,
    val team: TeamEntity? = null,
    val seasons: List<TeamSeasonEntity> = emptyList(),
    val eloHistory: List<EloPointEntity> = emptyList(),
    val nextGame: GameEntity? = null,
)

class TeamDetailViewModel(repository: NflRepository, handle: SavedStateHandle) : ViewModel() {
    private val abbr: String = checkNotNull(handle["abbr"])

    val state: StateFlow<TeamDetailState> = combine(
        repository.team(abbr),
        repository.seasons(abbr),
        repository.eloHistory(abbr),
        repository.games,
    ) { team, seasons, elo, games ->
        TeamDetailState(true, team, seasons, elo, games.firstOrNull { it.home == abbr || it.away == abbr })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamDetailState())
}
