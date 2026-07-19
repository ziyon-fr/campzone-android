package fr.ziyon.campzone.data.profile

import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.UserGender
import java.util.Date

/**
 * In-memory [UserProfileRepository] for ViewModel tests. Mirrors the iOS
 * `MockUserProfileService`: no Firestore, no denormalization fan-out. Reuses the
 * real [UserProfilePayload.cleaned] normalization so saved values match the
 * production write path.
 */
class FakeUserProfileRepository(
    initialUser: UserProfile = sampleUserProfile(),
    var shouldFail: Boolean = false,
) : UserProfileRepository {
    var user: UserProfile = initialUser
        private set

    val savedUsers: MutableList<UserProfile> = mutableListOf()

    override suspend fun fetchUser(uid: String, fallback: AuthenticatedUser): UserProfile {
        throwIfNeeded()
        return if (user.uid == uid) user else UserProfile.empty(from = fallback)
    }

    override suspend fun saveUser(user: UserProfile): UserProfile {
        throwIfNeeded()
        val saved = UserProfilePayload.cleaned(user)
        this.user = saved
        savedUsers += saved
        return saved
    }

    override suspend fun uploadProfilePhoto(
        user: UserProfile,
        bytes: ByteArray,
        mimeType: String,
        fileExtension: String,
    ): UserProfile {
        throwIfNeeded()
        val saved = UserProfilePayload.cleaned(
            user.copy(
                photoUrl = "https://cdn.example/${user.uid}.$fileExtension",
                photoPublicId = "campzone/avatars/${user.uid}",
                onboardingCompleted = true,
            ),
        )
        this.user = saved
        savedUsers += saved
        return saved
    }

    override suspend fun requestAccountDeletion(uid: String): UserProfile {
        throwIfNeeded()
        if (user.uid == uid) user = user.copy(pendingDeletionAt = Date())
        return user
    }

    override suspend fun cancelAccountDeletion(uid: String): UserProfile {
        throwIfNeeded()
        if (user.uid == uid) user = user.copy(pendingDeletionAt = null)
        return user
    }

    private fun throwIfNeeded() {
        if (shouldFail) error("The fake profile repository was configured to fail.")
    }
}

fun sampleUserProfile(): UserProfile =
    UserProfile(
        uid = "preview-user",
        displayName = "Campzone Guest",
        age = 22,
        gender = UserGender.PreferNotToSay,
        church = "Paris Central SDA",
        skills = listOf("Singing", "First Aid"),
        profession = "Designer",
        education = "Bachelor",
        pathfinderRank = "Guide",
        phone = "+33 6 00 00 00 00",
        email = "user@campzone.app",
        preferredLanguage = "fr",
        languages = listOf("pt", "fr"),
        role = UserRole.User,
        photoUrl = null,
        photoPublicId = null,
        onboardingCompleted = true,
    )
