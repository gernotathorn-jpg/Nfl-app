package com.nflapp.data.remote

import kotlinx.serialization.Serializable

/** Mirrors docs/predictions.json produced by the Python pipeline. */
@Serializable
data class PredictionsDto(
    val version: String,
    val season: Int,
    val week: Int,
    val seasonType: String = "REG",
    val model: String = "",
    val games: List<GameDto>,
)

@Serializable
data class GameDto(
    val id: String,
    val kickoff: String,
    val home: String,
    val away: String,
    val pHomeWin: Double,
    val spread: Double,
    val h2h: H2hDto,
    val form: FormDto,
    val eloHome: Double? = null,
    val eloAway: Double? = null,
    val homeScore: Double? = null,
    val awayScore: Double? = null,
    val stadium: String? = null,
)

@Serializable
data class H2hDto(val games: Int, val homeWins: Int)

@Serializable
data class FormDto(val home: String, val away: String)

/** Mirrors docs/stats.json. */
@Serializable
data class StatsDto(
    val version: String,
    val season: Int,
    val teams: List<TeamDto>,
    val headToHead: List<HeadToHeadDto>,
    val model: ModelDto,
)

@Serializable
data class RecordDto(val w: Int = 0, val l: Int = 0, val t: Int = 0)

@Serializable
data class TeamDto(
    val abbr: String,
    val name: String,
    val conference: String,
    val division: String,
    val color: String,
    val elo: Double,
    val eloRank: Int,
    val record: RecordDto,
    val homeRecord: RecordDto = RecordDto(),
    val awayRecord: RecordDto = RecordDto(),
    val allTime: RecordDto = RecordDto(),
    val pointsFor: Int,
    val pointsAgainst: Int,
    val form: String,
    val streak: String = "",
    val perGame: Map<String, Double> = emptyMap(),
    val seasons: List<TeamSeasonDto>,
    val eloHistory: List<EloPointDto>,
)

@Serializable
data class TeamSeasonDto(
    val season: Int,
    val record: RecordDto,
    val pointsFor: Int,
    val pointsAgainst: Int,
    val elo: Double,
)

@Serializable
data class EloPointDto(val season: Int, val week: Int, val elo: Double)

@Serializable
data class HeadToHeadDto(
    val teamA: String,
    val teamB: String,
    val games: Int,
    val winsA: Int,
    val winsB: Int,
    val ties: Int,
    val recent: List<PastGameDto> = emptyList(),
)

@Serializable
data class PastGameDto(
    val id: String,
    val season: Int,
    val week: Int,
    val home: String,
    val away: String,
    val homeScore: Int,
    val awayScore: Int,
)

@Serializable
data class ModelDto(
    val used: String,
    val accuracy: Double? = null,
    val brier: Double? = null,
    val eloAccuracy: Double? = null,
    val eloBrier: Double? = null,
    val homeBaseline: Double? = null,
    val perSeason: List<SeasonMetricDto> = emptyList(),
)

@Serializable
data class SeasonMetricDto(
    val season: Int,
    val games: Int,
    val accuracy: Double,
    val brier: Double,
    val eloAccuracy: Double,
    val eloBrier: Double,
)
