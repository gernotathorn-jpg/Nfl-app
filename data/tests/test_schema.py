import json

import jsonschema
import pytest

from conftest import make_schedule
from nfl_pipeline.export import (kickoff_utc, predictions_payload, stats_payload, target_week, write_json,
                                 write_outputs)
from nfl_pipeline.features import build_features
from nfl_pipeline.model import predict, walk_forward
from nfl_pipeline.schema import PREDICTIONS_SCHEMA, STATS_SCHEMA
import pandas as pd


@pytest.fixture(scope="module")
def payloads():
    sched = make_schedule(seasons=range(2010, 2015), open_from=(2014, 4))
    feats = build_features(sched)
    v = walk_forward(feats)
    season, week = target_week(sched)
    pred = predict(feats, feats[(feats["season"] == season) & (feats["week"] == week)], use=v.best)
    stats = pd.DataFrame({
        "season": [2014], "week": [1], "season_type": ["REG"], "game_id": ["x"], "team": ["KC"],
        "opponent_team": ["DEN"], "pass_yds": [250.0], "rush_yds": [100.0], "pass_td": [2.0], "rush_td": [1.0],
        "turnovers": [1.0], "takeaways": [2.0], "sacks": [3.0], "sacks_allowed": [1.0], "penalties": [5.0],
        "off_epa": [4.2],
    })
    return predictions_payload(pred, season, week, v.best), stats_payload(sched, stats, v, v.best)


def test_target_week_is_first_open_week():
    assert target_week(make_schedule(seasons=[2014], open_from=(2014, 4))) == (2014, 4)
    assert target_week(make_schedule(seasons=[2014], weeks=3)) == (2014, 3)


def test_kickoff_is_utc():
    assert kickoff_utc("2026-10-15", "20:15") == "2026-10-16T00:15:00Z"   # EDT
    assert kickoff_utc("2026-12-06", "13:00") == "2026-12-06T18:00:00Z"   # EST


def test_predictions_schema(payloads):
    pred, _ = payloads
    jsonschema.validate(pred, PREDICTIONS_SCHEMA)
    assert pred["season"] == 2014 and pred["week"] == 4
    assert len(pred["games"]) == 16
    g = pred["games"][0]
    assert set(g) >= {"id", "kickoff", "home", "away", "pHomeWin", "spread", "h2h", "form"}
    assert set(g["h2h"]) == {"games", "homeWins"} and set(g["form"]) == {"home", "away"}
    assert g["h2h"]["homeWins"] <= g["h2h"]["games"]


def test_stats_schema(payloads):
    _, stats = payloads
    jsonschema.validate(stats, STATS_SCHEMA)
    assert len(stats["teams"]) == 32
    assert sorted(t["eloRank"] for t in stats["teams"]) == list(range(1, 33))
    kc = next(t for t in stats["teams"] if t["abbr"] == "KC")
    assert kc["perGame"]["totalYds"] == 350.0
    for h in stats["headToHead"]:
        assert h["winsA"] + h["winsB"] + h["ties"] == h["games"]


def test_schema_rejects_bad_probability(payloads):
    pred = json.loads(json.dumps(payloads[0]))
    pred["games"][0]["pHomeWin"] = 1.5
    with pytest.raises(jsonschema.ValidationError):
        jsonschema.validate(pred, PREDICTIONS_SCHEMA)


def test_unchanged_content_keeps_file_and_version(tmp_path, payloads):
    pred, stats = payloads
    assert write_outputs(tmp_path, pred, stats) == {"predictions.json": True, "stats.json": True}
    before = (tmp_path / "predictions.json").read_text()
    again = {**pred, "version": "2099-01-01T00:00:00Z"}
    assert write_json(tmp_path / "predictions.json", again, PREDICTIONS_SCHEMA) is False
    assert (tmp_path / "predictions.json").read_text() == before
