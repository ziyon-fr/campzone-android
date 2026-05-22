package fr.ziyon.campzone.data.auth

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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

    private suspend fun ensureUserDocument(user: FirebaseUser, lastAuthProvider: String) {
        val ref = firestore.collection(UsersCollection).document(user.uid)
        val existing = ref.get().await().takeIf { it.exists() }?.data
        val payload = SignInUserPayload.mergePayload(
            identity = user.toSignInIdentity(lastAuthProvider),
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

    private fun FirebaseUser.toSignInIdentity(lastAuthProvider: String): SignInIdentity =
        SignInIdentity(
            uid = uid,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl?.toString(),
            providerIds = providerData
                .mapNotNull { it.providerId.takeUnless { providerId -> providerId == "firebase" } }
                .ifEmpty { listOf("${lastAuthProvider}.com") },
            lastAuthProvider = lastAuthProvider,
        )

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
