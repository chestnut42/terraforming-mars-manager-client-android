package xyz.blockthem.mars.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.blockthem.mars.manager.api.User
import xyz.blockthem.mars.manager.ui.creategame.CreateGameState
import xyz.blockthem.mars.manager.ui.creategame.PlayerSlot

class CreateGameStateTest {
    private val alice = User(id = "a", nickname = "alice")
    private val bob = User(id = "b", nickname = "bob")

    @Test
    fun needsAtLeastOneResolvedOpponent() {
        assertFalse(CreateGameState(slots = emptyList()).canCreate)
        assertFalse(CreateGameState(slots = listOf(PlayerSlot(0, query = "ali"))).canCreate)
        assertTrue(CreateGameState(slots = listOf(PlayerSlot(0, "alice", alice))).canCreate)
    }

    @Test
    fun rejectsDuplicates() {
        val s = CreateGameState(
            slots = listOf(PlayerSlot(0, "alice", alice), PlayerSlot(1, "alice", alice), PlayerSlot(2, "bob", bob)),
        )
        assertEquals(setOf(0, 1), s.duplicateIds)
        assertFalse(s.canCreate)
    }

    @Test
    fun capsOpponentsAtFour() {
        val four = CreateGameState(slots = (0 until 4).map { PlayerSlot(it) })
        assertFalse(four.canAddPlayer)
        assertTrue(CreateGameState().canAddPlayer)
    }
}
