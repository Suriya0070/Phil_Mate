package com.jsf.app.medication_solution.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jsf.app.medication_solution.data.model.User
import com.jsf.app.medication_solution.data.model.UserRole
import com.jsf.app.medication_solution.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val linkResult: String? = null
)

class AuthViewModel : ViewModel() {
    private val repo = AuthRepository()

    private val _state = MutableStateFlow(AuthUiState(isLoggedIn = repo.isLoggedIn))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        if (repo.isLoggedIn) loadCurrentUser()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            repo.currentUserId?.let { uid ->
                repo.getUserById(uid).onSuccess { user ->
                    _state.value = _state.value.copy(user = user, isLoggedIn = true)
                }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repo.login(email, password)
                .onSuccess { user ->
                    _state.value = _state.value.copy(isLoading = false, user = user, isLoggedIn = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Login failed")
                }
        }
    }

    fun register(name: String, email: String, password: String, role: UserRole, phone: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repo.register(name, email, password, role, phone)
                .onSuccess { user ->
                    _state.value = _state.value.copy(isLoading = false, user = user, isLoggedIn = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Registration failed")
                }
        }
    }

    fun linkSenior(seniorEmail: String) {
        val caregiverId = repo.currentUserId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repo.linkCaregiverToSenior(caregiverId, seniorEmail)
                .onSuccess { _ ->
                    loadCurrentUser()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        linkResult = "Successfully linked! You can now monitor their medications."
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Could not find that senior account")
                }
        }
    }

    fun logout() {
        repo.logout()
        _state.value = AuthUiState(isLoggedIn = false)
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
    fun clearLinkResult() { _state.value = _state.value.copy(linkResult = null) }
}
