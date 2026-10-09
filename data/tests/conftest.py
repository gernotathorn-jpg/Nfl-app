import sys
from pathlib import Path

import numpy as np
import pandas as pd
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from nfl_pipeline.fetch import clean_schedules  # noqa: E402
from nfl_pipeline.teams import TEAMS  # noqa: E402


def make_schedule(seasons=range(2010, 2016), weeks=6, open_from=None, seed=0) -> pd.DataFrame:
    """Synthetic league: 32 teams, round-robin-ish pairings, random scores.

    ``open_from=(season, week)`` leaves that week and everything after unplayed.
    """
    rng = np.random.default_rng(seed)
    teams = sorted(TEAMS)
    rows = []
    for season in seasons:
        start = pd.Timestamp(f"{season}-09-08")
        for week in range(1, weeks + 1):
            order = rng.permutation(teams)
            for i in range(0, len(order), 2):
                home, away = order[i], order[i + 1]
                played = open_from is None or (season, week) < open_from
                day = start + pd.Timedelta(days=7 * (week - 1))
                rows.append({
                    "game_id": f"{season}_{week:02d}_{away}_{home}",
                    "season": season, "game_type": "REG", "week": week,
                    "gameday": day.strftime("%Y-%m-%d"), "gametime": "13:00",
                    "home_team": home, "away_team": away,
                    "home_score": float(rng.integers(0, 40)) if played else np.nan,
                    "away_score": float(rng.integers(0, 40)) if played else np.nan,
                    "home_rest": 7.0, "away_rest": 7.0, "div_game": 0, "location": "Home",
                    "spread_line": 0.0, "stadium": "Somewhere",
                })
    return clean_schedules(pd.DataFrame(rows))


@pytest.fixture
def schedule():
    return make_schedule()
