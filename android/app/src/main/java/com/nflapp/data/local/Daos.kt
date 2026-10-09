package com.nflapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY kickoffEpochMs, id")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun observe(id: String): Flow<GameEntity?>

    @Query("DELETE FROM games")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<GameEntity>)

    @Transaction
    suspend fun replaceAll(games: List<GameEntity>) {
        clear()
        insertAll(games)
    }
}

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams ORDER BY eloRank")
    fun observeAll(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams WHERE abbr = :abbr")
    fun observe(abbr: String): Flow<TeamEntity?>

    @Query("SELECT * FROM team_seasons WHERE team = :abbr ORDER BY season")
    fun observeSeasons(abbr: String): Flow<List<TeamSeasonEntity>>

    @Query("SELECT * FROM elo_history WHERE team = :abbr ORDER BY season, week")
    fun observeEloHistory(abbr: String): Flow<List<EloPointEntity>>

    @Query("SELECT * FROM head_to_head WHERE (teamA = :a AND teamB = :b) OR (teamA = :b AND teamB = :a)")
    fun observeHeadToHead(a: String, b: String): Flow<HeadToHeadEntity?>

    @Query("DELETE FROM teams") suspend fun clearTeams()
    @Query("DELETE FROM team_seasons") suspend fun clearSeasons()
    @Query("DELETE FROM elo_history") suspend fun clearElo()
    @Query("DELETE FROM head_to_head") suspend fun clearHeadToHead()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTeams(items: List<TeamEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSeasons(items: List<TeamSeasonEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertElo(items: List<EloPointEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHeadToHead(items: List<HeadToHeadEntity>)

    @Transaction
    suspend fun replaceAll(
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
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta WHERE `key` = :key")
    suspend fun get(key: String): SyncMetaEntity?

    @Query("SELECT * FROM sync_meta WHERE `key` = :key")
    fun observe(key: String): Flow<SyncMetaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: SyncMetaEntity)
}
