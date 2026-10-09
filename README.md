# nfl-app

Android-App mit NFL-Spielplänen, historischen Vergleichen und Siegprognosen.
**Im Betrieb fallen keine LLM-Aufrufe oder Tokens an** – alles ist normaler Code:
eine Python-Pipeline rechnet per GitHub Actions, GitHub Pages liefert statisches
JSON aus, die App liest nur diese Dateien und funktioniert danach komplett offline.

```
nflverse ──(Actions: update.yml)──► data/ Python-Pipeline ──► docs/*.json ──(GitHub Pages)──► Android-App (Room-Cache)
```

| Ordner | Inhalt |
|---|---|
| `data/` | Python-Pipeline (`nfl_pipeline`), Parquet-Cache in `data/cache/`, pytest-Tests |
| `docs/` | Ausgabe für GitHub Pages: `predictions.json`, `stats.json` |
| `android/` | Kotlin-App (Jetpack Compose, Material 3, MVVM, Room, Retrofit/OkHttp, kotlinx.serialization, WorkManager, manuelle DI) |
| `.github/workflows/` | `update.yml` (Daten), `build.yml` (APK) |

## Einrichtung

1. **GitHub Pages** aktivieren: *Settings → Pages → Deploy from a branch → `main` / `/docs`*.
2. **Pages-URL in der App**: in `android/app/build.gradle.kts` ist
   `https://gernotathorn-jpg.github.io/Nfl-app/` eingetragen (Groß-/Kleinschreibung des
   Repo-Namens beachten). Für einen Fork anpassen oder beim Build `-PdataBaseUrl=https://…/`
   bzw. die Umgebungsvariable `DATA_BASE_URL` setzen. Daraus entstehen
   `BuildConfig.PREDICTIONS_URL` und `BuildConfig.STATS_URL`.
3. **Actions-Rechte**: *Settings → Actions → General → Workflow permissions → Read and write*.
4. **Signing-Secrets** für Releases (*Settings → Secrets and variables → Actions*):
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias nfl -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 release.jks   # Ausgabe als KEYSTORE_BASE64 speichern
   ```
5. Release bauen: `git tag v1.0.0 && git push origin v1.0.0` → APK hängt am GitHub Release.

## Daten-Pipeline (`data/`)

```bash
cd data
python3.11 -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt
python -m pytest            # Feature-Bau, Leakage, inkrementeller Cache, JSON-Schema
python -m nfl_pipeline      # aktualisiert data/cache/ und docs/
```

Python 3.11 ist nötig, weil `nfl_data_py` `pandas<2` pinnt.

- **Quelle**: nflverse über `nfl_data_py` (kein API-Key, kein Scraping). Fällt der von
  `nfl_data_py` genutzte Spielplan-Mirror aus, wird dieselbe nflverse-Datei direkt aus den
  nflverse-Releases gelesen. Wöchentliche Team-Stats kommen aus dem nflverse-Release
  `stats_team` (in `nfl_data_py` 0.3.3 nicht enthalten).
- **Cache / inkrementell**: Erstlauf lädt alles ab 2010 nach `data/cache/*.parquet`.
  Danach werden nur Saisons mit offenen Spielen bzw. neue Saisons angefragt; abgeschlossene
  Spiele im Cache bleiben unverändert, Team-Stats werden nur für neue (Saison, Woche) angehängt.
- **Modell**: Elo (K=20, Heimvorteil 48 Punkte, Margin-of-Victory-Multiplikator,
  ⅓-Regression zwischen Saisons) als Baseline plus logistische Regression mit den Features
  Elo-Differenz, Ruhetage-Differenz, Form (letzte 5), Punktedifferenz (letzte 5 / Saison),
  Head-to-Head und Divisionsspiel. Ridge-Regression auf die Punktedifferenz liefert den Spread.
- **Validierung ohne Leakage**: Features eines Spiels hängen nur von früheren Spielen ab
  (per Test abgesichert). Walk-forward: für jede Saison S wird nur auf Saisons < S trainiert.
  Accuracy und Brier Score beider Modelle werden geloggt und in `stats.json → model`
  geschrieben; für die Prognose wird das Modell mit dem besseren Brier Score verwendet.
- **Ausgabe**: `version` ändert sich nur, wenn sich der Inhalt ändert – sonst bleibt die Datei
  byte-identisch (kein Commit, gleicher ETag).

### `docs/predictions.json`

```jsonc
{
  "version": "2026-10-13T06:04:12Z", "season": 2026, "week": 6, "seasonType": "REG", "model": "elo",
  "games": [{
    "id": "2026_06_SEA_DEN", "kickoff": "2026-10-16T00:15:00Z", "home": "DEN", "away": "SEA",
    "pHomeWin": 0.57, "spread": -1.5,            // negativ = Heimteam favorisiert
    "h2h": {"games": 4, "homeWins": 1}, "form": {"home": "WLWWL", "away": "LWWWL"},
    "eloHome": 1532.1, "eloAway": 1510.4, "homeScore": null, "awayScore": null, "stadium": "…"
  }]
}
```

`docs/stats.json` enthält pro Team Elo/Rang, Bilanz (gesamt/heim/auswärts/seit 2010),
Punkte, Form, Serie, Pro-Spiel-Werte, alle Saisons seit 2010 und den Elo-Verlauf, dazu alle
Head-to-Head-Paarungen seit 2010 sowie die Validierungsmetriken.

## GitHub Actions

- **`update.yml`**: Cron Di 06:00 UTC (nach Monday Night Football), Do und So 06:00 UTC
  (Spielplanänderungen) sowie manuell. Pip-Cache, Tests, Pipeline; committet nur, wenn sich
  `docs/` oder `data/cache/` geändert haben. **Keep-alive**: GitHub pausiert Cron-Workflows
  nach 60 Tagen ohne Repo-Aktivität; ist der letzte Commit ≥ 45 Tage alt, aktiviert der
  Workflow sich per API selbst neu (ohne Dummy-Commit).
- **`build.yml`**: Bei Tag `v*` `./gradlew assembleRelease`, signiert mit dem Keystore aus den
  Secrets, APK als Asset am GitHub Release. Bei Änderungen unter `android/` zusätzlich ein
  Debug-Build mit Unit-Tests und Lint (ohne Secrets).

## Android-App (`android/`)

- **Daten nur von der eigenen Pages-URL**, Conditional GET mit `If-None-Match`/ETag. Bei `304`
  wird nichts geladen oder geschrieben. Ergebnis in Room → vollständig offline nutzbar.
- **WorkManager**: Prüfung dienstags und donnerstags (Slot 07:30 UTC, nach dem Pipeline-Lauf).
  Ein periodischer Worker wacht alle 6 h auf und geht nur ins Netz, wenn seit der letzten
  Prüfung ein Slot vergangen ist (holt verpasste Termine nach). Netz in den Einstellungen
  wählbar: nur WLAN (unmetered) oder beliebiges Netz.
- **Screens**: Wochenübersicht (Spiele, Siegwahrscheinlichkeiten, Spread, Form, Pull-to-Refresh),
  Spieldetail (Prognose, Elo, Form, Saisonvergleich, direkter Vergleich), Teams (Elo-Ranking /
  Divisionen), Team-Detail (Elo-Verlauf, Pro-Spiel-Werte, Saisons seit 2010), Historischer
  Vergleich zweier Teams, Einstellungen (Netzwahl, Datenstand, Modellgüte, manuelle Prüfung).

```bash
cd android
./gradlew assembleDebug testDebugUnitTest
```

Daten: [nflverse](https://github.com/nflverse) (CC-BY 4.0).
