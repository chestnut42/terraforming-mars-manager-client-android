package xyz.blockthem.mars.manager.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire models for the manager API (grpc-gateway JSON: lowerCamelCase fields,
// enums as their proto names). Every field has a default so that omitted or
// unknown values never break decoding.

@Serializable
enum class PlayerColor {
    BLUE, RED, YELLOW, GREEN, BLACK, PURPLE, ORANGE, PINK, BRONZE,
}

@Serializable
enum class GameStatus {
    @SerialName("GAME_STATUS_IN_PROGRESS") IN_PROGRESS,
    @SerialName("GAME_STATUS_AWAITS_INPUT") AWAITS_INPUT,
    @SerialName("GAME_STATUS_FINISHED") FINISHED,
}

@Serializable
enum class Board {
    RANDOM, THARSIS, HELLAS, ELYSIUM,
}

@Serializable
data class User(
    val id: String = "",
    val nickname: String = "",
    val color: PlayerColor = PlayerColor.BLUE,
    val createdAt: String? = null,
    val elo: Int = 0,
)

@Serializable
data class Game(
    val playUrl: String = "",
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val playersCount: Int = 0,
    val awaitsInput: Boolean = false,
    val status: GameStatus = GameStatus.IN_PROGRESS,
)

@Serializable
class EmptyMessage

@Serializable
data class UserResponse(val user: User = User())

@Serializable
data class UsersResponse(val users: List<User> = emptyList())

@Serializable
data class GamesResponse(val games: List<Game> = emptyList())

@Serializable
data class SearchRequest(val search: String)

/** Android sends an FCM token; an empty token turns pushes off. */
@Serializable
data class UpdateDeviceTokenRequest(val fcmToken: String)

@Serializable
data class UpdateMeRequest(val nickname: String, val color: PlayerColor)

@Serializable
data class CreateGameRequest(
    val players: List<String>,
    val board: Board,
    val corporateEra: Boolean,
    val prelude: Boolean,
    val venusNext: Boolean,
    val solarPhase: Boolean,
    val colonies: Boolean,
)

/** Error body produced by grpc-gateway for non-OK statuses. */
@Serializable
data class RpcStatus(val code: Int = 0, val message: String = "")
