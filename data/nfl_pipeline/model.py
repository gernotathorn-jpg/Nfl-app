"""Logistic regression on top of the Elo baseline, validated walk-forward by season."""
from __future__ import annotations

import logging
from dataclasses import dataclass

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression, Ridge
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from .elo import elo_to_spread
from .features import FEATURES

log = logging.getLogger(__name__)

MIN_TRAIN_SEASONS = 3


def accuracy(p: np.ndarray, y: np.ndarray) -> float:
    decided = y != 0.5
    return float(np.mean((p[decided] > 0.5) == (y[decided] == 1.0))) if decided.any() else float("nan")


def brier(p: np.ndarray, y: np.ndarray) -> float:
    return float(np.mean((p - y) ** 2))


def _fit(train: pd.DataFrame):
    decided = train[train["home_win"] != 0.5]
    clf = make_pipeline(StandardScaler(), LogisticRegression(C=1.0, max_iter=1000))
    clf.fit(decided[FEATURES], decided["home_win"].astype(int))
    reg = make_pipeline(StandardScaler(), Ridge(alpha=1.0))
    reg.fit(train[FEATURES], train["margin"])
    return clf, reg


@dataclass
class Validation:
    per_season: list[dict]
    logreg: dict
    elo: dict
    home_baseline: float

    @property
    def best(self) -> str:
        return "logreg" if self.logreg["brier"] <= self.elo["brier"] else "elo"


def walk_forward(feats: pd.DataFrame) -> Validation:
    """Train on all seasons < S, evaluate on season S. No future data leaks in."""
    final = feats[feats["home_win"].notna()]
    seasons = sorted(final["season"].unique())
    rows, ps_lr, ps_elo, ys = [], [], [], []
    for s in seasons[MIN_TRAIN_SEASONS:]:
        train, test = final[final["season"] < s], final[final["season"] == s]
        clf, _ = _fit(train)
        p_lr = clf.predict_proba(test[FEATURES])[:, 1]
        p_elo = test["elo_p_home"].to_numpy()
        y = test["home_win"].to_numpy()
        rows.append({
            "season": int(s), "games": int(len(test)),
            "accuracy": round(accuracy(p_lr, y), 4), "brier": round(brier(p_lr, y), 4),
            "eloAccuracy": round(accuracy(p_elo, y), 4), "eloBrier": round(brier(p_elo, y), 4),
        })
        log.info("season %s  n=%3d  logreg acc=%.3f brier=%.4f | elo acc=%.3f brier=%.4f",
                 s, len(test), rows[-1]["accuracy"], rows[-1]["brier"], rows[-1]["eloAccuracy"], rows[-1]["eloBrier"])
        ps_lr.append(p_lr); ps_elo.append(p_elo); ys.append(y)

    if not ys:
        nan = {"accuracy": float("nan"), "brier": float("nan")}
        return Validation([], nan, nan, float("nan"))
    p_lr, p_elo, y = np.concatenate(ps_lr), np.concatenate(ps_elo), np.concatenate(ys)
    v = Validation(
        per_season=rows,
        logreg={"accuracy": round(accuracy(p_lr, y), 4), "brier": round(brier(p_lr, y), 4)},
        elo={"accuracy": round(accuracy(p_elo, y), 4), "brier": round(brier(p_elo, y), 4)},
        home_baseline=round(accuracy(np.ones_like(y), y), 4),
    )
    log.info("overall  logreg acc=%.3f brier=%.4f | elo acc=%.3f brier=%.4f | always-home acc=%.3f",
             v.logreg["accuracy"], v.logreg["brier"], v.elo["accuracy"], v.elo["brier"], v.home_baseline)
    return v


def predict(feats: pd.DataFrame, target: pd.DataFrame, use: str = "logreg") -> pd.DataFrame:
    """Predict ``target`` games using only final games that kicked off before the earliest target game."""
    cutoff = target["gameday"].min()
    train = feats[feats["home_win"].notna() & (feats["gameday"] < cutoff)]
    out = target.copy()
    if use == "logreg" and train["season"].nunique() >= MIN_TRAIN_SEASONS:
        clf, reg = _fit(train)
        out["p_home"] = clf.predict_proba(target[FEATURES])[:, 1]
        out["spread"] = -reg.predict(target[FEATURES])
    else:
        out["p_home"] = target["elo_p_home"]
        out["spread"] = target["elo_diff"].map(elo_to_spread)
    out["spread"] = (out["spread"] * 2).round() / 2  # half points, like a betting line
    return out
