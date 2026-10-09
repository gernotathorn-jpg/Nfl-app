package com.nflapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GameDao {
    @Query("SELECT * FROM games ORDER BY kickoffEpochMs, id")
    abstract fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    abstract fun observe(id: String): Flow<GameEntity?>

    @Query("DELETE FROM games")
    abstract suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(games: List<GameEntity>)

    @Transaction
    open suspend fun replaceAll(games: List<GameEntity>) {
        clear()
        insertAll(games)
    }
}

@Dao
abstract class TeamDao {
    @Query("SELECT * FROM teams ORDER BY eloRank")
    abstract fun observeAll(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams WHERE abbr = :abbr")
    abstract fun observe(abbr: String): Flow<TeamEntity?>

    @Query("SELECT * FROM team_seasons WHERE team = :abbr ORDER BY season")
    abstract fun observeSeasons(abbr: String): Flow<List<TeamSeasonEntity>>

    @Query("SELECT * FROM elo_history WHERE team = :abbr ORDER BY season, week")
    abstract fun observeEloHistory(abbr: String): Flow<List<EloPointEntity>>

    @Query("SELECT * FROM head_to_head WHERE (teamA = :a AND teamB = :b) OR (teamA = :b AND teamB = :a)")
    abstract fun observeHeadToHead(a: String, b: String): Flow<HeadToHeadEntity?>

    @Query("DELETE FROM teams") abstract suspend fun clearTeams()
    @Query("DELETE FROM team_seasons") abstract suspend fun clearSeasons()
    @Query("DELETE FROM elo_history") abstract suspend fun clearElo()
    @Query("DELETE FROM head_to_head") abstract suspend fun clearHeadToHead()

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertTeams(items: List<TeamEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertSeasons(items: List<TeamSeasonEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertElo(items: List<EloPointEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertHeadToHead(items: List<HeadToHeadEntity>)

    @Transaction
    open suspend fun replaceAll(
        teams: List<TeamEntity>,
        seasons: List<TeamSeasonEntity>,
        elo: List<EloPointEntity>,
        headToHead: List<HeadToHeadEntity>,
    ) {
        clearTeams(); clearSeasons(); clearElo(); clearHeadToHead()
        insertTeams(teams); insertSeasons(seasons); insertElo(elo); insertHeadToHead(headToHead)
    }
}

@Dao
abstract class SyncMetaDao {
    @Query("SELECT * FROM sync_meta WHERE `key` = :key")
    abstract suspend fun get(key: String): SyncMetaEntity?

    @Query("SELECT * FROM sync_meta WHERE `key` = :key")
    abstract fun observe(key: String): Flow<SyncMetaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(meta: SyncMetaEntity)
}
