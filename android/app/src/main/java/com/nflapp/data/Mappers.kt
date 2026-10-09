package com.nflapp.data

import com.nflapp.data.local.EloPointEntity
import com.nflapp.data.local.GameEntity
import com.nflapp.data.local.HeadToHeadEntity
import com.nflapp.data.local.RecordColumns
import com.nflapp.data.local.TeamEntity
import com.nflapp.data.local.TeamSeasonEntity
import com.nflapp.data.remote.GameDto
import com.nflapp.data.remote.PredictionsDto
import com.nflapp.data.remote.RecordDto
import com.nflapp.data.remote.StatsDto
import java.time.Instant

fun GameDto.toEntity(parent: PredictionsDto) = GameEntity(
    id = id,
    season = parent.season,
    week = parent.week,
    kickoffEpochMs = Instant.parse(kickoff).toEpochMilli(),
    home = home,
    away = away,
    pHomeWin = pHomeWin,
    spread = spread,
    h2hGames = h2h.games,
    h2hHomeWins = h2h.homeWins,
    formHome = form.home,
    formAway = form.away,
    eloHome = eloHome,
    eloAway = eloAway,
    homeScore = homeScore?.toInt(),
    awayScore = awayScore?.toInt(),
    stadium = stadium,
)

fun RecordDto.toColumns() = RecordColumns(w, l, t)

data class StatsEntities(
    val teams: List<TeamEntity>,
    val seasons: List<TeamSeasonEntity>,
    val elo: List<EloPointEntity>,
    val headToHead: List<HeadToHeadEntity>,
)

fun StatsDto.toEntities() = StatsEntities(
    teams = teams.map { t ->
        TeamEntity(
            abbr = t.abbr, name = t.name, conference = t.conference, division = t.division,
            color = t.color, elo = t.elo, eloRank = t.eloRank,
            record = t.record.toColumns(), homeRecord = t.homeRecord.toColumns(),
            awayRecord = t.awayRecord.toColumns(), allTime = t.allTime.toColumns(),
            pointsFor = t.pointsFor, pointsAgainst = t.pointsAgainst,
            form = t.form, streak = t.streak, perGame = t.perGame,
        )
    },
    seasons = teams.flatMap { t ->
        t.seasons.map { s ->
            TeamSeasonEntity(t.abbr, s.season, s.record.toColumns(), s.pointsFor, s.pointsAgainst, s.elo)
        }
    },
    elo = teams.flatMap { t -> t.eloHistory.map { EloPointEntity(t.abbr, it.season, it.week, it.elo) } },
    headToHead = headToHead.map {
        HeadToHeadEntity(it.teamA, it.teamB, it.games, it.winsA, it.winsB, it.ties, it.recent)
    },
)

// ---------------------------------------------------------------- UI helpers

val GameEntity.isFinal get() = homeScore != null && awayScore != null
val GameEntity.pAwayWin get() = 1.0 - pHomeWin

fun RecordColumns.format(): String = if (t > 0) "$w-$l-$t" else "$w-$l"

fun HeadToHeadEntity.winsOf(team: String) = if (team == teamA) winsA else winsB
