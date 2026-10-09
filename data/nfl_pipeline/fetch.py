"""Download nflverse data and keep an incremental Parquet cache in data/cache/.

Rules:
* First run: load everything since ``START_SEASON``.
* Later runs: seasons whose games are all final are frozen and never fetched
  again. Only "open" seasons (unplayed games, or a new season that appeared)
  are requested; final games already in the cache are kept as they are and
  only new or still-open games are upserted. Weekly team stats are appended
  only for (season, week) pairs that are not cached yet.
"""
from __future__ import annotations

import datetime as dt
import logging
from pathlib import Path
from typing import Callable, Iterable

import pandas as pd

from .teams import normalize

log = logging.getLogger(__name__)

START_SEASON = 2010

SCHEDULE_COLUMNS = [
    "game_id", "season", "game_type", "week", "gameday", "gametime",
    "home_team", "away_team", "home_score", "away_score",
    "home_rest", "away_rest", "div_game", "location", "spread_line", "stadium",
]

TEAM_STAT_SOURCES = {
    # stat name in cache: columns summed from the nflverse file
    "pass_yds": ["passing_yards"],
    "rush_yds": ["rushing_yards"],
    "pass_td": ["passing_tds"],
    "rush_td": ["rushing_tds"],
    "turnovers": ["passing_interceptions", "sack_fumbles_lost", "rushing_fumbles_lost", "receiving_fumbles_lost"],
    "takeaways": ["def_interceptions", "fumble_recovery_opp"],
    "sacks": ["def_sacks"],
    "sacks_allowed": ["sacks_suffered"],
    "penalties": ["penalties"],
    "off_epa": ["passing_epa", "rushing_epa"],
}
TEAM_STAT_COLUMNS = ["season", "week", "season_type", "game_id", "team", "opponent_team", *TEAM_STAT_SOURCES]

NFLVERSE_GAMES_URL = "https://github.com/nflverse/nflverse-data/releases/download/schedules/games.csv"
NFLVERSE_TEAM_STATS_URL = (
    "https://github.com/nflverse/nflverse-data/releases/download/stats_team/stats_team_week_{season}.parquet"
)


def current_season(today: dt.date | None = None) -> int:
    today = today or dt.date.today()
    # The NFL season label is the year it starts in; Jan/Feb belong to the previous one.
    return today.year if today.month >= 3 else today.year - 1


# ---------------------------------------------------------------- downloaders

def download_schedules(seasons: Iterable[int]) -> pd.DataFrame:
    seasons = sorted(set(seasons))
    try:
        import nfl_data_py as nfl

        df = nfl.import_schedules(seasons)
    except Exception as exc:  # habitatring mirror is plain http and occasionally down
        log.warning("nfl_data_py.import_schedules failed (%s), using nflverse release file", exc)
        df = pd.read_csv(NFLVERSE_GAMES_URL)
        df = df[df["season"].isin(seasons)]
    return clean_schedules(df)


def download_team_stats(season: int) -> pd.DataFrame:
    try:
        raw = pd.read_parquet(NFLVERSE_TEAM_STATS_URL.format(season=season))
    except Exception as exc:  # season not published yet
        log.info("no team stats for %s (%s)", season, exc)
        return pd.DataFrame(columns=TEAM_STAT_COLUMNS)
    return clean_team_stats(raw)


# ---------------------------------------------------------------- cleaning

def clean_schedules(df: pd.DataFrame) -> pd.DataFrame:
    df = df.reindex(columns=SCHEDULE_COLUMNS).copy()
    df["home_team"] = df["home_team"].map(normalize)
    df["away_team"] = df["away_team"].map(normalize)
    df["season"] = df["season"].astype(int)
    df["week"] = df["week"].astype(int)
    for col in ("home_score", "away_score", "home_rest", "away_rest", "spread_line"):
        df[col] = pd.to_numeric(df[col], errors="coerce").astype(float)
    df["div_game"] = pd.to_numeric(df["div_game"], errors="coerce").fillna(0).astype(int)
    df["gametime"] = df["gametime"].fillna("13:00").astype(str)
    df["gameday"] = df["gameday"].astype(str)
    return df.sort_values(["gameday", "gametime", "game_id"]).reset_index(drop=True)


