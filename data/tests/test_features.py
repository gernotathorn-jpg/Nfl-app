import numpy as np
import pandas as pd

from conftest import make_schedule
from nfl_pipeline.elo import EloModel, expected
from nfl_pipeline.features import FEATURES, build_features


def _game(gid, day, home, away, hs, as_, season=2020, week=1, rest=(7, 7)):
    return {
        "game_id": gid, "season": season, "game_type": "REG", "week": week, "gameday": day,
        "gametime": "13:00", "home_team": home, "away_team": away, "home_score": hs, "away_score": as_,
        "home_rest": rest[0], "away_rest": rest[1], "div_game": 1, "location": "Home",
        "spread_line": 0.0, "stadium": None,
    }


def test_form_h2h_and_rest_use_only_prior_games():
    games = pd.DataFrame([
        _game("g1", "2020-09-10", "KC", "BUF", 30, 10),
        _game("g2", "2020-09-17", "BUF", "KC", 20, 21, week=2),
        _game("g3", "2020-09-24", "KC", "BUF", np.nan, np.nan, week=3, rest=(10, 6)),
    ])
    f = build_features(games).set_index("game_id")

    # first meeting: no history at all
    assert f.loc["g1", "h2h_games"] == 0
    assert f.loc["g1", "form_home"] == "" and f.loc["g1", "form_away"] == ""
    assert f.loc["g1", "form_diff"] == 0

    # second game sees exactly the first one, not itself
    assert f.loc["g2", "h2h_games"] == 1
    assert f.loc["g2", "h2h_home_wins"] == 0          # BUF is home and lost g1
    assert f.loc["g2", "form_home"] == "L" and f.loc["g2", "form_away"] == "W"
    assert f.loc["g2", "pd5_diff"] == -20 - 20

    # upcoming game: both results, no label
    assert f.loc["g3", "h2h_games"] == 2 and f.loc["g3", "h2h_home_wins"] == 2
    assert f.loc["g3", "form_home"] == "WW" and f.loc["g3", "form_away"] == "LL"
    assert f.loc["g3", "rest_diff"] == 4
    assert np.isnan(f.loc["g3", "home_win"])
    assert f.loc["g1", "home_win"] == 1.0 and f.loc["g2", "home_win"] == 0.0


def test_form_window_is_five_games():
    rows = [_game(f"g{i}", f"2020-09-{10 + i:02d}", "KC", "DEN", 20, 10, week=i + 1) for i in range(7)]
    f = build_features(pd.DataFrame(rows)).set_index("game_id")
    assert f.loc["g6", "form_home"] == "WWWWW"


def test_no_leakage_features_independent_of_future_results():
    sched = make_schedule(seasons=range(2018, 2021))
    base = build_features(sched).set_index("game_id")

    # wildly change every result of the last season: earlier features must not move
    changed = sched.copy()
    last = changed["season"] == 2020
    changed.loc[last, "home_score"] = 99.0
    changed.loc[last, "away_score"] = 0.0
    other = build_features(changed).set_index("game_id")

    first_2020 = sched.loc[last, "gameday"].min()
    earlier = sched.loc[sched["gameday"] <= first_2020, "game_id"]
    pd.testing.assert_frame_equal(base.loc[earlier, FEATURES], other.loc[earlier, FEATURES])


def test_features_complete_and_finite(schedule):
    f = build_features(schedule)
    assert len(f) == len(schedule)
    assert np.isfinite(f[FEATURES].to_numpy()).all()
    assert f["h2h_rate"].between(0, 1).all()


def test_elo_home_advantage_and_zero_sum():
    m = EloModel()
    assert m.p_home("A", "B") > 0.5
    assert m.p_home("A", "B", neutral=True) == 0.5
    m.update("A", "B", 31, 3)
    assert m.rating("A") > 1505 > m.rating("B")
    assert abs(m.rating("A") + m.rating("B") - 2 * 1505) < 1e-9
    big = EloModel(); big.update("A", "B", 40, 0)
    small = EloModel(); small.update("A", "B", 14, 13)
    assert big.rating("A") > small.rating("A")          # margin of victory matters
    assert expected(0) == 0.5


def test_elo_reverts_between_seasons():
    m = EloModel()
    m.start_season(2020)
    m.ratings["A"] = 1700
    m.start_season(2021)
    assert 1505 < m.rating("A") < 1700
