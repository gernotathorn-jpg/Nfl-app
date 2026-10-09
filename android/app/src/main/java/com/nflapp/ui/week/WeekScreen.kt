package com.nflapp.ui.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nflapp.data.isFinal
import com.nflapp.data.local.GameEntity
import com.nflapp.data.local.TeamEntity
import com.nflapp.ui.NoInsets
import com.nflapp.ui.appViewModel
import com.nflapp.ui.components.EmptyState
import com.nflapp.ui.components.FormRow
import com.nflapp.ui.components.ProbabilityBar
import com.nflapp.ui.components.TeamBadge
import com.nflapp.util.Format
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(onGameClick: (String) -> Unit) {
    val vm = appViewModel { app, _ -> WeekViewModel(app.container.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val info = state.info
                        Text(if (info != null) "Woche ${info.week}" else "Spielplan")
                        if (info != null) {
                            Text(
                                "Saison ${info.season}" + if (info.seasonType != "REG") " · Playoffs" else "",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                !state.loaded -> Unit
                state.games.isEmpty() && !refreshing -> EmptyState(
                    "Noch keine Daten gespeichert.\nBeim ersten Start wird eine Internetverbindung benötigt.",
                    onRetry = vm::refresh,
                )
                else -> GameList(state, onGameClick)
            }
        }
    }
}

@Composable
private fun GameList(state: WeekUiState, onGameClick: (String) -> Unit) {
    val zone = ZoneId.systemDefault()
    val byDay = state.games.groupBy { Instant.ofEpochMilli(it.kickoffEpochMs).atZone(zone).toLocalDate() }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        byDay.forEach { (_, games) ->
            item(key = "day-${games.first().id}") {
                Text(
                    Format.day(games.first().kickoffEpochMs),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(games, key = { it.id }) { game ->
                GameCard(game, state.teams[game.home], state.teams[game.away]) { onGameClick(game.id) }
            }
        }
        state.lastCheckedMs?.let {
            item {
                Text(
                    "Zuletzt geprüft: ${Format.dateTime(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun GameCard(game: GameEntity, home: TeamEntity?, away: TeamEntity?, onClick: () -> Unit) {
    val homeColor = Format.teamColor(home?.color)
    val awayColor = Format.teamColor(away?.color)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Format.kickoff(game.kickoffEpochMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (game.isFinal) "Final" else Format.spread(game.spread, game.home, game.away),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamSide(game.away, away, awayColor, game.formAway, Modifier.weight(1f), alignEnd = false)
                Text(
                    if (game.isFinal) "${game.awayScore} : ${game.homeScore}" else "@",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                TeamSide(game.home, home, homeColor, game.formHome, Modifier.weight(1f), alignEnd = true)
            }
            Spacer(Modifier.height(12.dp))
            ProbabilityBar(game.pHomeWin, awayColor, homeColor)
        }
    }
}

@Composable
private fun TeamSide(
    abbr: String,
    team: TeamEntity?,
    color: androidx.compose.ui.graphics.Color,
    form: String,
    modifier: Modifier,
    alignEnd: Boolean,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        if (!alignEnd) {
            TeamBadge(abbr, color)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Text(team?.name?.substringAfterLast(' ') ?: abbr, fontWeight = FontWeight.SemiBold)
            FormRow(form)
        }
        if (alignEnd) {
            Spacer(Modifier.width(8.dp))
            TeamBadge(abbr, color)
        }
    }
}