def clean_team_stats(raw: pd.DataFrame) -> pd.DataFrame:
    out = pd.DataFrame({
        "season": raw["season"].astype(int),
        "week": raw["week"].astype(int),
        "season_type": raw.get("season_type", "REG"),
        "game_id": raw.get("game_id"),
        "team": raw["team"].map(normalize),
        "opponent_team": raw["opponent_team"].map(normalize),
    })
    for name, cols in TEAM_STAT_SOURCES.items():
        present = [c for c in cols if c in raw.columns]
        out[name] = raw[present].fillna(0).sum(axis=1).astype(float) if present else 0.0
    return out.reset_index(drop=True)


# ---------------------------------------------------------------- incremental merge

def open_seasons(cached: pd.DataFrame, season_now: int) -> list[int]:
    """Seasons that still have to be requested from the source."""
    unfinished = cached.loc[cached["home_score"].isna(), "season"].unique().tolist()
    newest = int(cached["season"].max()) if len(cached) else START_SEASON - 1
    new = list(range(newest + 1, season_now + 1))
    return sorted(set(int(s) for s in unfinished) | set(new))


def merge_schedules(cached: pd.DataFrame, fresh: pd.DataFrame, seasons: Iterable[int]) -> pd.DataFrame:
    """Upsert ``fresh`` into ``cached`` for the given seasons.

    Final games already in the cache are immutable. Non-final games of the
    refreshed seasons are replaced by the fresh version (results, kickoff
    changes, newly scheduled playoff games).
    """
    seasons = set(seasons)
    in_scope = cached["season"].isin(seasons)
    final_ids = set(cached.loc[in_scope & cached["home_score"].notna(), "game_id"])
    keep = cached[~in_scope | cached["game_id"].isin(final_ids)]
    add = fresh[fresh["season"].isin(seasons) & ~fresh["game_id"].isin(final_ids)]
    merged = pd.concat([keep, add], ignore_index=True)
    return merged.sort_values(["gameday", "gametime", "game_id"]).reset_index(drop=True)


def append_team_stats(cached: pd.DataFrame, fresh: pd.DataFrame) -> pd.DataFrame:
    if fresh.empty:
        return cached
    have = set(zip(cached["season"], cached["week"])) if len(cached) else set()
    mask = [(s, w) not in have for s, w in zip(fresh["season"], fresh["week"])]
    new_rows = fresh[mask]
    if new_rows.empty:
        return cached
    return pd.concat([cached, new_rows], ignore_index=True).sort_values(["season", "week", "team"]).reset_index(drop=True)


# ---------------------------------------------------------------- orchestration

def update_cache(
    cache_dir: Path,
    season_now: int | None = None,
    schedule_loader: Callable[[Iterable[int]], pd.DataFrame] = download_schedules,
    stats_loader: Callable[[int], pd.DataFrame] = download_team_stats,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    cache_dir.mkdir(parents=True, exist_ok=True)
    season_now = season_now or current_season()
    sched_path = cache_dir / "schedules.parquet"
    stats_path = cache_dir / "team_stats.parquet"

    if sched_path.exists():
        cached = pd.read_parquet(sched_path)
        seasons = open_seasons(cached, season_now)
        log.info("schedules: incremental refresh of seasons %s", seasons)
        schedules = merge_schedules(cached, schedule_loader(seasons), seasons) if seasons else cached
    else:
        seasons = list(range(START_SEASON, season_now + 1))
        log.info("schedules: initial load %s-%s", seasons[0], seasons[-1])
        schedules = schedule_loader(seasons)
    if not sched_path.exists() or not schedules.equals(pd.read_parquet(sched_path)):
        schedules.to_parquet(sched_path, index=False)

    if stats_path.exists():
        stats = pd.read_parquet(stats_path)
        first = int(stats["season"].max()) if len(stats) else START_SEASON
    else:
        stats = pd.DataFrame(columns=TEAM_STAT_COLUMNS)
        first = START_SEASON
    before = len(stats)
    for season in range(first, season_now + 1):
        stats = append_team_stats(stats, stats_loader(season))
    if len(stats) != before or not stats_path.exists():
        log.info("team stats: %d new rows", len(stats) - before)
        stats.to_parquet(stats_path, index=False)

    return schedules, stats
