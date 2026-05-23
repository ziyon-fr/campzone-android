package fr.ziyon.campzone.data.auth

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import fr.ziyon.campzone.core.permissions.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface AuthSessionRepository {
    val authState: StateFlow<AuthState>
    suspend fun signInWithGoogle(activity: Activity)
    suspend fun signInWithApple(activity: Activity)
    suspend fun signInWithEmail(email: String, password: String)
    suspend fun signUpWithEmail(email: String, password: String, displayName: String?)
    suspend fun sendPasswordReset(email: String)
    suspend fun completeOnboarding(user: AuthenticatedUser, profile: OnboardingProfile)
    fun signOut()
}

@Singleton
class FirebaseAuthSessionRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val authProviders: AuthProviders,
) : AuthSessionRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var userRegistration: ListenerRegistration? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            userRegistration?.remove()
            val user = firebaseAuth.currentUser
            if (user == null) {
                _authState.value = AuthState.SignedOut
            } else {
                observeUserDocument(user)
            }
        }
    }

    override suspend fun signInWithGoogle(activity: Activity) {
        val credential = authProviders.googleSignInCredential(activity)
        val result = auth.signInWithCredential(credential).await()
        ensureUserDocument(result.user ?: error("Google sign-in returned no Firebase user"), "google")
    }

    override suspend fun signInWithApple(activity: Activity) {
        val result = auth.startActivityForSignInWithProvider(
            activity,
            authProviders.appleOAuthProvider(),
        ).await()
        ensureUserDocument(result.user ?: error("Apple sign-in returned no Firebase user"), "apple")
    }

    override suspend fun signInWithEmail(email: String, password: String) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isEmpty() || password.isEmpty()) {
            throw AuthSessionException("Enter both an email and a password.")
        }

        runCatching {
            val result = auth.signInWithEmailAndPassword(normalizedEmail, password).await()
            ensureUserDocument(
                user = result.user ?: error("Email sign-in returned no Firebase user"),
                lastAuthProvider = "email",
            )
        }.getOrElse { error ->
            throw error.toEmailAuthException()
        }
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String?,
    ) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isEmpty() || password.isEmpty()) {
            throw AuthSessionException("Enter both an email and a password.")
        }

        val resolvedName = displayName?.trim()?.takeUnless { it.isBlank() }

        runCatching {
            val result = auth.createUserWithEmailAndPassword(normalizedEmail, password).await()
            val user = result.user ?: error("Email sign-up returned no Firebase user")

            if (resolvedName != null) {
                user.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(resolvedName)
                        .build(),
                ).await()
            }

            ensureUserDocument(
                user = user,
                lastAuthProvider = "email",
                displayNameOverride = resolvedName,
            )
        }.getOrElse { error ->
            throw error.toEmailAuthException()
        }
    }

    override suspend fun sendPasswordReset(email: String) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isEmpty()) {
            throw AuthSessionException("That email address doesn't look right.")
        }

        runCatching {
            auth.sendPasswordResetEmail(normalizedEmail).await()
        }.getOrElse { error ->
            throw error.toEmailAuthException()
        }
    }

    override suspend fun completeOnboarding(user: AuthenticatedUser, profile: OnboardingProfile) {
        val currentUser = auth.currentUser ?: error("There is no signed-in user.")
        require(currentUser.uid == user.uid) { "Signed-in user does not match onboarding profile." }

        firestore.collection(UsersCollection)
            .document(user.uid)
            .set(
                OnboardingProfilePayload.userMergePayload(
                    profile = profile,
                    serverTimestamp = FieldValue.serverTimestamp(),
                    deleteField = FieldValue.delete(),
                ),
                SetOptions.merge(),
            )
            .await()

        syncOnboardingProfileSnapshot(user, profile)
    }

    override fun signOut() {
        userRegistration?.remove()
        userRegistration = null
        auth.signOut()
        _authState.value = AuthState.SignedOut
    }

    private fun observeUserDocument(user: FirebaseUser) {
        userRegistration = firestore.collection(UsersCollection)
            .document(user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val authenticatedUser = snapshot.toAuthenticatedUser(user)
                _authState.value = if (authenticatedUser.onboardingCompleted) {
                    AuthState.SignedIn(authenticatedUser)
                } else {
                    AuthState.OnboardingIncomplete(authenticatedUser)
                }
            }
    }

    private suspend fun ensureUserDocument(
        user: FirebaseUser,
        lastAuthProvider: String,
        displayNameOverride: String? = null,
    ) {
        val ref = firestore.collection(UsersCollection).document(user.uid)
        val existing = ref.get().await().takeIf { it.exists() }?.data
        val payload = SignInUserPayload.mergePayload(
            identity = user.toSignInIdentity(
                lastAuthProvider = lastAuthProvider,
                displayNameOverride = displayNameOverride,
            ),
            existing = existing,
            serverTimestamp = FieldValue.serverTimestamp(),
        )

        ref.set(payload, SetOptions.merge()).await()
    }

    private fun DocumentSnapshot?.toAuthenticatedUser(user: FirebaseUser): AuthenticatedUser {
        val data = this?.data.orEmpty()
        val uid = user.uid

        return AuthenticatedUser(
            uid = uid,
            email = data.stringValue("email") ?: user.email.orEmpty(),
            displayName = data.stringValue("displayName") ?: user.displayName.orEmpty(),
            photoUrl = data.stringValue("photoURL") ?: user.photoUrl?.toString(),
            role = UserRole.fromWire(data.stringValue("role")),
            church = data.stringValue("church").orEmpty(),
            age = data.intValue("age"),
            preferredLanguage = data.stringValue("preferredLanguage")
                ?: data.stringListValue("languages").firstOrNull().orEmpty(),
            gender = UserGender.fromWire(data.stringValue("gender")),
            onboardingCompleted = data["onboardingCompleted"] as? Boolean ?: false,
            providerIds = data.stringListValue("providerIDs")
                .ifEmpty {
                    user.providerData
                        .mapNotNull { it.providerId.takeUnless { providerId -> providerId == "firebase" } }
                },
        )
    }

    private suspend fun syncOnboardingProfileSnapshot(
        user: AuthenticatedUser,
        profile: OnboardingProfile,
    ) {
        if (profile.age == null || profile.gender == null || profile.ageGroup == null) return

        val registrations = firestore
            .collectionGroup(RegistrationsCollection)
            .whereEqualTo("userID", user.uid)
            .get()
            .await()

        val checkIns = firestore
            .collectionGroup(CheckInsCollection)
            .whereEqualTo("userID", user.uid)
            .get()
            .await()

        if (registrations.isEmpty && checkIns.isEmpty) return

        val batch = firestore.batch()
        val registrationFields = OnboardingProfilePayload.registrationSnapshotPayload(
            user = user,
            profile = profile,
            serverTimestamp = FieldValue.serverTimestamp(),
        )
        val checkInFields = OnboardingProfilePayload.checkInSnapshotPayload(
            user = user,
            profile = profile,
            serverTimestamp = FieldValue.serverTimestamp(),
        )

        registrations.documents.forEach { snapshot ->
            batch.update(snapshot.reference, registrationFields)
        }
        checkIns.documents.forEach { snapshot ->
            batch.update(snapshot.reference, checkInFields)
        }
        batch.commit().await()
    }

    private fun FirebaseUser.toSignInIdentity(
        lastAuthProvider: String,
        displayNameOverride: String? = null,
    ): SignInIdentity =
        SignInIdentity(
            uid = uid,
            email = email,
            displayName = displayNameOverride ?: displayName,
            photoUrl = photoUrl?.toString(),
            providerIds = providerData
                .mapNotNull { it.providerId.takeUnless { providerId -> providerId == "firebase" } }
                .ifEmpty { listOf(providerIdFallback(lastAuthProvider)) },
            lastAuthProvider = lastAuthProvider,
        )

    private fun providerIdFallback(lastAuthProvider: String): String = when (lastAuthProvider) {
        "apple" -> "apple.com"
        "google" -> "google.com"
        "email" -> "password"
        else -> lastAuthProvider
    }

    private fun Throwable.toEmailAuthException(): Throwable {
        if (this is AuthSessionException) return this
        val errorCode = (this as? FirebaseAuthException)?.errorCode
        val message = when (errorCode) {
            "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
            "ERROR_WEAK_PASSWORD" -> "Choose a stronger password (at least 6 characters)."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already exists for that email. Try signing in instead."
            "ERROR_WRONG_PASSWORD",
            "ERROR_INVALID_CREDENTIAL" -> "The email or password is incorrect."
            "ERROR_USER_NOT_FOUND" -> "We couldn't find an account for that email."
            "ERROR_USER_DISABLED" -> "That account has been disabled. Contact support."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Network unavailable. Check your connection and try again."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Wait a moment and try again."
            else -> {
                when (javaClass.simpleName) {
                    "FirebaseNetworkException" -> "Network unavailable. Check your connection and try again."
                    "FirebaseTooManyRequestsException" -> "Too many attempts. Wait a moment and try again."
                    else -> message?.takeUnless { it.isBlank() } ?: "Authentication failed. Please try again."
                }
            }
        }
        return AuthSessionException(message)
    }

    private fun Map<String, Any?>.stringValue(key: String): String? =
        (this[key] as? String)?.trim()?.takeUnless { it.isBlank() }

    private fun Map<String, Any?>.intValue(key: String): Int? =
        when (val value = this[key]) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            else -> null
        }

    private fun Map<String, Any?>.stringListValue(key: String): List<String> =
        (this[key] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.takeUnless { value -> value.isBlank() } }
            .orEmpty()

    private companion object {
        const val UsersCollection = "users"
        const val RegistrationsCollection = "registrations"
        const val CheckInsCollection = "checkIns"
    }
}

class AuthSessionException(message: String) : RuntimeException(message)
