package com.nflapp.ui.teams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nflapp.data.format
import com.nflapp.data.local.TeamEntity
import com.nflapp.ui.NoInsets
import com.nflapp.ui.appViewModel
import com.nflapp.ui.components.EmptyState
import com.nflapp.ui.components.FormRow
import com.nflapp.ui.components.TeamBadge
import com.nflapp.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamsScreen(onTeamClick: (String) -> Unit) {
    val vm = appViewModel { app, _ -> TeamsViewModel(app.container.repository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { TopAppBar(title = { Text("Teams") }) },
    ) { padding ->
        if (state.loaded && state.teams.isEmpty()) {
            EmptyState("Noch keine Team-Daten gespeichert.", modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    FilterChip(state.sort == TeamSort.ELO, { vm.setSort(TeamSort.ELO) }, { Text("Elo-Ranking") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(state.sort == TeamSort.DIVISION, { vm.setSort(TeamSort.DIVISION) }, { Text("Divisionen") })
                }
            }
            var lastDivision: String? = null
            state.teams.forEach { team ->
                val division = "${team.conference} ${team.division}"
                if (state.sort == TeamSort.DIVISION && division != lastDivision) {
                    lastDivision = division
                    item(key = division) {
                        Text(
                            division,
                            Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                item(key = team.abbr) { TeamRow(team, showRank = state.sort == TeamSort.ELO) { onTeamClick(team.abbr) } }
            }
        }
    }
}

@Composable
private fun TeamRow(team: TeamEntity, showRank: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showRank) {
            Text("${team.eloRank}.", Modifier.width(32.dp), style = MaterialTheme.typography.labelLarge)
        }
        TeamBadge(team.abbr, Format.teamColor(team.color), size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(team.name, fontWeight = FontWeight.SemiBold)
            Text(
                "${team.record.format()} · Elo ${team.elo.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FormRow(team.form)
    }
}
