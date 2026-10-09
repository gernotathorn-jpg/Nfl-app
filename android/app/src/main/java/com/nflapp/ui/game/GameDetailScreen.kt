package com.nflapp.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nflapp.data.format
import com.nflapp.data.isFinal
import com.nflapp.data.local.GameEntity
import com.nflapp.data.local.HeadToHeadEntity
import com.nflapp.data.local.TeamEntity
import com.nflapp.data.winsOf
import com.nflapp.ui.NoInsets
import com.nflapp.ui.appViewModel
import com.nflapp.ui.components.CompareRow
import com.nflapp.ui.components.EmptyState
import com.nflapp.ui.components.FormRow
import com.nflapp.ui.components.ProbabilityBar
import com.nflapp.ui.components.SectionTitle
import com.nflapp.ui.components.TeamBadge
import com.nflapp.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    onBack: () -> Unit,
    onTeamClick: (String) -> Unit,
    onCompare: (String, String) -> Unit,
) {
    val vm = appViewModel { app, handle -> GameDetailViewModel(app.container.repository, handle) }
    val state by vm.state.collectAsStateWithLifecycle()
    val game = state.game

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(game?.let { "${it.away} @ ${it.home}" } ?: "Spiel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (game != null) {
                        IconButton(onClick = { onCompare(game.away, game.home) }) {
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = "Teams vergleichen")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.loaded -> Unit
            game == null -> EmptyState("Spiel nicht gefunden.", modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                Header(game, state.home, state.away, onTeamClick)
                Prediction(game, state.home, state.away)
                Comparison(game, state.home, state.away)
                HeadToHead(game, state.headToHead)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Header(game: GameEntity, home: TeamEntity?, away: TeamEntity?, onTeamClick: (String) -> Unit) {
    Text(Format.kickoff(game.kickoffEpochMs), style = MaterialTheme.typography.labelLarge)
    game.stadium?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TeamHeader(game.away, away, Modifier.weight(1f), onTeamClick)
        Text(
            if (game.isFinal) "${game.awayScore} : ${game.homeScore}" else "@",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        TeamHeader(game.home, home, Modifier.weight(1f), onTeamClick)
    }
}

@Composable
private fun TeamHeader(abbr: String, team: TeamEntity?, modifier: Modifier, onTeamClick: (String) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { onTeamClick(abbr) }) {
            TeamBadge(abbr, Format.teamColor(team?.color), size = 56.dp)
        }
        Text(team?.name ?: abbr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        team?.let { Text(it.record.format(), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Prediction(game: GameEntity, home: TeamEntity?, away: TeamEntity?) {
    SectionTitle("Prognose")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProbabilityBar(game.pHomeWin, Format.teamColor(away?.color), Format.teamColor(home?.color))
            val fav = if (game.pHomeWin >= 0.5) game.home else game.away
            val p = maxOf(game.pHomeWin, 1 - game.pHomeWin)
            Text("$fav gewinnt mit ${Format.percent(p)} Wahrscheinlichkeit", fontWeight = FontWeight.SemiBold)
            Text("Spread: ${Format.spread(game.spread, game.home, game.away)}")
            if (game.eloHome != null && game.eloAway != null) {
                Text(
                    "Elo: ${game.away} ${game.eloAway.toInt()} · ${game.home} ${game.eloHome.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Comparison(game: GameEntity, home: TeamEntity?, away: TeamEntity?) {
    SectionTitle("Form (letzte 5)")
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(game.away, fontWeight = FontWeight.SemiBold)
            FormRow(game.formAway)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(game.home, fontWeight = FontWeight.SemiBold)
            FormRow(game.formHome)
        }
    }
    if (home == null || away == null) return

    SectionTitle("Saisonvergleich")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            CompareRow(away.abbr, "", home.abbr)
            HorizontalDivider()
            TeamComparisonRows(away, home)
        }
    }
}

/** Shared by game detail and the compare screen. Left = [a], right = [b]. */
@Composable
fun TeamComparisonRows(a: TeamEntity, b: TeamEntity) {
    CompareRow("Bilanz", a.record.format(), b.record.format())
    CompareRow("Elo (Rang)", "${a.elo.toInt()} (${a.eloRank})", "${b.elo.toInt()} (${b.eloRank})", a.elo > b.elo)
    CompareRow("Punkte erzielt", a.pointsFor.toString(), b.pointsFor.toString(), a.pointsFor > b.pointsFor)
    CompareRow("Punkte zugelassen", a.pointsAgainst.toString(), b.pointsAgainst.toString(),
        a.pointsAgainst < b.pointsAgainst)
    CompareRow("Heim / Auswärts", "${a.homeRecord.format()} / ${a.awayRecord.format()}",
        "${b.homeRecord.format()} / ${b.awayRecord.format()}")
    CompareRow("Serie", a.streak, b.streak)
    PER_GAME_LABELS.forEach { (key, label, higherIsBetter) ->
        val x = a.perGame[key]
        val y = b.perGame[key]
        if (x != null && y != null) {
            CompareRow(label, Format.number(x), Format.number(y), if (x == y) null else (x > y) == higherIsBetter)
        }
    }
    CompareRow("Bilanz seit 2010", a.allTime.format(), b.allTime.format())
}

val PER_GAME_LABELS = listOf(
    Triple("totalYds", "Yards / Spiel", true),
    Triple("passYds", "Pass-Yards / Spiel", true),
    Triple("rushYds", "Lauf-Yards / Spiel", true),
    Triple("ydsAllowed", "Yards zugelassen / Spiel", false),
    Triple("offEpa", "Offense-EPA / Spiel", true),
    Triple("turnovers", "Turnover / Spiel", false),
    Triple("takeaways", "Takeaways / Spiel", true),
    Triple("sacks", "Sacks / Spiel", true),
    Triple("sacksAllowed", "Sacks kassiert / Spiel", false),
    Triple("penalties", "Strafen / Spiel", false),
)

@Composable
private fun HeadToHead(game: GameEntity, h2h: HeadToHeadEntity?) {
    SectionTitle("Direkter Vergleich seit 2010")
    if (game.h2hGames == 0 && h2h == null) {
        Text("Keine bisherigen Duelle.")
        return
    }
    val homeWins = h2h?.winsOf(game.home) ?: game.h2hHomeWins
    val awayWins = h2h?.winsOf(game.away) ?: (game.h2hGames - game.h2hHomeWins)
    val total = h2h?.games ?: game.h2hGames
    Text("$total Spiele · ${game.home} $homeWins Siege · ${game.away} $awayWins Siege")
    h2h?.recent?.forEach { g ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("${g.season} W${g.week}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text("${g.away} ${g.awayScore} @ ${g.home} ${g.homeScore}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
