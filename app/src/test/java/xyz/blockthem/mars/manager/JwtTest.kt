package xyz.blockthem.mars.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.blockthem.mars.manager.auth.Jwt
import java.util.Base64

class JwtTest {
    private fun token(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("""{"alg":"RS256"}""".toByteArray()) + "." +
            enc.encodeToString(payload.toByteArray()) + ".sig"
    }

    @Test
    fun readsExpiry() {
        assertEquals(1_700_000_000L, Jwt.expiresAt(token("""{"sub":"1","exp":1700000000}""")))
    }

    @Test
    fun freshness() {
        val t = token("""{"exp":1000}""")
        assertTrue(Jwt.isFresh(t, nowSeconds = 500))
        assertFalse(Jwt.isFresh(t, nowSeconds = 900)) // inside the 120s margin
        assertFalse(Jwt.isFresh(t, nowSeconds = 2000))
    }

    @Test
    fun garbageIsNotFresh() {
        assertNull(Jwt.expiresAt("not-a-jwt"))
        assertNull(Jwt.expiresAt("a.@@@.c"))
        assertFalse(Jwt.isFresh("not-a-jwt", nowSeconds = 0))
    }
}
