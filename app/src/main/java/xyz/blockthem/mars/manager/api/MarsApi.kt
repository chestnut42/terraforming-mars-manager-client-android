package xyz.blockthem.mars.manager.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Supplies ID tokens for the `Authorization: Bearer` header. */
interface TokenProvider {
    /** Current token, refreshed if it is about to expire. */
    suspend fun token(): String

    /** Called after the server rejected [rejected]; returns a fresh token. */
    suspend fun refresh(rejected: String): String
}

/** Client for the Terraforming Mars manager API (`/manager/api/...`). */
class MarsApi(
    baseUrl: String,
    private val tokens: TokenProvider,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val baseUrl: HttpUrl = baseUrl.toHttpUrl()

    suspend fun login(): User =
        post<EmptyMessage, UserResponse>("/manager/api/v1/login", EmptyMessage()).user

    suspend fun getMe(): User = get<UserResponse>("/manager/api/v1/me").user

    suspend fun updateMe(nickname: String, color: PlayerColor): User =
        post<UpdateMeRequest, UserResponse>("/manager/api/v1/me", UpdateMeRequest(nickname, color)).user

    suspend fun getGames(): List<Game> = get<GamesResponse>("/manager/api/v1/me/games").games

    suspend fun search(term: String): List<User> =
        post<SearchRequest, UsersResponse>("/manager/api/v1/search", SearchRequest(term)).users

    suspend fun getLeaderboard(): List<User> =
        get<UsersResponse>("/manager/api/v1/leaderboard").users

    /** Registers this device for pushes. An empty token unregisters it. */
    suspend fun updateFcmToken(token: String) {
        post<UpdateDeviceTokenRequest, EmptyMessage>("/manager/api/v1/me/device-token", UpdateDeviceTokenRequest(token))
    }

    suspend fun createGame(request: CreateGameRequest) {
        post<CreateGameRequest, EmptyMessage>("/manager/api/v2/game", request)
    }

    // --- plumbing ---

    private suspend inline fun <reified R> get(path: String): R =
        execute(path, method = "GET", body = null, responseSerializer = serializer<R>())

    private suspend inline fun <reified B, reified R> post(path: String, body: B): R =
        execute(
            path,
            method = "POST",
            body = json.encodeToString(serializer<B>(), body),
            responseSerializer = serializer<R>(),
        )

    private suspend fun <R> execute(
        path: String,
        method: String,
        body: String?,
        responseSerializer: KSerializer<R>,
    ): R {
        var token = tokens.token()
        var (status, text) = send(path, method, body, token)
        if (status == 401) {
            // ID tokens are short lived: get a fresh one and retry once.
            token = tokens.refresh(token)
            val retry = send(path, method, body, token)
            status = retry.first
            text = retry.second
            if (status == 401) throw ApiException.Unauthorized()
        }
        if (status !in 200..299) {
            throw ApiException.Http(status, text, parseServerMessage(text))
        }
        return try {
            json.decodeFromString(responseSerializer, text)
        } catch (e: SerializationException) {
            throw ApiException.Decode(text, e)
        } catch (e: IllegalArgumentException) {
            throw ApiException.Decode(text, e)
        }
    }

    private suspend fun send(path: String, method: String, body: String?, token: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(baseUrl.resolve(path) ?: error("bad path: $path"))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .method(method, body?.toRequestBody(JSON_MEDIA))
            .build()
        return try {
            client.newCall(request).await().use { resp ->
                val text = withContext(Dispatchers.IO) { resp.body?.string().orEmpty() }
                resp.code to text
            }
        } catch (e: IOException) {
            throw ApiException.Network(e)
        }
    }

    private fun parseServerMessage(text: String): String? = try {
        json.decodeFromString(RpcStatus.serializer(), text).message.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        text.takeIf { it.isNotBlank() && it.length < 200 }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            encodeDefaults = true
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
