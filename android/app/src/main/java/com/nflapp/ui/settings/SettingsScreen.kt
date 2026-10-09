package com.nflapp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nflapp.BuildConfig
import com.nflapp.data.local.SyncMetaEntity
import com.nflapp.ui.NoInsets
import com.nflapp.ui.appViewModel
import com.nflapp.ui.components.SectionTitle
import com.nflapp.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val vm = appViewModel { app, _ -> SettingsViewModel(app, app.container.repository, app.container.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
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
        topBar = { TopAppBar(title = { Text("Einstellungen") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionTitle("Automatische Aktualisierung")
            Text(
                "Die App prüft dienstags und donnerstags im Hintergrund, ob neue Daten vorliegen. " +
                    "Unveränderte Daten werden dank ETag nicht erneut geladen.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Nur über WLAN")
                    Text(
                        if (state.wifiOnly) "Hintergrundprüfung nur in unbegrenzten Netzen"
                        else "Hintergrundprüfung in jedem Netz (auch mobil)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = state.wifiOnly, onCheckedChange = vm::setWifiOnly)
            }
            Text(
                "Nächster Prüftermin: ${Format.dateTime(state.nextSlot.toEpochMilli())}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = vm::refreshNow, enabled = !busy) {
                Text(if (busy) "Prüfe …" else "Jetzt prüfen")
            }

            SectionTitle("Datenstand")
            MetaRow("Prognosen", state.predictions)
            MetaRow("Statistiken", state.stats)
            Text("Quelle: ${BuildConfig.DATA_BASE_URL}", style = MaterialTheme.typography.bodySmall)

            state.model?.let { m ->
                SectionTitle("Modellgüte (Walk-forward-Validierung)")
                Text("Verwendetes Modell: ${if (m.used == "logreg") "Logistische Regression" else "Elo"}")
                m.accuracy?.let { Text("Logistische Regression: Trefferquote ${Format.percent(it)}, Brier ${Format.number(m.brier ?: 0.0, 3)}") }
                m.eloAccuracy?.let { Text("Elo: Trefferquote ${Format.percent(it)}, Brier ${Format.number(m.eloBrier ?: 0.0, 3)}") }
                m.homeBaseline?.let { Text("Immer Heimteam: ${Format.percent(it)}", style = MaterialTheme.typography.bodySmall) }
            }

            SectionTitle("Über")
            Text(
                "Daten: nflverse (CC-BY 4.0). Prognosen werden wöchentlich per GitHub Actions berechnet; " +
                    "die App rechnet nichts online und funktioniert vollständig offline.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MetaRow(label: String, meta: SyncMetaEntity?) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label)
        if (meta == null) {
            Text("noch nicht geladen", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                "Version ${meta.version ?: "–"} · geprüft ${Format.dateTime(meta.lastCheckedMs)} · " +
                    "geändert ${Format.dateTime(meta.lastChangedMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
