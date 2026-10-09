"""Build docs/predictions.json and docs/stats.json."""
from __future__ import annotations

import datetime as dt
import json
from pathlib import Path
from zoneinfo import ZoneInfo

import jsonschema
import pandas as pd

from .elo import run_elo
from .features import chronological, is_final
from .model import Validation
from .schema import PREDICTIONS_SCHEMA, STATS_SCHEMA
from .teams import TEAMS

EASTERN = ZoneInfo("America/New_York")
H2H_RECENT = 5
ELO_HISTORY_SEASONS = 3


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def kickoff_utc(gameday: str, gametime: str) -> str:
    """nflverse times are US/Eastern local time."""
    local = dt.datetime.strptime(f"{gameday} {gametime}", "%Y-%m-%d %H:%M").replace(tzinfo=EASTERN)
    return local.astimezone(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _num(x):
    return None if pd.isna(x) else float(x)


def target_week(games: pd.DataFrame) -> tuple[int, int]:
    """Next week with an unplayed game in the newest season, else that season's last week."""
    season = int(games["season"].max())
    this = games[games["season"] == season]
    open_ = this[~is_final(this)]
    week = int(open_["week"].min()) if len(open_) else int(this["week"].max())
    return season, week


# ---------------------------------------------------------------- predictions.json

def predictions_payload(pred: pd.DataFrame, season: int, week: int, model_name: str) -> dict:
    pred = chronological(pred)
    return {
        "version": utc_now(),
        "season": season,
        "week": week,
        "seasonType": str(pred["game_type"].iloc[0]) if len(pred) else "REG",
        "model": model_name,
        "games": [
            {
                "id": g.game_id,
                "kickoff": kickoff_utc(g.gameday, g.gametime),
                "home": g.home_team,
                "away": g.away_team,
                "pHomeWin": round(float(g.p_home), 3),
                "spread": float(g.spread),
                "h2h": {"games": int(g.h2h_games), "homeWins": int(g.h2h_home_wins)},
                "form": {"home": g.form_home, "away": g.form_away},
                "eloHome": round(float(g.elo_home), 1),
                "eloAway": round(float(g.elo_away), 1),
                "homeScore": _num(g.home_score),
                "awayScore": _num(g.away_score),
                "stadium": None if pd.isna(g.stadium) else str(g.stadium),
            }
            for g in pred.itertuples(index=False)
        ],
    }


# ---------------------------------------------------------------- stats.json

def _record(margins) -> dict:
    m = list(margins)
    return {"w": sum(x > 0 for x in m), "l": sum(x < 0 for x in m), "t": sum(x == 0 for x in m)}


def _team_games(final: pd.DataFrame) -> pd.DataFrame:
    """Long format: one row per team and game."""
    home = pd.DataFrame({
        "game_id": final["game_id"], "season": final["season"], "week": final["week"],
        "gameday": final["gameday"], "team": final["home_team"], "opp": final["away_team"],
        "pf": final["home_score"], "pa": final["away_score"], "is_home": True,
    })
    away = pd.DataFrame({
        "game_id": final["game_id"], "season": final["season"], "week": final["week"],
        "gameday": final["gameday"], "team": final["away_team"], "opp": final["home_team"],
        "pf": final["away_score"], "pa": final["home_score"], "is_home": False,
    })
    tg = pd.concat([home, away], ignore_index=True)
    tg["margin"] = tg["pf"] - tg["pa"]
    return tg.sort_values(["gameday", "game_id"]).reset_index(drop=True)


def _per_game(stats: pd.DataFrame, season: int) -> dict[str, dict]:
    s = stats[stats["season"] == season]
    if s.empty:
        return {}
    opp = s[["season", "week", "team", "pass_yds", "rush_yds"]].rename(
        columns={"team": "opponent_team", "pass_yds": "opp_pass", "rush_yds": "opp_rush"})
    s = s.merge(opp, on=["season", "week", "opponent_team"], how="left")
    out = {}
    for team, g in s.groupby("team"):
        n = len(g)
        out[team] = {
            "passYds": round(g["pass_yds"].sum() / n, 1),
            "rushYds": round(g["rush_yds"].sum() / n, 1),
            "totalYds": round((g["pass_yds"] + g["rush_yds"]).sum() / n, 1),
            "ydsAllowed": round((g["opp_pass"].fillna(0) + g["opp_rush"].fillna(0)).sum() / n, 1),
            "turnovers": round(g["turnovers"].sum() / n, 2),
            "takeaways": round(g["takeaways"].sum() / n, 2),
            "sacks": round(g["sacks"].sum() / n, 2),
            "sacksAllowed": round(g["sacks_allowed"].sum() / n, 2),
            "penalties": round(g["penalties"].sum() / n, 2),
            "offEpa": round(g["off_epa"].sum() / n, 2),
        }
    return out


def _streak(margins: list[float]) -> str:
    if not margins:
        return ""
    last = "W" if margins[-1] > 0 else "L" if margins[-1] < 0 else "T"
    n = 0
    for m in reversed(margins):
        if ("W" if m > 0 else "L" if m < 0 else "T") != last:
            break
        n += 1
    return f"{last}{n}"


def stats_payload(games: pd.DataFrame, stats: pd.DataFrame, validation: Validation, model_name: str) -> dict:
    games = chronological(games)
    final = games[is_final(games)]
    _, elo_model, history = run_elo(games)
    hist = pd.DataFrame(history)
    tg = _team_games(final)
    season = int(final.loc[final["game_type"] == "REG", "season"].max())
    per_game = _per_game(stats, season)

    elos = {t: elo_model.rating(t) for t in TEAMS}
    rank = {t: i + 1 for i, t in enumerate(sorted(elos, key=elos.get, reverse=True))}

    teams = []
    for abbr, (name, conf, div, color) in TEAMS.items():
        mine = tg[tg["team"] == abbr]
        cur = mine[mine["season"] == season]
        th = hist[hist["team"] == abbr] if len(hist) else hist
        seasons = []
        for s, g in mine.groupby("season"):
            end_elo = th.loc[th["season"] == s, "elo"]
            seasons.append({
                "season": int(s), "record": _record(g["margin"]),
                "pointsFor": int(g["pf"].sum()), "pointsAgainst": int(g["pa"].sum()),
                "elo": round(float(end_elo.iloc[-1]), 1) if len(end_elo) else 1505.0,
            })
        recent_hist = th[th["season"] > season - ELO_HISTORY_SEASONS]
        margins = mine["margin"].tolist()
        teams.append({
            "abbr": abbr, "name": name, "conference": conf, "division": div, "color": color,
            "elo": round(elos[abbr], 1), "eloRank": rank[abbr],
            "record": _record(cur["margin"]),
            "homeRecord": _record(cur.loc[cur["is_home"], "margin"]),
            "awayRecord": _record(cur.loc[~cur["is_home"], "margin"]),
            "allTime": _record(mine["margin"]),
            "pointsFor": int(cur["pf"].sum()), "pointsAgainst": int(cur["pa"].sum()),
            "form": "".join("W" if m > 0 else "L" if m < 0 else "T" for m in margins[-5:]),
            "streak": _streak(margins),
            "perGame": per_game.get(abbr, {}),
            "seasons": seasons,
            "eloHistory": [
                {"season": int(r.season), "week": int(r.week), "elo": round(float(r.elo), 1)}
                for r in recent_hist.itertuples(index=False)
            ],
        })

    h2h = []
    abbrs = sorted(TEAMS)
    for i, a in enumerate(abbrs):
        for b in abbrs[i + 1:]:
            m = final[((final["home_team"] == a) & (final["away_team"] == b))
                      | ((final["home_team"] == b) & (final["away_team"] == a))]
            if m.empty:
                continue
            a_margin = (m["home_score"] - m["away_score"]).where(m["home_team"] == a,
                                                                 m["away_score"] - m["home_score"])
            h2h.append({
                "teamA": a, "teamB": b, "games": int(len(m)),
                "winsA": int((a_margin > 0).sum()), "winsB": int((a_margin < 0).sum()),
                "ties": int((a_margin == 0).sum()),
                "recent": [
                    {"id": r.game_id, "season": int(r.season), "week": int(r.week),
                     "home": r.home_team, "away": r.away_team,
                     "homeScore": int(r.home_score), "awayScore": int(r.away_score)}
                    for r in m.tail(H2H_RECENT)[::-1].itertuples(index=False)
                ],
            })

    return {
        "version": utc_now(),
        "season": season,
        "teams": teams,
        "headToHead": h2h,
        "model": {
            "used": model_name,
            "accuracy": validation.logreg["accuracy"], "brier": validation.logreg["brier"],
            "eloAccuracy": validation.elo["accuracy"], "eloBrier": validation.elo["brier"],
            "homeBaseline": validation.home_baseline,
            "perSeason": validation.per_season,
        },
    }


# ---------------------------------------------------------------- writing

def write_json(path: Path, payload: dict, schema: dict) -> bool:
    """Validate and write. Keeps the previous ``version`` if nothing else changed,
    so unchanged data leads to an identical file (no commit, same ETag)."""
    jsonschema.validate(payload, schema)
    if path.exists():
        old = json.loads(path.read_text())
        if {**old, "version": None} == {**json.loads(json.dumps(payload)), "version": None}:
            return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":"), allow_nan=False) + "\n")
    return True


def write_outputs(docs: Path, predictions: dict, stats: dict) -> dict[str, bool]:
    return {
        "predictions.json": write_json(docs / "predictions.json", predictions, PREDICTIONS_SCHEMA),
        "stats.json": write_json(docs / "stats.json", stats, STATS_SCHEMA),
    }
