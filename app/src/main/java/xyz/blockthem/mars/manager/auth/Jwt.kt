package xyz.blockthem.mars.manager.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/** Minimal, unverified JWT payload reader. Only used to schedule refreshes. */
object Jwt {
    /** `exp` claim in epoch seconds, or null if the token can't be read. */
    fun expiresAt(token: String): Long? = try {
        val payload = token.split('.').getOrNull(1) ?: return null
        val bytes = Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '='))
        val obj = Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
        obj?.get("exp")?.jsonPrimitive?.longOrNull
    } catch (_: IllegalArgumentException) {
        null
    }

    /** True if the token is still valid for at least [marginSeconds]. */
    fun isFresh(token: String, nowSeconds: Long, marginSeconds: Long = 120): Boolean {
        val exp = expiresAt(token) ?: return false
        return exp - marginSeconds > nowSeconds
    }
}
