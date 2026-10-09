import pandas as pd

from conftest import make_schedule
from nfl_pipeline.fetch import (START_SEASON, append_team_stats, current_season, merge_schedules,
                                open_seasons, update_cache)
import datetime as dt


def test_current_season():
    assert current_season(dt.date(2026, 10, 9)) == 2026
    assert current_season(dt.date(2027, 1, 20)) == 2026


def test_open_seasons_only_unfinished_and_new():
    cached = make_schedule(seasons=range(2010, 2013), open_from=(2012, 3))
    assert open_seasons(cached, 2012) == [2012]
    assert open_seasons(cached, 2013) == [2012, 2013]
    done = make_schedule(seasons=range(2010, 2013))
    assert open_seasons(done, 2012) == []


def test_merge_keeps_final_games_and_updates_open_ones():
    cached = make_schedule(seasons=[2012], open_from=(2012, 3))
    fresh = cached.copy()  # source now has every result, and "corrected" old ones
    fresh["home_score"] = 50.0
    fresh["away_score"] = 0.0
    merged = merge_schedules(cached, fresh, [2012])
    assert len(merged) == len(cached)
    final_ids = cached.loc[cached["home_score"].notna(), "game_id"]
    pd.testing.assert_frame_equal(
        merged.set_index("game_id").loc[final_ids].reset_index(),
        cached.set_index("game_id").loc[final_ids].reset_index(),
    )
    assert merged["home_score"].notna().all()
    assert (merged.set_index("game_id").drop(index=final_ids)["home_score"] == 50.0).all()


def test_append_team_stats_only_new_weeks():
    old = pd.DataFrame({"season": [2012, 2012], "week": [1, 2], "team": ["KC", "KC"], "x": [1, 2]})
    new = pd.DataFrame({"season": [2012, 2012, 2012], "week": [1, 2, 3], "team": ["KC"] * 3, "x": [9, 9, 3]})
    out = append_team_stats(old, new)
    assert out["x"].tolist() == [1, 2, 3]


def test_update_cache_initial_then_incremental(tmp_path):
    calls = []
    full = make_schedule(seasons=range(START_SEASON, 2013), open_from=(2012, 3))

    def sched_loader(seasons):
        calls.append(sorted(seasons))
        return full[full["season"].isin(seasons)]

    def stats_loader(season):
        calls.append(("stats", season))
        return pd.DataFrame({"season": [season], "week": [1], "team": ["KC"]})

    update_cache(tmp_path, 2012, sched_loader, stats_loader)
    assert calls[0] == [2010, 2011, 2012]
    assert (tmp_path / "schedules.parquet").exists()

    calls.clear()
    update_cache(tmp_path, 2012, sched_loader, stats_loader)
    assert calls == [[2012], ("stats", 2012)]  # finished seasons are never fetched again
