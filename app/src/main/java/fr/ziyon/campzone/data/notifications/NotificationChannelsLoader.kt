package fr.ziyon.campzone.data.notifications

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.teams.TeamService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** A camping the user attends paired with the team they belong to there. */
data class PersonalTeamChannel(
    val camping: Camping,
    val team: Team,
)

/**
 * Loads the data the notification channel pickers need: the campings the user
 * is registered for (self or as guardian) and the team they belong to in each.
 * Mirrors iOS `attendedCampings` / `personalTeam`.
 */
interface NotificationChannelsLoader {
    suspend fun attendedCampings(uid: String): List<Camping>
    suspend fun personalTeams(uid: String): List<PersonalTeamChannel>
}

@Singleton
class FirestoreNotificationChannelsLoader @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val campingService: CampingService,
    private val teamService: TeamService,
) : NotificationChannelsLoader {
    /** Campings the user has a registration in (self or guardian), newest first. */
    override suspend fun attendedCampings(uid: String): List<Camping> {
        if (uid.isBlank()) return emptyList()
        val attendedIds = attendedCampingIds(uid)
        if (attendedIds.isEmpty()) return emptyList()
        return campingService.observeCampings().first()
            .filter { it.id in attendedIds }
            .sortedByDescending { it.startDate }
    }

    /** The user's personal team in each attended camping (only where they are a member). */
    override suspend fun personalTeams(uid: String): List<PersonalTeamChannel> {
        if (uid.isBlank()) return emptyList()
        return attendedCampings(uid).mapNotNull { camping ->
            val team = runCatching { teamService.loadTeams(camping.id) }
                .getOrDefault(emptyList())
                .firstOrNull { uid in it.memberUserIds }
            team?.let { PersonalTeamChannel(camping = camping, team = it) }
        }
    }

    private suspend fun attendedCampingIds(uid: String): Set<String> = withContext(Dispatchers.IO) {
        val byUser = runCatching {
            firestore.collectionGroup(Registrations).whereEqualTo(UserIdField, uid).get().await()
        }.getOrNull()?.documents.orEmpty()
        val byGuardian = runCatching {
            firestore.collectionGroup(Registrations).whereEqualTo(GuardianIdField, uid).get().await()
        }.getOrNull()?.documents.orEmpty()

        (byUser + byGuardian)
            .mapNotNull { it.reference.parent.parent?.id }
            .toSet()
    }

    private companion object {
        const val Registrations = "registrations"
        const val UserIdField = "userID"
        const val GuardianIdField = "guardianID"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationChannelsLoaderBindings {
    @Binds
    abstract fun bindNotificationChannelsLoader(
        impl: FirestoreNotificationChannelsLoader,
    ): NotificationChannelsLoader
}
