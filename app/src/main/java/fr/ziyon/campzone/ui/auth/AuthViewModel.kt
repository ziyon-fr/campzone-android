package fr.ziyon.campzone.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.analytics.AnalyticsService
import fr.ziyon.campzone.data.analytics.NoOpAnalyticsService
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
    val isSigningInWithEmail: Boolean = false,
    val isSendingPasswordReset: Boolean = false,
    val isCompletingOnboarding: Boolean = false,
    val emailResetMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authSessionRepository: AuthSessionRepository,
    private val analyticsService: AnalyticsService = NoOpAnalyticsService,
) : ViewModel() {
    val authState: StateFlow<AuthState> = authSessionRepository.authState

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signInWithGoogle(activity: Activity) {
        signIn(
            provider = "google",
            start = { _uiState.value = AuthUiState(isSigningInWithGoogle = true) },
            action = { authSessionRepository.signInWithGoogle(activity) },
        )
    }

    fun signInWithApple(activity: Activity) {
        signIn(
            provider = "apple",
            start = { _uiState.value = AuthUiState(isSigningInWithApple = true) },
            action = { authSessionRepository.signInWithApple(activity) },
        )
    }

    fun signInWithEmail(email: String, password: String) {
        signIn(
            provider = "email",
            start = { _uiState.value = AuthUiState(isSigningInWithEmail = true) },
            action = { authSessionRepository.signInWithEmail(email, password) },
        )
    }

    fun signUpWithEmail(email: String, password: String, displayName: String?) {
        signIn(
            provider = "email",
            start = { _uiState.value = AuthUiState(isSigningInWithEmail = true) },
            action = { authSessionRepository.signUpWithEmail(email, password, displayName) },
        )
    }

    fun sendPasswordReset(email: String) {
        if (_uiState.value.isBusy) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSendingPasswordReset = true,
                emailResetMessage = null,
                errorMessage = null,
            )
            runCatching { authSessionRepository.sendPasswordReset(email) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSendingPasswordReset = false,
                        emailResetMessage = "Password reset email sent to ${email.trim()}.",
                    )
                }
                .onFailure { error ->
                    _uiState.value = AuthUiState(errorMessage = error.friendlyMessage())
                }
        }
    }

    fun signOut() {
        authSessionRepository.signOut()
        analyticsService.signOut()
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

    fun dismissEmailResetMessage() {
        _uiState.value = _uiState.value.copy(emailResetMessage = null)
    }

    private fun signIn(
        provider: String,
        start: () -> Unit,
        action: suspend () -> Unit,
    ) {
        if (_uiState.value.isBusy) return

        viewModelScope.launch {
            start()
            runCatching { action() }
                .onSuccess {
                    analyticsService.signIn(provider)
                    _uiState.value = AuthUiState()
                }
                .onFailure { error ->
                    _uiState.value = AuthUiState(errorMessage = error.friendlyMessage())
                }
        }
    }

    private fun Throwable.friendlyMessage(): String =
        message?.takeUnless { it.isBlank() } ?: "Sign-in failed. Please try again."
}

private val AuthUiState.isBusy: Boolean
    get() = isSigningInWithGoogle ||
        isSigningInWithApple ||
        isSigningInWithEmail ||
        isSendingPasswordReset ||
        isCompletingOnboarding
