package com.nflapp.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nflapp.data.remote.PastGameDto

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val season: Int,
    val week: Int,
    val kickoffEpochMs: Long,
    val home: String,
    val away: String,
    val pHomeWin: Double,
    val spread: Double,
    val h2hGames: Int,
    val h2hHomeWins: Int,
    val formHome: String,
    val formAway: String,
    val eloHome: Double?,
    val eloAway: Double?,
    val homeScore: Int?,
    val awayScore: Int?,
    val stadium: String?,
)

data class RecordColumns(val w: Int, val l: Int, val t: Int)

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val abbr: String,
    val name: String,
    val conference: String,
    val division: String,
    val color: String,
    val elo: Double,
    val eloRank: Int,
    @Embedded(prefix = "rec_") val record: RecordColumns,
    @Embedded(prefix = "home_") val homeRecord: RecordColumns,
    @Embedded(prefix = "away_") val awayRecord: RecordColumns,
    @Embedded(prefix = "all_") val allTime: RecordColumns,
    val pointsFor: Int,
    val pointsAgainst: Int,
    val form: String,
    val streak: String,
    val perGame: Map<String, Double>,
)

@Entity(tableName = "team_seasons", primaryKeys = ["team", "season"])
data class TeamSeasonEntity(
    val team: String,
    val season: Int,
    @Embedded val record: RecordColumns,
    val pointsFor: Int,
    val pointsAgainst: Int,
    val elo: Double,
)

@Entity(tableName = "elo_history", primaryKeys = ["team", "season", "week"])
data class EloPointEntity(
    val team: String,
    val season: Int,
    val week: Int,
    val elo: Double,
)

@Entity(tableName = "head_to_head", primaryKeys = ["teamA", "teamB"])
data class HeadToHeadEntity(
    val teamA: String,
    val teamB: String,
    val games: Int,
    val winsA: Int,
    val winsB: Int,
    val ties: Int,
    val recent: List<PastGameDto>,
)

/**
 * Sync bookkeeping per remote file: ETag for conditional requests, the data
 * version and an optional JSON payload (season/week, model metrics).
 */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val etag: String?,
    val version: String?,
    val lastCheckedMs: Long,
    val lastChangedMs: Long,
    val payload: String?,
)
