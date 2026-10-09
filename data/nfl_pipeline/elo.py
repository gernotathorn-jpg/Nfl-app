"""Elo ratings with home-field advantage and margin-of-victory multiplier.

Follows the well known FiveThirtyEight NFL parameters: K=20, ~48 Elo points
home-field advantage, MOV multiplier ln(|mov|+1) * 2.2 / (0.001*elo_diff + 2.2)
and regression of one third towards the mean between seasons.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

import pandas as pd

MEAN = 1505.0


@dataclass
class EloModel:
    k: float = 20.0
    hfa: float = 48.0
    revert: float = 1 / 3
    ratings: dict[str, float] = field(default_factory=dict)
    season: int | None = None

    def rating(self, team: str) -> float:
        return self.ratings.get(team, MEAN)

    def start_season(self, season: int) -> None:
        if self.season is not None and season != self.season:
            self.ratings = {t: r + self.revert * (MEAN - r) for t, r in self.ratings.items()}
        self.season = season

    def diff(self, home: str, away: str, neutral: bool = False) -> float:
        return self.rating(home) - self.rating(away) + (0.0 if neutral else self.hfa)

    def p_home(self, home: str, away: str, neutral: bool = False) -> float:
        return expected(self.diff(home, away, neutral))

    def update(self, home: str, away: str, home_score: float, away_score: float, neutral: bool = False) -> None:
        d = self.diff(home, away, neutral)
        p = expected(d)
        result = 1.0 if home_score > away_score else 0.0 if home_score < away_score else 0.5
        mov = abs(home_score - away_score)
        # winner's Elo edge dampens the multiplier (autocorrelation correction)
        winner_diff = d if result >= 0.5 else -d
        mult = math.log(mov + 1) * 2.2 / (winner_diff * 0.001 + 2.2) if mov > 0 else 1.0
        shift = self.k * mult * (result - p)
        self.ratings[home] = self.rating(home) + shift
        self.ratings[away] = self.rating(away) - shift


def expected(diff: float) -> float:
    return 1.0 / (1.0 + 10 ** (-diff / 400.0))


def elo_to_spread(diff: float) -> float:
    """Elo difference -> point spread from the home team's view (negative = home favoured)."""
    return -diff / 25.0


def run_elo(games: pd.DataFrame) -> tuple[pd.DataFrame, EloModel, list[dict]]:
    """Walk through games chronologically.

    Returns per-game pre-game ratings (only ever based on earlier games), the
    final model state and a rating history for charts.
    """
    model = EloModel()
    rows, history = [], []
    for g in games.itertuples(index=False):
        model.start_season(g.season)
        neutral = getattr(g, "location", "Home") == "Neutral"
        rows.append({
            "game_id": g.game_id,
            "elo_home": model.rating(g.home_team),
            "elo_away": model.rating(g.away_team),
            "elo_diff": model.diff(g.home_team, g.away_team, neutral),
            "elo_p_home": model.p_home(g.home_team, g.away_team, neutral),
        })
        if pd.notna(g.home_score) and pd.notna(g.away_score):
            model.update(g.home_team, g.away_team, g.home_score, g.away_score, neutral)
            for team in (g.home_team, g.away_team):
                history.append({"team": team, "season": g.season, "week": g.week, "elo": model.rating(team)})
    return pd.DataFrame(rows), model, history
