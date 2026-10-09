package com.nflapp.data

import androidx.room.withTransaction
import com.nflapp.data.local.NflDatabase
import com.nflapp.data.local.SyncMetaEntity
import com.nflapp.data.remote.ModelDto
import com.nflapp.data.remote.NflApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@Serializable
data class WeekInfo(val season: Int, val week: Int, val seasonType: String, val model: String)

data class RefreshResult(val predictionsChanged: Boolean, val statsChanged: Boolean) {
    val anyChanged get() = predictionsChanged || statsChanged
}

/**
 * Single source of truth is Room. The network only feeds the database, the UI
 * observes the database, so the app works fully offline with the last data.
 */
class NflRepository(
    private val api: NflApi,
    private val db: NflDatabase,
    private val json: Json,
    private val predictionsUrl: String,
    private val statsUrl: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val meta = db.syncMeta()

    val games = db.games().observeAll()
    fun game(id: String) = db.games().observe(id)
    val teams = db.teams().observeAll()
    fun team(abbr: String) = db.teams().observe(abbr)
    fun seasons(abbr: String) = db.teams().observeSeasons(abbr)
    fun eloHistory(abbr: String) = db.teams().observeEloHistory(abbr)
    fun headToHead(a: String, b: String) = db.teams().observeHeadToHead(a, b)

    val weekInfo: Flow<WeekInfo?> = meta.observe(KEY_PREDICTIONS)
        .map { m -> m?.payload?.let { json.decodeFromString(WeekInfo.serializer(), it) } }
    val modelInfo: Flow<ModelDto?> = meta.observe(KEY_STATS)
        .map { m -> m?.payload?.let { json.decodeFromString(ModelDto.serializer(), it) } }
    val predictionsMeta: Flow<SyncMetaEntity?> = meta.observe(KEY_PREDICTIONS)
    val statsMeta: Flow<SyncMetaEntity?> = meta.observe(KEY_STATS)

    suspend fun lastCheckedMs(): Long? = meta.get(KEY_PREDICTIONS)?.lastCheckedMs

    /** Conditional GET of both files. Throws [IOException] / [HttpException] on failure. */
    suspend fun refresh(): RefreshResult = RefreshResult(
        predictionsChanged = refreshPredictions(),
        statsChanged = refreshStats(),
    )

    private suspend fun refreshPredictions(): Boolean =
        conditionalFetch(KEY_PREDICTIONS, { api.predictions(predictionsUrl, it) }) { body ->
            Prepared(
                version = body.version,
                payload = json.encodeToString(
                    WeekInfo.serializer(),
                    WeekInfo(body.season, body.week, body.seasonType, body.model),
                ),
                write = { db.games().replaceAll(body.games.map { it.toEntity(body) }) },
            )
        }

    private suspend fun refreshStats(): Boolean =
        conditionalFetch(KEY_STATS, { api.stats(statsUrl, it) }) { body ->
            Prepared(
                version = body.version,
                payload = json.encodeToString(ModelDto.serializer(), body.model),
                write = {
                    val e = body.toEntities()
                    db.teams().replaceAll(e.teams, e.seasons, e.elo, e.headToHead)
                },
            )
        }

    private class Prepared(val version: String, val payload: String, val write: suspend () -> Unit)

    /**
     * Sends If-None-Match with the stored ETag. HTTP 304 -> only the check time
     * is updated, nothing is downloaded or written. Returns whether data changed.
     */
    private suspend fun <T : Any> conditionalFetch(
        key: String,
        request: suspend (etag: String?) -> Response<T>,
        prepare: (T) -> Prepared,
    ): Boolean {
        val old = meta.get(key)
        val response = request(old?.etag)
        val now = clock()
        if (response.code() == 304 && old != null) {
            meta.upsert(old.copy(lastCheckedMs = now))
            return false
        }
        if (!response.isSuccessful) throw HttpException(response)
        val body = response.body() ?: throw IOException("Empty response for $key")
        val prepared = prepare(body)
        val changed = prepared.version != old?.version
        db.withTransaction {
            if (changed) prepared.write()
            meta.upsert(
                SyncMetaEntity(
                    key = key,
                    etag = response.headers()["ETag"],
                    version = prepared.version,
                    lastCheckedMs = now,
                    lastChangedMs = if (changed) now else old?.lastChangedMs ?: now,
                    payload = prepared.payload,
                ),
            )
        }
        return changed
    }

    companion object {
        const val KEY_PREDICTIONS = "predictions"
        const val KEY_STATS = "stats"
    }
}
