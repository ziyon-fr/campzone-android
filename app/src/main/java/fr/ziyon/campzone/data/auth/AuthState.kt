package fr.ziyon.campzone.data.auth

import fr.ziyon.campzone.core.permissions.UserRole

sealed interface AuthState {
    data object SignedOut : AuthState
    data class OnboardingIncomplete(val user: AuthenticatedUser) : AuthState
    data class SignedIn(val user: AuthenticatedUser) : AuthState
}

data class AuthenticatedUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?,
    val role: UserRole,
    val church: String,
    val age: Int?,
    val preferredLanguage: String,
    val gender: UserGender?,
    val onboardingCompleted: Boolean,
    val providerIds: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
) {
    val preferredDisplayName: String
        get() = displayName
            .takeUnless { it.isBlank() }
            ?: email.takeUnless { it.isBlank() }
            ?: "Campzone user"
}
