package xyz.blockthem.mars.manager.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.blockthem.mars.manager.api.MarsApi
import xyz.blockthem.mars.manager.api.User
import xyz.blockthem.mars.manager.api.userMessage
import xyz.blockthem.mars.manager.ui.common.Loadable

class LeaderboardViewModel(private val api: MarsApi) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<List<User>>())
    val state: StateFlow<Loadable<List<User>>> = _state.asStateFlow()
    private var job: Job? = null

    fun reload() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                _state.value = Loadable(api.getLeaderboard(), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage) }
            }
        }
    }
}
