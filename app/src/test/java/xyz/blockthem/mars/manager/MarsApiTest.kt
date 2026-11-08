package xyz.blockthem.mars.manager

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import xyz.blockthem.mars.manager.api.ApiException
import xyz.blockthem.mars.manager.api.Board
import xyz.blockthem.mars.manager.api.CreateGameRequest
import xyz.blockthem.mars.manager.api.GameStatus
import xyz.blockthem.mars.manager.api.MarsApi
import xyz.blockthem.mars.manager.api.PlayerColor
import xyz.blockthem.mars.manager.api.TokenProvider
import xyz.blockthem.mars.manager.api.userMessage

class MarsApiTest {
    private lateinit var server: MockWebServer
    private val tokens = FakeTokens()
    private lateinit var api: MarsApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = MarsApi(server.url("/").toString(), tokens)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun decodesGamesWithUnknownAndMissingFields() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"games":[
                  {"playUrl":"https://x/game?id=1","createdAt":"2024-08-19T10:00:00.123Z",
                   "expiresAt":"2024-09-19T10:00:00Z","playersCount":3,"awaitsInput":true,
                   "status":"GAME_STATUS_AWAITS_INPUT","somethingNew":1},
                  {"playUrl":"https://x/game?id=2","playersCount":2,"status":"GAME_STATUS_SOMETHING_ELSE"}
                ]}
                """.trimIndent(),
            ),
        )
        val games = api.getGames()
        assertEquals(2, games.size)
        assertEquals(GameStatus.AWAITS_INPUT, games[0].status)
        assertEquals(3, games[0].playersCount)
        assertEquals(GameStatus.IN_PROGRESS, games[1].status) // unknown enum falls back to default

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/manager/api/v1/me/games", req.path)
        assertEquals("Bearer token-1", req.getHeader("Authorization"))
    }

    @Test
    fun encodesCreateGameRequestLikeTheGateway() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        api.createGame(
            CreateGameRequest(
                players = listOf("alice"),
                board = Board.HELLAS,
                corporateEra = true,
                prelude = false,
                venusNext = true,
                solarPhase = false,
                colonies = true,
            ),
        )
        val req = server.takeRequest()
        assertEquals("/manager/api/v2/game", req.path)
        assertEquals(
            """{"players":["alice"],"board":"HELLAS","corporateEra":true,"prelude":false,""" +
                """"venusNext":true,"solarPhase":false,"colonies":true}""",
            req.body.readUtf8(),
        )
    }

    @Test
    fun updateMeSendsColorName() = runTest {
        server.enqueue(MockResponse().setBody("""{"user":{"id":"google:1","nickname":"neo","color":"PINK","elo":1012}}"""))
        val user = api.updateMe("neo", PlayerColor.PINK)
        assertEquals(PlayerColor.PINK, user.color)
        assertEquals(1012, user.elo)
        assertEquals("""{"nickname":"neo","color":"PINK"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun refreshesTokenOnceOn401() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Failed to parse token: exp"))
        server.enqueue(MockResponse().setBody("""{"users":[{"id":"1","nickname":"a","elo":1000}]}"""))

        val users = api.getLeaderboard()

        assertEquals(1, users.size)
        assertEquals(listOf("token-1"), tokens.rejected)
        assertEquals("Bearer token-1", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer token-2", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun repeated401IsUnauthorized() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            api.getMe()
            fail("expected Unauthorized")
        } catch (_: ApiException.Unauthorized) {
        }
    }

    @Test
    fun parsesGatewayErrorMessage() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"code":6,"message":"already exists","details":[]}"""),
        )
        try {
            api.updateMe("taken", PlayerColor.RED)
            fail("expected Http error")
        } catch (e: ApiException.Http) {
            assertEquals(409, e.status)
            assertEquals("already exists", e.serverMessage)
            assertEquals("That nickname is already taken", e.userMessage)
        }
    }

    @Test
    fun badJsonIsDecodeError() = runTest {
        server.enqueue(MockResponse().setBody("<html>oops</html>"))
        try {
            api.getMe()
            fail("expected Decode error")
        } catch (e: ApiException.Decode) {
            assertTrue(e.message!!.contains("oops"))
        }
    }

    private class FakeTokens : TokenProvider {
        var current = "token-1"
        val rejected = mutableListOf<String>()
        private var n = 1

        override suspend fun token() = current

        override suspend fun refresh(rejected: String): String {
            this.rejected += rejected
            current = "token-${++n}"
            return current
        }
    }
}
