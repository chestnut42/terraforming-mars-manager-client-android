package xyz.blockthem.mars.manager.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.blockthem.mars.manager.api.PlayerColor
import xyz.blockthem.mars.manager.api.User
import xyz.blockthem.mars.manager.api.userMessage
import xyz.blockthem.mars.manager.auth.AuthRepository

/** Nicknames given to brand new accounts by the server ("Player 1A2B..."). */
const val DEFAULT_NICKNAME_PREFIX = "Player "

data class ProfileState(
    val user: User? = null,
    val nickname: String = "",
    val color: PlayerColor = PlayerColor.BLUE,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val isDirty get() = user != null && (nickname.trim() != user.nickname || color != user.color)
    val canSave get() = isDirty && nickname.isNotBlank() && !saving
    val hasDefaultNickname get() = user?.nickname?.startsWith(DEFAULT_NICKNAME_PREFIX) == true
}

class ProfileViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    /** Reloads the profile unless the user has unsaved edits. */
    fun reload() {
        if (_state.value.isDirty || _state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val user = auth.api.getMe()
                auth.updateUser(user)
                _state.update { it.withUser(user).copy(loading = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage) }
            }
        }
    }

    fun setNickname(value: String) = _state.update { it.copy(nickname = value, message = null) }

    fun setColor(value: PlayerColor) = _state.update { it.copy(color = value, message = null) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, message = null) }
            try {
                val user = auth.api.updateMe(s.nickname.trim(), s.color)
                auth.updateUser(user)
                _state.update { it.withUser(user).copy(saving = false, message = "Saved") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.userMessage) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    private fun ProfileState.withUser(user: User) = copy(user = user, nickname = user.nickname, color = user.color)
}
