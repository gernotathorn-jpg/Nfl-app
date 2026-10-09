package com.nflapp.ui.compare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nflapp.data.NflRepository
import com.nflapp.data.local.HeadToHeadEntity
import com.nflapp.data.local.TeamEntity
import com.nflapp.data.local.TeamSeasonEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class CompareState(
    val teams: List<TeamEntity> = emptyList(),
    val a: TeamEntity? = null,
    val b: TeamEntity? = null,
    val seasonsA: List<TeamSeasonEntity> = emptyList(),
    val seasonsB: List<TeamSeasonEntity> = emptyList(),
    val headToHead: HeadToHeadEntity? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CompareViewModel(private val repository: NflRepository, private val handle: SavedStateHandle) : ViewModel() {
    private val selA = handle.getStateFlow<String?>(KEY_A, null)
    private val selB = handle.getStateFlow<String?>(KEY_B, null)

    val state: StateFlow<CompareState> = combine(repository.teams, selA, selB) { teams, a, b ->
        // Default selection: the two best teams by Elo.
        val byRank = teams.sortedBy { it.eloRank }
        Triple(teams, a ?: byRank.getOrNull(0)?.abbr, b ?: byRank.getOrNull(1)?.abbr)
    }.flatMapLatest { (teams, a, b) ->
        if (a == null || b == null) {
            flowOf(CompareState(teams))
        } else {
            combine(
                repository.seasons(a),
                repository.seasons(b),
                repository.headToHead(a, b),
            ) { sa, sb, h2h ->
                CompareState(teams, teams.find { it.abbr == a }, teams.find { it.abbr == b }, sa, sb, h2h)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareState())

    fun selectA(abbr: String) { handle[KEY_A] = abbr }
    fun selectB(abbr: String) { handle[KEY_B] = abbr }

    companion object {
        const val KEY_A = "a"
        const val KEY_B = "b"
    }
}
