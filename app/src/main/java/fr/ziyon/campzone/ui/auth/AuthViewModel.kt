package fr.ziyon.campzone.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.auth.AuthSessionRepository
import fr.ziyon.campzone.data.auth.AuthState
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.OnboardingProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isSigningInWithGoogle: Boolean = false,
    val isSigningInWithApple: Boolean = false,
    val isCompletingOnboarding: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authSessionRepository: AuthSessionRepository,
) : ViewModel() {
    val authState: StateFlow<AuthState> = authSessionRepository.authState

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signInWithGoogle(activity: Activity) {
        signIn(
            start = { _uiState.value = AuthUiState(isSigningInWithGoogle = true) },
            action = { authSessionRepository.signInWithGoogle(activity) },
        )
    }

    fun signInWithApple(activity: Activity) {
        signIn(
            start = { _uiState.value = AuthUiState(isSigningInWithApple = true) },
            action = { authSessionRepository.signInWithApple(activity) },
        )
    }

    fun signOut() {
        authSessionRepository.signOut()
        _uiState.value = AuthUiState()
    }

    fun completeOnboarding(user: AuthenticatedUser, profile: OnboardingProfile) {
        if (_uiState.value.isCompletingOnboarding) return

        viewModelScope.launch {
            _uiState.value = AuthUiState(isCompletingOnboarding = true)
            runCatching { authSessionRepository.completeOnboarding(user, profile) }
                .onSuccess { _uiState.value = AuthUiState() }
                .onFailure { error ->
                    _uiState.value = AuthUiState(errorMessage = error.friendlyMessage())
                }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun signIn(
        start: () -> Unit,
        action: suspend () -> Unit,
    ) {
        if (_uiState.value.isSigningInWithGoogle ||
            _uiState.value.isSigningInWithApple ||
            _uiState.value.isCompletingOnboarding
        ) return

        viewModelScope.launch {
            start()
            runCatching { action() }
                .onSuccess { _uiState.value = AuthUiState() }
                .onFailure { error ->
                    _uiState.value = AuthUiState(errorMessage = error.friendlyMessage())
                }
        }
    }

    private fun Throwable.friendlyMessage(): String =
        message?.takeUnless { it.isBlank() } ?: "Sign-in failed. Please try again."
}
