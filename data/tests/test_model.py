import numpy as np

from conftest import make_schedule
from nfl_pipeline.features import build_features
from nfl_pipeline.model import accuracy, brier, predict, walk_forward


def test_metrics():
    p = np.array([0.9, 0.2, 0.6])
    y = np.array([1.0, 0.0, 0.0])
    assert accuracy(p, y) == 2 / 3
    assert abs(brier(p, y) - (0.01 + 0.04 + 0.36) / 3) < 1e-12


def test_walk_forward_evaluates_only_out_of_sample_seasons():
    feats = build_features(make_schedule(seasons=range(2010, 2016)))
    v = walk_forward(feats)
    assert [r["season"] for r in v.per_season] == [2013, 2014, 2015]
    assert 0 <= v.logreg["brier"] <= 1 and 0 <= v.elo["accuracy"] <= 1
    assert v.best in {"logreg", "elo"}


def test_predict_trains_only_on_games_before_target():
    sched = make_schedule(seasons=range(2010, 2015), open_from=(2014, 4))
    feats = build_features(sched)
    target = feats[(feats["season"] == 2014) & (feats["week"] == 4)]
    for use in ("logreg", "elo"):
        out = predict(feats, target, use=use)
        assert len(out) == 16
        assert out["p_home"].between(0, 1).all()
        assert ((out["spread"] * 2) % 1 == 0).all()
