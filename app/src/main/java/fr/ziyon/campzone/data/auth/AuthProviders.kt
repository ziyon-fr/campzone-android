package fr.ziyon.campzone.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ziyon.campzone.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthProviders @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) {
    suspend fun googleSignInCredential(activity: Activity): AuthCredential {
        val webClientId = appContext.getString(R.string.firebase_google_web_client_id)
        check(webClientId.isNotBlank()) {
            "firebase_google_web_client_id is empty - paste the Web client ID from Firebase console"
        }
        val credentialManager = CredentialManager.create(activity)
        val response = runCatching {
            credentialManager.getCredential(activity, signInWithGoogleRequest(webClientId))
        }.recoverCatching { error ->
            if (error is NoCredentialException) {
                credentialManager.getCredential(activity, googleIdRequest(webClientId))
            } else {
                throw error
            }
        }.getOrElse { error ->
            throw error.toGoogleAuthException()
        }
        val credential = response.credential
        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "Unexpected credential type: ${credential.type}" }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return GoogleAuthProvider.getCredential(idToken, null)
    }

    fun appleOAuthProvider(): OAuthProvider =
        OAuthProvider.newBuilder("apple.com")
            .setScopes(listOf("email", "name"))
            .build()

    private fun signInWithGoogleRequest(webClientId: String): GetCredentialRequest {
        val googleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        return GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()
    }

    private fun googleIdRequest(webClientId: String): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    private fun Throwable.toGoogleAuthException(): Throwable =
        when (this) {
            is AuthSessionException -> this
            is GetCredentialCancellationException -> AuthSessionException("Google sign-in was cancelled.")
            is NoCredentialException -> AuthSessionException(
                "No Google account is available on this device. Add a Google account, update Google Play services, then try again.",
            )
            is GetCredentialProviderConfigurationException,
            is GetCredentialUnsupportedException -> AuthSessionException(
                "Google sign-in is not available on this device. Check Google Play services and try again.",
            )
            else -> AuthSessionException(
                message?.takeUnless { it.isBlank() } ?: "Google sign-in failed. Please try again.",
            )
        }
}
