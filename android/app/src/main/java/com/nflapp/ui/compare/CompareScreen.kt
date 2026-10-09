package com.nflapp.ui.compare

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nflapp.data.format
import com.nflapp.data.local.TeamEntity
import com.nflapp.data.winsOf
import com.nflapp.ui.NoInsets
import com.nflapp.ui.appViewModel
import com.nflapp.ui.components.CompareRow
import com.nflapp.ui.components.EmptyState
import com.nflapp.ui.components.LineChart
import com.nflapp.ui.components.SectionTitle
import com.nflapp.ui.game.TeamComparisonRows
import com.nflapp.util.Format

private const val SEASONS_SHOWN = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen() {
    val vm = appViewModel { app, handle -> CompareViewModel(app.container.repository, handle) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { TopAppBar(title = { Text("Historischer Vergleich") }) },
    ) { padding ->
        val a = state.a
        val b = state.b
        if (state.teams.isEmpty() || a == null || b == null) {
            EmptyState("Noch keine Team-Daten gespeichert.", modifier = Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row {
                TeamPicker(state.teams, a, vm::selectA, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                TeamPicker(state.teams, b, vm::selectB, Modifier.weight(1f))
            }

            SectionTitle("Direkter Vergleich seit 2010")
            val h2h = state.headToHead
            if (h2h == null || a.abbr == b.abbr) {
                Text("Keine Duelle seit 2010.")
            } else {
                Text(
                    "${h2h.games} Spiele · ${a.abbr} ${h2h.winsOf(a.abbr)} – ${h2h.winsOf(b.abbr)} ${b.abbr}" +
                        if (h2h.ties > 0) " · ${h2h.ties} Unentschieden" else "",
                    fontWeight = FontWeight.SemiBold,
                )
                h2h.recent.forEach { g ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("${g.season} W${g.week}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${g.away} ${g.awayScore} @ ${g.home} ${g.homeScore}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            SectionTitle("Aktuelle Saison")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    CompareRow("", a.abbr, b.abbr)
                    HorizontalDivider()
                    TeamComparisonRows(a, b)
                }
            }

            SectionTitle("Saisons im Vergleich")
            val colorA = Format.teamColor(a.color)
            val colorB = Format.teamColor(b.color, fallback = MaterialTheme.colorScheme.secondary)
            val seasons = (state.seasonsA.map { it.season } + state.seasonsB.map { it.season }).distinct().sorted()
            val eloA = state.seasonsA.associateBy { it.season }
            val eloB = state.seasonsB.associateBy { it.season }
            Text("Elo am Saisonende: ${a.abbr} vs ${b.abbr}", style = MaterialTheme.typography.bodySmall)
            LineChart(
                values = seasons.mapNotNull { eloA[it]?.elo?.toFloat() },
                color = colorA,
                secondValues = seasons.mapNotNull { eloB[it]?.elo?.toFloat() },
                secondColor = colorB,
            )
            Spacer(Modifier.height(8.dp))
            CompareRow("Saison", a.abbr, b.abbr)
            HorizontalDivider()
            seasons.takeLast(SEASONS_SHOWN).asReversed().forEach { s ->
                val ra = eloA[s]
                val rb = eloB[s]
                CompareRow(
                    s.toString(),
                    ra?.record?.format() ?: "–",
                    rb?.record?.format() ?: "–",
                    if (ra != null && rb != null && ra.record.w != rb.record.w) ra.record.w > rb.record.w else null,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamPicker(
    teams: List<TeamEntity>,
    selected: TeamEntity,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.abbr,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            teams.sortedBy { it.name }.forEach { team ->
                DropdownMenuItem(
                    text = { Text(team.name) },
                    onClick = {
                        onSelect(team.abbr)
                        expanded = false
                    },
                )
            }
        }
    }
}
