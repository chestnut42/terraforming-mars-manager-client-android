package xyz.blockthem.mars.manager.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.blockthem.mars.manager.api.Game
import xyz.blockthem.mars.manager.api.GameStatus
import xyz.blockthem.mars.manager.api.MarsApi
import xyz.blockthem.mars.manager.api.userMessage
import xyz.blockthem.mars.manager.ui.common.Loadable

data class GameSections(
    val yourTurn: List<Game>,
    val inProgress: List<Game>,
    val finished: List<Game>,
) {
    val isEmpty get() = yourTurn.isEmpty() && inProgress.isEmpty() && finished.isEmpty()

    companion object {
        fun from(games: List<Game>) = GameSections(
            yourTurn = games.filter { it.status == GameStatus.AWAITS_INPUT },
            inProgress = games.filter { it.status == GameStatus.IN_PROGRESS },
            finished = games.filter { it.status == GameStatus.FINISHED },
        )
    }
}

class GamesViewModel(private val api: MarsApi) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<GameSections>())
    val state: StateFlow<Loadable<GameSections>> = _state.asStateFlow()
    private var job: Job? = null

    fun reload() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val games = api.getGames()
                _state.value = Loadable(GameSections.from(games), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage) }
            }
        }
    }
}
