"""Leakage-free feature construction.

Games are processed in chronological order. For every game the features are
computed *before* its own result is added to the running state, so a game's
features only ever depend on strictly earlier games.
"""
from __future__ import annotations

from collections import defaultdict, deque

import numpy as np
import pandas as pd

from .elo import run_elo

FORM_WINDOW = 5
DEFAULT_REST = 7.0

FEATURES = [
    "elo_diff",       # pre-game Elo difference incl. home-field advantage
    "rest_diff",      # home rest days - away rest days
    "form_diff",      # win share last 5 games, home - away
    "pd5_diff",       # avg point differential last 5 games, home - away
    "season_pd_diff", # avg point differential this season so far, home - away
    "h2h_rate",       # smoothed share of previous meetings won by the home team
    "div_game",
]


def is_final(df: pd.DataFrame) -> pd.Series:
    return df["home_score"].notna() & df["away_score"].notna()


def chronological(games: pd.DataFrame) -> pd.DataFrame:
    return games.sort_values(["gameday", "gametime", "game_id"]).reset_index(drop=True)


def _letter(margin: float) -> str:
    return "W" if margin > 0 else "L" if margin < 0 else "T"


def _pair(a: str, b: str) -> tuple[str, str]:
    return (a, b) if a < b else (b, a)


def build_features(games: pd.DataFrame) -> pd.DataFrame:
    """One row per game with model features and display helpers (form, h2h)."""
    games = chronological(games)
    elo_rows, _, _ = run_elo(games)

    recent: dict[str, deque] = defaultdict(lambda: deque(maxlen=FORM_WINDOW))
    season_pd: dict[tuple[int, str], list[float]] = defaultdict(list)
    h2h: dict[tuple[str, str], list[tuple[str, float]]] = defaultdict(list)  # (winner or "", margin)

    rows = []
    for g in games.itertuples(index=False):
        home, away = g.home_team, g.away_team
        rh, ra = recent[home], recent[away]
        meetings = h2h[_pair(home, away)]
        home_wins = sum(1 for w, _ in meetings if w == home)
        ties = sum(1 for w, _ in meetings if w == "")

        def win_share(dq):
            return float(np.mean([1.0 if m > 0 else 0.5 if m == 0 else 0.0 for m in dq])) if dq else 0.5

        def avg(xs):
            return float(np.mean(xs)) if len(xs) else 0.0

        rest_h = g.home_rest if pd.notna(g.home_rest) else DEFAULT_REST
        rest_a = g.away_rest if pd.notna(g.away_rest) else DEFAULT_REST
        rows.append({
            "game_id": g.game_id,
            "rest_diff": float(rest_h - rest_a),
            "form_diff": win_share(rh) - win_share(ra),
            "pd5_diff": avg(rh) - avg(ra),
            "season_pd_diff": avg(season_pd[(g.season, home)]) - avg(season_pd[(g.season, away)]),
            "h2h_rate": (home_wins + 0.5 * ties + 1) / (len(meetings) + 2),
            "h2h_games": len(meetings),
            "h2h_home_wins": home_wins,
            "form_home": "".join(_letter(m) for m in rh),
            "form_away": "".join(_letter(m) for m in ra),
            "div_game": int(g.div_game),
        })

        if pd.notna(g.home_score) and pd.notna(g.away_score):
            margin = float(g.home_score - g.away_score)
            rh.append(margin)
            ra.append(-margin)
            season_pd[(g.season, home)].append(margin)
            season_pd[(g.season, away)].append(-margin)
            winner = home if margin > 0 else away if margin < 0 else ""
            meetings.append((winner, margin))

    feats = pd.DataFrame(rows).merge(elo_rows, on="game_id")
    out = games.merge(feats.drop(columns=["div_game"]), on="game_id")
    out["div_game"] = out["div_game"].astype(int)
    out["margin"] = out["home_score"] - out["away_score"]
    out["home_win"] = np.where(out["margin"] > 0, 1.0, np.where(out["margin"] < 0, 0.0, 0.5))
    out.loc[~is_final(out), "home_win"] = np.nan
    return out
