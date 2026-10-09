package com.nflapp.ui.teams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nflapp.data.format
import com.nflapp.data.local.TeamSeasonEntity
import com.nflapp.ui.NoInsets
import com.nflapp.ui.appViewModel
import com.nflapp.ui.components.EmptyState
import com.nflapp.ui.components.FormRow
import com.nflapp.ui.components.LineChart
import com.nflapp.ui.components.SectionTitle
import com.nflapp.ui.components.TeamBadge
import com.nflapp.ui.game.PER_GAME_LABELS
import com.nflapp.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDetailScreen(onBack: () -> Unit, onGameClick: (String) -> Unit) {
    val vm = appViewModel { app, handle -> TeamDetailViewModel(app.container.repository, handle) }
    val state by vm.state.collectAsStateWithLifecycle()
    val team = state.team

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(team?.name ?: "Team") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.loaded -> Unit
            team == null -> EmptyState("Team nicht gefunden.", modifier = Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                val color = Format.teamColor(team.color)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(team.abbr, color, size = 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("${team.conference} ${team.division}", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${team.record.format()} · Elo ${team.elo.toInt()} (Rang ${team.eloRank})",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Heim ${team.homeRecord.format()} · Auswärts ${team.awayRecord.format()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FormRow(team.form)
                            if (team.streak.isNotEmpty()) {
                                Text("  Serie ${team.streak}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                state.nextGame?.let { g ->
                    SectionTitle("Diese Woche")
                    Card(Modifier.fillMaxWidth().clickable { onGameClick(g.id) }) {
                        val isHome = g.home == team.abbr
                        val p = if (isHome) g.pHomeWin else 1 - g.pHomeWin
                        Column(Modifier.padding(16.dp)) {
                            Text(if (isHome) "vs ${g.away}" else "@ ${g.home}", fontWeight = FontWeight.SemiBold)
                            Text("${Format.kickoff(g.kickoffEpochMs)} · Siegchance ${Format.percent(p)}")
                        }
                    }
                }

                SectionTitle("Elo-Verlauf (letzte Saisons)")
                LineChart(state.eloHistory.map { it.elo.toFloat() }, color)

                if (team.perGame.isNotEmpty()) {
                    SectionTitle("Pro Spiel (aktuelle Saison)")
                    PER_GAME_LABELS.forEach { (key, label, _) ->
                        team.perGame[key]?.let { v ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(label, Modifier.weight(1f))
                                Text(Format.number(v))
                            }
                        }
                    }
                }

                SectionTitle("Saisons seit 2010")
                Text(
                    "Bilanz gesamt: ${team.allTime.format()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                SeasonTable(state.seasons)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SeasonTable(seasons: List<TeamSeasonEntity>) {
    Row(Modifier.fillMaxWidth()) {
        listOf("Saison", "Bilanz", "PF", "PA", "Elo").forEach {
            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
        }
    }
    HorizontalDivider()
    seasons.asReversed().forEach { s ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            listOf(
                s.season.toString(), s.record.format(), s.pointsFor.toString(),
                s.pointsAgainst.toString(), s.elo.toInt().toString(),
            ).forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
            }
        }
    }
}
