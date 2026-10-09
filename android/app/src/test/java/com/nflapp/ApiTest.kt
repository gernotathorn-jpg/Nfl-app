package com.nflapp

import com.nflapp.data.local.Converters
import com.nflapp.data.remote.NflApi
import com.nflapp.data.remote.PastGameDto
import com.nflapp.data.remote.StatsDto
import com.nflapp.data.toEntities
import com.nflapp.data.toEntity
import com.nflapp.util.Format
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ApiTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var server: MockWebServer
    private lateinit var api: NflApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/nfl-app/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NflApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun parsesPredictionsAndSendsNoEtagInitially() = runTest {
        server.enqueue(MockResponse().setBody(PREDICTIONS).setHeader("ETag", "\"abc\""))
        val response = api.predictions(server.url("/nfl-app/predictions.json").toString(), null)
        assertNull(server.takeRequest().getHeader("If-None-Match"))
        assertEquals(200, response.code())
        assertEquals("\"abc\"", response.headers()["ETag"])
        val body = response.body()!!
        assertEquals(6, body.week)
        val game = body.games.single()
        assertEquals("KC", game.home)
        assertEquals(0.62, game.pHomeWin, 1e-9)
        assertEquals(3, game.h2h.games)
        assertEquals("WWLWW", game.form.home)

        val entity = game.toEntity(body)
        assertEquals(1760573700000L, entity.kickoffEpochMs) // 2025-10-16T00:15:00Z
        assertNull(entity.homeScore)
    }

    @Test
    fun conditionalRequestReturns304WithoutBody() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))
        val response = api.predictions(server.url("/nfl-app/predictions.json").toString(), "\"abc\"")
        assertEquals("\"abc\"", server.takeRequest().getHeader("If-None-Match"))
        assertEquals(304, response.code())
        assertNull(response.body())
    }

    @Test
    fun parsesStatsIntoEntities() {
        val stats = json.decodeFromString(StatsDto.serializer(), STATS)
        val e = stats.toEntities()
        assertEquals(1, e.teams.size)
        assertEquals(2, e.seasons.size)
        assertEquals(1, e.elo.size)
        assertEquals(250.5, e.teams.single().perGame.getValue("passYds"), 1e-9)
        assertEquals(1, e.headToHead.single().recent.size)
        assertEquals("logreg", stats.model.used)
    }

    @Test
    fun convertersRoundTrip() {
        val c = Converters()
        val map = mapOf("a" to 1.5)
        assertEquals(map, c.stringToMap(c.mapToString(map)))
        val games = listOf(PastGameDto("g", 2024, 3, "KC", "DEN", 20, 17))
        assertEquals(games, c.stringToPastGames(c.pastGamesToString(games)))
    }

    @Test
    fun formatsSpreadFromFavouriteView() {
        assertEquals("KC -3.5", Format.spread(-3.5, "KC", "DEN"))
        assertEquals("DEN -7", Format.spread(7.0, "KC", "DEN"))
        assertEquals("Pick'em", Format.spread(0.0, "KC", "DEN"))
    }

    private companion object {
        val PREDICTIONS = """
            {"version":"2025-10-14T06:03:11Z","season":2025,"week":6,"seasonType":"REG","model":"elo",
             "games":[{"id":"2025_06_DEN_KC","kickoff":"2025-10-16T00:15:00Z","home":"KC","away":"DEN",
             "pHomeWin":0.62,"spread":-3.5,"h2h":{"games":3,"homeWins":2},"form":{"home":"WWLWW","away":"LWL"},
             "eloHome":1600.1,"eloAway":1500.0,"homeScore":null,"awayScore":null,"stadium":"Arrowhead",
             "somethingNew":true}]}
        """.trimIndent()

        val STATS = """
            {"version":"2025-10-14T06:03:11Z","season":2025,
             "teams":[{"abbr":"KC","name":"Kansas City Chiefs","conference":"AFC","division":"West","color":"#E31837",
               "elo":1600.1,"eloRank":1,"record":{"w":4,"l":1,"t":0},"homeRecord":{"w":2,"l":0,"t":0},
               "awayRecord":{"w":2,"l":1,"t":0},"allTime":{"w":150,"l":100,"t":0},"pointsFor":130,"pointsAgainst":90,
               "form":"WWLWW","streak":"W2","perGame":{"passYds":250.5},
               "seasons":[{"season":2024,"record":{"w":15,"l":2,"t":0},"pointsFor":400,"pointsAgainst":300,"elo":1700.0},
                          {"season":2025,"record":{"w":4,"l":1,"t":0},"pointsFor":130,"pointsAgainst":90,"elo":1600.1}],
               "eloHistory":[{"season":2025,"week":5,"elo":1600.1}]}],
             "headToHead":[{"teamA":"DEN","teamB":"KC","games":3,"winsA":1,"winsB":2,"ties":0,
               "recent":[{"id":"x","season":2025,"week":2,"home":"DEN","away":"KC","homeScore":20,"awayScore":24}]}],
             "model":{"used":"logreg","accuracy":0.64,"brier":0.22,"eloAccuracy":0.65,"eloBrier":0.22}}
        """.trimIndent()
    }
}
