"""Static team metadata and franchise normalisation.

nflverse uses the abbreviation that was valid in the respective season
(e.g. ``OAK`` until 2019, ``LV`` afterwards). For Elo, form and head-to-head we
want one continuous history per franchise, so old codes are mapped to the
current ones.
"""

FRANCHISE_ALIASES = {
    "OAK": "LV",
    "SD": "LAC",
    "STL": "LA",
    "LAR": "LA",
}

# abbr: (name, conference, division, primary color)
TEAMS = {
    "ARI": ("Arizona Cardinals", "NFC", "West", "#97233F"),
    "ATL": ("Atlanta Falcons", "NFC", "South", "#A71930"),
    "BAL": ("Baltimore Ravens", "AFC", "North", "#241773"),
    "BUF": ("Buffalo Bills", "AFC", "East", "#00338D"),
    "CAR": ("Carolina Panthers", "NFC", "South", "#0085CA"),
    "CHI": ("Chicago Bears", "NFC", "North", "#0B162A"),
    "CIN": ("Cincinnati Bengals", "AFC", "North", "#FB4F14"),
    "CLE": ("Cleveland Browns", "AFC", "North", "#311D00"),
    "DAL": ("Dallas Cowboys", "NFC", "East", "#003594"),
    "DEN": ("Denver Broncos", "AFC", "West", "#FB4F14"),
    "DET": ("Detroit Lions", "NFC", "North", "#0076B6"),
    "GB": ("Green Bay Packers", "NFC", "North", "#203731"),
    "HOU": ("Houston Texans", "AFC", "South", "#03202F"),
    "IND": ("Indianapolis Colts", "AFC", "South", "#002C5F"),
    "JAX": ("Jacksonville Jaguars", "AFC", "South", "#006778"),
    "KC": ("Kansas City Chiefs", "AFC", "West", "#E31837"),
    "LA": ("Los Angeles Rams", "NFC", "West", "#003594"),
    "LAC": ("Los Angeles Chargers", "AFC", "West", "#0080C6"),
    "LV": ("Las Vegas Raiders", "AFC", "West", "#000000"),
    "MIA": ("Miami Dolphins", "AFC", "East", "#008E97"),
    "MIN": ("Minnesota Vikings", "NFC", "North", "#4F2683"),
    "NE": ("New England Patriots", "AFC", "East", "#002244"),
    "NO": ("New Orleans Saints", "NFC", "South", "#D3BC8D"),
    "NYG": ("New York Giants", "NFC", "East", "#0B2265"),
    "NYJ": ("New York Jets", "AFC", "East", "#125740"),
    "PHI": ("Philadelphia Eagles", "NFC", "East", "#004C54"),
    "PIT": ("Pittsburgh Steelers", "AFC", "North", "#FFB612"),
    "SEA": ("Seattle Seahawks", "NFC", "West", "#002244"),
    "SF": ("San Francisco 49ers", "NFC", "West", "#AA0000"),
    "TB": ("Tampa Bay Buccaneers", "NFC", "South", "#D50A0A"),
    "TEN": ("Tennessee Titans", "AFC", "South", "#0C2340"),
    "WAS": ("Washington Commanders", "NFC", "East", "#5A1414"),
}


def normalize(abbr: str) -> str:
    return FRANCHISE_ALIASES.get(abbr, abbr)
