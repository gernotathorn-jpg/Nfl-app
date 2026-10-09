package com.nflapp.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nflapp.data.NflRepository
import com.nflapp.data.local.GameEntity
import com.nflapp.data.local.HeadToHeadEntity
import com.nflapp.data.local.TeamEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class GameDetailState(
    val loaded: Boolean = false,
    val game: GameEntity? = null,
    val home: TeamEntity? = null,
    val away: TeamEntity? = null,
    val headToHead: HeadToHeadEntity? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModel(repository: NflRepository, handle: SavedStateHandle) : ViewModel() {
    private val id: String = checkNotNull(handle["id"])

    val state: StateFlow<GameDetailState> = repository.game(id).flatMapLatest { game ->
        if (game == null) {
            flowOf(GameDetailState(loaded = true))
        } else {
            combine(
                repository.team(game.home),
                repository.team(game.away),
                repository.headToHead(game.home, game.away),
            ) { home, away, h2h -> GameDetailState(true, game, home, away, h2h) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameDetailState())
}
