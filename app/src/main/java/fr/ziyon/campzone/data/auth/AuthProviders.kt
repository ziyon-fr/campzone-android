package fr.ziyon.campzone.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
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
            "firebase_google_web_client_id is empty — paste the Web client ID from Firebase console"
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val credentialManager = CredentialManager.create(activity)
        val response = credentialManager.getCredential(activity, request)
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
}
