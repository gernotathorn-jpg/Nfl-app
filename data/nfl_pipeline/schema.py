"""JSON schemas for the files consumed by the Android app (docs/*.json)."""

_ISO = {"type": "string", "pattern": r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$"}
_TEAM = {"type": "string", "pattern": "^[A-Z]{2,3}$"}
_FORM = {"type": "string", "pattern": "^[WLT]{0,5}$"}
_RECORD = {
    "type": "object",
    "required": ["w", "l", "t"],
    "properties": {k: {"type": "integer", "minimum": 0} for k in ("w", "l", "t")},
}
_NUM_OR_NULL = {"type": ["number", "null"]}

PREDICTIONS_SCHEMA = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object",
    "required": ["version", "season", "week", "games"],
    "properties": {
        "version": _ISO,
        "season": {"type": "integer", "minimum": 2010},
        "week": {"type": "integer", "minimum": 1, "maximum": 23},
        "seasonType": {"type": "string"},
        "model": {"type": "string"},
        "games": {
            "type": "array",
            "items": {
                "type": "object",
                "required": ["id", "kickoff", "home", "away", "pHomeWin", "spread", "h2h", "form"],
                "properties": {
                    "id": {"type": "string"},
                    "kickoff": _ISO,
                    "home": _TEAM,
                    "away": _TEAM,
                    "pHomeWin": {"type": "number", "minimum": 0, "maximum": 1},
                    "spread": {"type": "number"},
                    "h2h": {
                        "type": "object",
                        "required": ["games", "homeWins"],
                        "properties": {
                            "games": {"type": "integer", "minimum": 0},
                            "homeWins": {"type": "integer", "minimum": 0},
                        },
                    },
                    "form": {
                        "type": "object",
                        "required": ["home", "away"],
                        "properties": {"home": _FORM, "away": _FORM},
                    },
                    "eloHome": {"type": "number"},
                    "eloAway": {"type": "number"},
                    "homeScore": _NUM_OR_NULL,
                    "awayScore": _NUM_OR_NULL,
                    "stadium": {"type": ["string", "null"]},
                },
            },
        },
    },
}

STATS_SCHEMA = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object",
    "required": ["version", "season", "teams", "headToHead", "model"],
    "properties": {
        "version": _ISO,
        "season": {"type": "integer"},
        "teams": {
            "type": "array",
            "minItems": 1,
            "items": {
                "type": "object",
                "required": ["abbr", "name", "conference", "division", "color", "elo", "eloRank",
                             "record", "pointsFor", "pointsAgainst", "form", "seasons", "eloHistory"],
                "properties": {
                    "abbr": _TEAM,
                    "name": {"type": "string"},
                    "conference": {"enum": ["AFC", "NFC"]},
                    "division": {"type": "string"},
                    "color": {"type": "string", "pattern": "^#[0-9A-Fa-f]{6}$"},
                    "elo": {"type": "number"},
                    "eloRank": {"type": "integer", "minimum": 1},
                    "record": _RECORD,
                    "homeRecord": _RECORD,
                    "awayRecord": _RECORD,
                    "allTime": _RECORD,
                    "pointsFor": {"type": "integer"},
                    "pointsAgainst": {"type": "integer"},
                    "form": _FORM,
                    "streak": {"type": "string"},
                    "perGame": {"type": "object", "additionalProperties": {"type": "number"}},
                    "seasons": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "required": ["season", "record", "pointsFor", "pointsAgainst", "elo"],
                            "properties": {
                                "season": {"type": "integer"},
                                "record": _RECORD,
                                "pointsFor": {"type": "integer"},
                                "pointsAgainst": {"type": "integer"},
                                "elo": {"type": "number"},
                            },
                        },
                    },
                    "eloHistory": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "required": ["season", "week", "elo"],
                            "properties": {
                                "season": {"type": "integer"},
                                "week": {"type": "integer"},
                                "elo": {"type": "number"},
                            },
                        },
                    },
                },
            },
        },
        "headToHead": {
            "type": "array",
            "items": {
                "type": "object",
                "required": ["teamA", "teamB", "games", "winsA", "winsB", "ties", "recent"],
                "properties": {
                    "teamA": _TEAM,
                    "teamB": _TEAM,
                    "games": {"type": "integer"},
                    "winsA": {"type": "integer"},
                    "winsB": {"type": "integer"},
                    "ties": {"type": "integer"},
                    "recent": {"type": "array"},
                },
            },
        },
        "model": {
            "type": "object",
            "required": ["used", "accuracy", "brier", "eloAccuracy", "eloBrier"],
        },
    },
}
