"""Entry point: ``python -m nfl_pipeline`` (run from the data/ directory)."""
from __future__ import annotations

import argparse
import logging
from pathlib import Path

from .export import predictions_payload, stats_payload, target_week, write_outputs
from .features import build_features
from .fetch import update_cache
from .model import predict, walk_forward

ROOT = Path(__file__).resolve().parents[2]


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--cache", type=Path, default=ROOT / "data" / "cache")
    ap.add_argument("--docs", type=Path, default=ROOT / "docs")
    ap.add_argument("--season", type=int, help="override the current season (default: from today's date)")
    args = ap.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    log = logging.getLogger("nfl_pipeline")

    games, team_stats = update_cache(args.cache, args.season)
    feats = build_features(games)
    validation = walk_forward(feats)
    model_name = validation.best

    season, week = target_week(games)
    target = feats[(feats["season"] == season) & (feats["week"] == week)]
    pred = predict(feats, target, use=model_name)
    log.info("predicting %s week %s with %s (%d games)", season, week, model_name, len(pred))

    changed = write_outputs(
        args.docs,
        predictions_payload(pred, season, week, model_name),
        stats_payload(games, team_stats, validation, model_name),
    )
    for name, did in changed.items():
        log.info("%s: %s", name, "updated" if did else "unchanged")


if __name__ == "__main__":
    main()
