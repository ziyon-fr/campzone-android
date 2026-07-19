package fr.ziyon.campzone.data.notifications

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.core.permissions.UserRole
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

data class NotificationVisibleTeam(
    val teamId: String,
    val campingId: String?,
)

data class NotificationVisibleStaffRole(
    val staffRoleId: String,
    val campingId: String?,
)

data class NotificationVisibilityScope(
    val visibleCampingIds: Set<String> = emptySet(),
    val visibleTeams: Set<NotificationVisibleTeam>? = emptySet(),
    val visibleStaffRoles: Set<NotificationVisibleStaffRole>? = emptySet(),
    val canViewAllCampings: Boolean = false,
) {
    fun canSeeCamping(campingId: String): Boolean =
        canViewAllCampings || campingId in visibleCampingIds

    fun canSeeTeam(teamId: String): Boolean =
        canViewAllCampings || visibleTeams?.any { it.teamId == teamId } == true

    fun canSeeStaffRole(staffRoleId: String): Boolean =
        canViewAllCampings || visibleStaffRoles?.any { it.staffRoleId == staffRoleId } == true

    fun filteredCampingIds(configuredIds: List<String>): List<String> =
        configuredIds.cleanIds().filter { canSeeCamping(it) }

    fun filteredTeams(configuredIds: List<String>): List<NotificationVisibleTeam> {
        val configured = configuredIds.cleanIds().toSet()
        if (canViewAllCampings) {
            return configured.map { NotificationVisibleTeam(teamId = it, campingId = null) }
                .sortedBy { it.teamId }
        }
        return visibleTeams.orEmpty()
            .filter { it.teamId in configured }
            .sortedWith(compareBy(NotificationVisibleTeam::teamId, { it.campingId.orEmpty() }))
    }

    fun filteredStaffRoles(configuredIds: List<String>): List<NotificationVisibleStaffRole> {
        val configured = configuredIds.cleanIds().toSet()
        if (canViewAllCampings) {
            return configured.map { NotificationVisibleStaffRole(staffRoleId = it, campingId = null) }
                .sortedBy { it.staffRoleId }
        }
        return visibleStaffRoles.orEmpty()
            .filter { it.staffRoleId in configured }
            .sortedWith(compareBy(NotificationVisibleStaffRole::staffRoleId, { it.campingId.orEmpty() }))
    }

    companion object {
        val Unrestricted = NotificationVisibilityScope(
            canViewAllCampings = true,
            visibleTeams = null,
            visibleStaffRoles = null,
        )
    }
}

/**
 * Loads the data the notification channel pickers need: campings where the
 * user has their own registration and the team they belong to in each.
 * Mirrors iOS `attendedCampings` / `personalTeam`.
 */
interface NotificationChannelsLoader {
    suspend fun attendedCampings(uid: String): List<Camping>
    suspend fun personalTeams(uid: String): List<PersonalTeamChannel>
    suspend fun visibilityScope(uid: String, role: UserRole, church: String): NotificationVisibilityScope {
        if (role.isAdmin) return NotificationVisibilityScope.Unrestricted
        return NotificationVisibilityScope(
            visibleCampingIds = attendedCampings(uid).mapTo(mutableSetOf()) { it.id },
            visibleTeams = personalTeams(uid).mapTo(mutableSetOf()) {
                NotificationVisibleTeam(teamId = it.team.id, campingId = it.camping.id)
            },
            visibleStaffRoles = emptySet(),
        )
    }
}

@Singleton
class FirestoreNotificationChannelsLoader @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val campingService: CampingService,
    private val teamService: TeamService,
) : NotificationChannelsLoader {
    private val permissions = AppPermissionEvaluator()

    /** Campings where the user has their own registration, newest first. */
    override suspend fun attendedCampings(uid: String): List<Camping> {
        if (uid.isBlank()) return emptyList()
        // Firestore feed/activity rules join the viewer's own registration
        // document. Guardian-only child registrations cannot authorize a broad
        // collection listener, so they are not part of this listener scope.
        val attendedIds = directlyRegisteredCampingIds(uid)
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

    override suspend fun visibilityScope(
        uid: String,
        role: UserRole,
        church: String,
    ): NotificationVisibilityScope {
        if (role.isAdmin) return NotificationVisibilityScope.Unrestricted
        if (uid.isBlank()) return NotificationVisibilityScope()

        val attendedIds = directlyRegisteredCampingIds(uid)
        val permissionUser = PermissionUser(role = role, userId = uid, church = church)
        val visibleCampings = campingService.observeCampings().first().filter { camping ->
            camping.id in attendedIds || canManageScopedNotifications(permissionUser, camping)
        }
        val visibleCampingIds = visibleCampings.mapTo(mutableSetOf()) { it.id }
        val visibleTeams = buildSet {
            visibleCampings.forEach { camping ->
                val context = camping.permissionContext()
                val canSeeEveryTeam = permissions.canManageTeams(permissionUser, context) ||
                    permissions.canModerateTeamChat(permissionUser, context)
                runCatching { teamService.loadTeams(camping.id) }
                    .getOrDefault(emptyList())
                    .filter { canSeeEveryTeam || uid in it.memberUserIds }
                    .mapTo(this) {
                        NotificationVisibleTeam(teamId = it.id, campingId = camping.id)
                    }
            }
        }
        val visibleStaffRoles = buildSet {
            visibleCampings.forEach { camping ->
                val canSeeEveryStaffRole = canManageScopedNotifications(permissionUser, camping)
                runCatching { teamService.loadStaffRoles(camping.id) }
                    .getOrDefault(emptyList())
                    .filter { canSeeEveryStaffRole || it.containsUser(uid) }
                    .mapTo(this) {
                        NotificationVisibleStaffRole(staffRoleId = it.id, campingId = camping.id)
                    }
            }
        }

        return NotificationVisibilityScope(
            visibleCampingIds = visibleCampingIds,
            visibleTeams = visibleTeams,
            visibleStaffRoles = visibleStaffRoles,
        )
    }

    private fun canManageScopedNotifications(user: PermissionUser, camping: Camping): Boolean {
        val context = camping.permissionContext()
        return permissions.canManageAnnouncements(user, context) ||
            permissions.canManageSchedule(user, context) ||
            permissions.canManageTeams(user, context) ||
            permissions.canManagePolls(user, context) ||
            permissions.canManageTransportation(user, context) ||
            permissions.canManageCheckIns(user, context) ||
            permissions.canManageAlbumMedia(user, context)
    }

    private suspend fun directlyRegisteredCampingIds(uid: String): Set<String> = withContext(Dispatchers.IO) {
        runCatching {
            firestore.collectionGroup(Registrations).whereEqualTo(UserIdField, uid).get().await()
        }.getOrNull()?.documents.orEmpty()
            .mapNotNull { it.reference.parent.parent?.id }
            .toSet()
    }

    private companion object {
        const val Registrations = "registrations"
        const val UserIdField = "userID"
    }
}

private fun Camping.permissionContext() = CampingPermissionContext(
    organizerLevelType = organizerLevel.type.wireValue,
    organizerLevelValue = organizerLevel.value,
    createdByUid = createdByUid,
)

private fun List<String>.cleanIds(): List<String> =
    map(String::trim).filter(String::isNotBlank).distinct().sorted()

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationChannelsLoaderBindings {
    @Binds
    abstract fun bindNotificationChannelsLoader(
        impl: FirestoreNotificationChannelsLoader,
    ): NotificationChannelsLoader
}
