package xyz.blockthem.mars.manager.api

import java.io.IOException

sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The server answered with a non-2xx status. */
    class Http(val status: Int, val body: String, val serverMessage: String?) :
        ApiException("HTTP $status: ${serverMessage ?: body}")

    /** The response could not be decoded. */
    class Decode(body: String, cause: Throwable) :
        ApiException("Unexpected response: ${cause.message}\n$body", cause)

    /** No network / connection failure. */
    class Network(cause: IOException) : ApiException("Network error: ${cause.message}", cause)

    /** There is no valid sign-in, the user has to sign in again. */
    class Unauthorized(message: String = "Please sign in again") : ApiException(message)
}

/** A short, human readable description suitable for snackbars/dialogs. */
val Throwable.userMessage: String
    get() = when (this) {
        is ApiException.Http -> when (status) {
            409 -> "That nickname is already taken"
            404 -> serverMessage?.replaceFirstChar { it.uppercase() } ?: "Not found"
            else -> serverMessage?.replaceFirstChar { it.uppercase() } ?: "Server error ($status)"
        }
        is ApiException.Network -> "Can't reach the server. Check your connection."
        else -> message ?: toString()
    }
