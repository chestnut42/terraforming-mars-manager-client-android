package xyz.blockthem.mars.manager.ui.creategame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.blockthem.mars.manager.api.Board
import xyz.blockthem.mars.manager.api.CreateGameRequest
import xyz.blockthem.mars.manager.api.MarsApi
import xyz.blockthem.mars.manager.api.User
import xyz.blockthem.mars.manager.api.userMessage

/** One opponent slot. [selected] is set only once a search result was picked. */
data class PlayerSlot(
    val id: Int,
    val query: String = "",
    val selected: User? = null,
    val suggestions: List<User> = emptyList(),
)

data class GameOptions(
    val board: Board = Board.RANDOM,
    val corporateEra: Boolean = true,
    val prelude: Boolean = true,
    val venusNext: Boolean = true,
    val solarPhase: Boolean = true,
    val colonies: Boolean = true,
)

data class CreateGameState(
    val slots: List<PlayerSlot> = listOf(PlayerSlot(id = 0)),
    val options: GameOptions = GameOptions(),
    val creating: Boolean = false,
    val created: Boolean = false,
    val error: String? = null,
) {
    /** The server adds the current user, so up to 4 opponents fit a 5 player game. */
    val canAddPlayer get() = slots.size < MAX_OPPONENTS

    val duplicateIds: Set<Int>
        get() = slots.filter { it.selected != null }
            .groupBy { it.selected!!.id }
            .filterValues { it.size > 1 }
            .values.flatten().map { it.id }.toSet()

    val canCreate
        get() = !creating && slots.isNotEmpty() && slots.all { it.selected != null } && duplicateIds.isEmpty()

    companion object {
        const val MAX_OPPONENTS = 4
    }
}

class CreateGameViewModel(private val api: MarsApi) : ViewModel() {
    private val _state = MutableStateFlow(CreateGameState())
    val state: StateFlow<CreateGameState> = _state.asStateFlow()

    private var nextSlotId = 1
    private val searchJobs = mutableMapOf<Int, Job>()

    fun addPlayer() = _state.update {
        if (it.canAddPlayer) it.copy(slots = it.slots + PlayerSlot(id = nextSlotId++)) else it
    }

    fun removePlayer(slotId: Int) {
        searchJobs.remove(slotId)?.cancel()
        _state.update { s -> s.copy(slots = s.slots.filterNot { it.id == slotId }) }
    }

    fun onQueryChange(slotId: Int, query: String) {
        updateSlot(slotId) { slot ->
            // Typing exactly a suggested nickname selects it, like the iOS app.
            val match = slot.suggestions.firstOrNull { it.nickname == query }
            slot.copy(query = query, selected = match)
        }
        search(slotId, query, debounceMs = 300)
    }

    /** Called when a slot's field gains focus, to show suggestions right away. */
    fun onFocus(slotId: Int) {
        val slot = _state.value.slots.firstOrNull { it.id == slotId } ?: return
        if (slot.suggestions.isEmpty()) search(slotId, slot.query, debounceMs = 0)
    }

    fun select(slotId: Int, user: User) {
        searchJobs.remove(slotId)?.cancel()
        updateSlot(slotId) { it.copy(query = user.nickname, selected = user, suggestions = emptyList()) }
    }

    fun updateOptions(transform: (GameOptions) -> GameOptions) =
        _state.update { it.copy(options = transform(it.options)) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun create() {
        val s = _state.value
        if (!s.canCreate) return
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = null) }
            try {
                api.createGame(
                    CreateGameRequest(
                        players = s.slots.map { it.selected!!.nickname },
                        board = s.options.board,
                        corporateEra = s.options.corporateEra,
                        prelude = s.options.prelude,
                        venusNext = s.options.venusNext,
                        solarPhase = s.options.solarPhase,
                        colonies = s.options.colonies,
                    ),
                )
                _state.update { it.copy(creating = false, created = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(creating = false, error = e.userMessage) }
            }
        }
    }

    private fun search(slotId: Int, query: String, debounceMs: Long) {
        searchJobs.remove(slotId)?.cancel()
        searchJobs[slotId] = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            try {
                val users = api.search(query.trim())
                updateSlot(slotId) { slot ->
                    // Drop stale responses.
                    if (slot.query != query) return@updateSlot slot
                    slot.copy(
                        suggestions = users,
                        selected = slot.selected ?: users.firstOrNull { it.nickname == query },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Suggestions are best effort; errors surface when creating the game.
            }
        }
    }

    private fun updateSlot(slotId: Int, transform: (PlayerSlot) -> PlayerSlot) = _state.update { s ->
        s.copy(slots = s.slots.map { if (it.id == slotId) transform(it) else it })
    }
}
