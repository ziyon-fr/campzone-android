package fr.ziyon.campzone.data.teams

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.TeamMember
import fr.ziyon.campzone.data.model.TeamMemberRole
import fr.ziyon.campzone.data.model.TeamPenalty
import fr.ziyon.campzone.data.model.CampingStaffRole
import fr.ziyon.campzone.data.model.StaffCapability
import fr.ziyon.campzone.data.model.StaffRoleKind
import fr.ziyon.campzone.data.model.StaffRoleMember
import fr.ziyon.campzone.data.model.StaffRolePayload
import fr.ziyon.campzone.data.model.TeamPayload
import fr.ziyon.campzone.data.model.toStaffRoleOrNull
import fr.ziyon.campzone.data.model.toTeamOrNull
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class TeamDraft(
    val id: String,
    val campingId: String,
    val name: String,
    val slogan: String,
    val symbolName: String,
    val colorHex: String,
    val photoUrl: String?,
    val photoPublicId: String?,
)

data class StaffRoleDraft(
    val id: String,
    val campingId: String,
    val name: String,
    val kind: StaffRoleKind = StaffRoleKind.Custom,
    val description: String = "",
    val symbolName: String = CampingStaffRole.DEFAULT_SYMBOL,
    val colorHex: String = CampingStaffRole.DEFAULT_COLOR,
    val members: List<StaffRoleMember> = emptyList(),
    val capabilities: List<StaffCapability> = emptyList(),
    val chatEnabled: Boolean = true,
    val createdByUid: String? = null,
)

data class TeamScoreRequest(
    val teamId: String,
    val campingId: String,
    val points: Int,
    val reason: String,
)

interface TeamService {
    fun observeTeams(campingId: String): Flow<List<Team>>
    suspend fun loadTeams(campingId: String): List<Team>
    suspend fun saveTeam(draft: TeamDraft): Team
    suspend fun deleteTeam(id: String, campingId: String)
    fun observeStaffRoles(campingId: String, memberUserId: String? = null): Flow<List<CampingStaffRole>>
    suspend fun loadStaffRoles(campingId: String, memberUserId: String? = null): List<CampingStaffRole>
    suspend fun saveStaffRole(draft: StaffRoleDraft): CampingStaffRole
    suspend fun deleteStaffRole(id: String, campingId: String)
    suspend fun assignMember(member: TeamMember, toTeamId: String, campingId: String): List<Team>
    suspend fun removeMember(memberId: String, fromTeamId: String, campingId: String): List<Team>
    suspend fun updateMemberRole(memberId: String, role: TeamMemberRole, teamId: String, campingId: String): List<Team>
    suspend fun updateTeamScore(request: TeamScoreRequest): Team
    suspend fun applyPenalty(penalty: TeamPenalty, teamId: String, campingId: String): Team
    suspend fun updateMemberScore(memberId: String, delta: Int, teamId: String, campingId: String): Team
}

@Singleton
class FirestoreTeamService @Inject constructor(
    private val db: FirebaseFirestore,
) : TeamService {

    override fun observeTeams(campingId: String): Flow<List<Team>> = callbackFlow {
        val listener = teamsCollection(campingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val teams = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.toTeamOrNull(doc.id)
                }?.sortedWith(teamComparator) ?: emptyList()
                trySend(teams)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun loadTeams(campingId: String): List<Team> {
        val snapshot = teamsCollection(campingId).get().await()
        return snapshot.documents
            .mapNotNull { it.data?.toTeamOrNull(it.id) }
            .sortedWith(teamComparator)
    }

    override suspend fun saveTeam(draft: TeamDraft): Team {
        val doc = teamsCollection(draft.campingId).document(draft.id)
        val existingSnapshot = doc.get().await()
        val existing = if (existingSnapshot.exists()) {
            existingSnapshot.data?.toTeamOrNull(draft.id)
        } else null

        val team = Team(
            id = draft.id,
            campingId = draft.campingId,
            name = draft.name.trim(),
            slogan = draft.slogan,
            symbolName = draft.symbolName,
            colorHex = draft.colorHex,
            points = existing?.points ?: 0,
            penalties = existing?.penalties ?: emptyList(),
            members = existing?.members ?: emptyList(),
            photoUrl = draft.photoUrl?.trim()?.takeUnless { it.isBlank() },
            photoPublicId = draft.photoPublicId?.trim()?.takeUnless { it.isBlank() },
            createdAt = existing?.createdAt,
            updatedAt = Date(),
        )

        writeTeam(team, includeCreatedAt = !existingSnapshot.exists())
        val saved = doc.get().await()
        return saved.data?.toTeamOrNull(draft.id)
            ?: throw IllegalStateException("Team could not be loaded after save.")
    }

    override suspend fun deleteTeam(id: String, campingId: String) {
        teamsCollection(campingId).document(id).delete().await()
    }

    override fun observeStaffRoles(campingId: String, memberUserId: String?): Flow<List<CampingStaffRole>> = callbackFlow {
        val collection = staffRolesCollection(campingId)
        val query = memberUserId?.trim()?.takeUnless { it.isBlank() }
            ?.let { collection.whereArrayContains("memberUserIDs", it) }
            ?: collection
        val listener = query
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val roles = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.toStaffRoleOrNull(doc.id)
                }?.sortedWith(staffRoleComparator) ?: emptyList()
                trySend(roles)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun loadStaffRoles(campingId: String, memberUserId: String?): List<CampingStaffRole> {
        val collection = staffRolesCollection(campingId)
        val query = memberUserId?.trim()?.takeUnless { it.isBlank() }
            ?.let { collection.whereArrayContains("memberUserIDs", it) }
            ?: collection
        val snapshot = query.get().await()
        return snapshot.documents
            .mapNotNull { it.data?.toStaffRoleOrNull(it.id) }
            .sortedWith(staffRoleComparator)
    }

    override suspend fun saveStaffRole(draft: StaffRoleDraft): CampingStaffRole {
        val doc = staffRolesCollection(draft.campingId).document(draft.id)
        val existingSnapshot = doc.get().await()
        val existing = if (existingSnapshot.exists()) {
            existingSnapshot.data?.toStaffRoleOrNull(draft.id)
        } else null
        val role = CampingStaffRole(
            id = draft.id,
            campingId = draft.campingId,
            name = draft.name.trim(),
            kind = draft.kind,
            description = draft.description,
            symbolName = draft.symbolName,
            colorHex = draft.colorHex,
            members = draft.members.distinctBy { it.userId },
            capabilities = draft.capabilities.distinct(),
            chatEnabled = draft.chatEnabled,
            createdByUid = existing?.createdByUid ?: draft.createdByUid,
            createdAt = existing?.createdAt,
            updatedAt = Date(),
        )
        val payload = StaffRolePayload.staffRolePayload(
            role = role,
            serverTimestamp = FieldValue.serverTimestamp(),
            includeCreatedAt = !existingSnapshot.exists(),
        )
        doc.set(payload, SetOptions.merge()).await()
        return doc.get().await().data?.toStaffRoleOrNull(draft.id)
            ?: throw IllegalStateException("Staff role could not be loaded after save.")
    }

    override suspend fun deleteStaffRole(id: String, campingId: String) {
        staffRolesCollection(campingId).document(id).delete().await()
    }

    override suspend fun assignMember(
        member: TeamMember,
        toTeamId: String,
        campingId: String,
    ): List<Team> {
        var teams = loadTeams(campingId)
        check(teams.any { it.id == toTeamId }) { "Team not found." }

        val batch = db.batch()
        teams = teams.map { team ->
            val updatedMembers = team.members
                .filter { it.userId != member.userId }
                .let { if (team.id == toTeamId) it + member else it }
            val normalized = TeamPayload.normalizeCaptaincy(updatedMembers)
            val updatedTeam = team.copy(members = normalized, updatedAt = Date())
            val (payload, requiresMerge) = teamWritePayload(updatedTeam, includeCreatedAt = false)
            val document = teamsCollection(campingId).document(team.id)
            if (requiresMerge) {
                batch.set(document, payload, SetOptions.merge())
            } else {
                batch.set(document, payload)
            }
            updatedTeam
        }
        batch.commit().await()
        return loadTeams(campingId)
    }

    override suspend fun removeMember(
        memberId: String,
        fromTeamId: String,
        campingId: String,
    ): List<Team> {
        val team = loadSingleTeam(fromTeamId, campingId)
        val updated = team.copy(members = team.members.filter { it.id != memberId })
        saveMutable(updated)
        return loadTeams(campingId)
    }

    override suspend fun updateMemberRole(
        memberId: String,
        role: TeamMemberRole,
        teamId: String,
        campingId: String,
    ): List<Team> {
        var team = loadSingleTeam(teamId, campingId)
        check(team.members.any { it.id == memberId }) { "Member not found." }
        val updated = team.copy(
            members = TeamPayload.normalizeCaptaincy(
                team.members.map { if (it.id == memberId) it.copy(role = role) else it }
            ),
        )
        saveMutable(updated)
        return loadTeams(campingId)
    }

    override suspend fun updateTeamScore(request: TeamScoreRequest): Team {
        val team = loadSingleTeam(request.teamId, request.campingId)
        val updated = team.copy(points = team.points + request.points)
        saveMutable(updated)
        return loadSingleTeam(request.teamId, request.campingId)
    }

    override suspend fun applyPenalty(
        penalty: TeamPenalty,
        teamId: String,
        campingId: String,
    ): Team {
        val team = loadSingleTeam(teamId, campingId)
        val updated = team.copy(penalties = team.penalties + penalty)
        saveMutable(updated)
        return loadSingleTeam(teamId, campingId)
    }

    override suspend fun updateMemberScore(
        memberId: String,
        delta: Int,
        teamId: String,
        campingId: String,
    ): Team {
        val team = loadSingleTeam(teamId, campingId)
        val updatedMembers = team.members.map { m ->
            if (m.id == memberId) m.copy(personalScore = m.personalScore + delta) else m
        }
        saveMutable(team.copy(members = updatedMembers))
        return loadSingleTeam(teamId, campingId)
    }

    private suspend fun saveMutable(team: Team) {
        writeTeam(team, includeCreatedAt = false)
    }

    private suspend fun writeTeam(team: Team, includeCreatedAt: Boolean) {
        val (payload, requiresMerge) = teamWritePayload(team, includeCreatedAt)
        val document = teamsCollection(team.campingId).document(team.id)
        if (requiresMerge) {
            document.set(payload, SetOptions.merge()).await()
        } else {
            document.set(payload).await()
        }
    }

    private fun teamWritePayload(
        team: Team,
        includeCreatedAt: Boolean,
    ): Pair<Map<String, Any?>, Boolean> {
        val payload = TeamPayload.teamPayload(
            team = team,
            serverTimestamp = FieldValue.serverTimestamp(),
            deleteField = FieldValue.delete(),
            includeCreatedAt = includeCreatedAt,
        ).toMutableMap()
        if (!includeCreatedAt) {
            team.createdAt?.let { payload["createdAt"] = it }
        }
        val requiresMerge = team.photoUrl.isNullOrBlank() || team.photoPublicId.isNullOrBlank()
        return payload to requiresMerge
    }

    private suspend fun loadSingleTeam(id: String, campingId: String): Team {
        val snapshot = teamsCollection(campingId).document(id).get().await()
        return snapshot.data?.toTeamOrNull(id) ?: throw IllegalStateException("Team not found.")
    }

    private fun teamsCollection(campingId: String) =
        db.collection("campings").document(campingId).collection("teams")

    private fun staffRolesCollection(campingId: String) =
        db.collection("campings").document(campingId).collection("staffRoles")

    private val teamComparator: Comparator<Team> = compareByDescending<Team> { it.totalScore }
        .thenBy { it.name.lowercase() }

    private val staffRoleComparator: Comparator<CampingStaffRole> =
        compareBy<CampingStaffRole> { it.name.lowercase() }.thenBy { it.id }
}

class FakeTeamService(
    private val teams: MutableList<Team> = mutableListOf(
        Team(
            id = "lions",
            campingId = "preview-camping",
            name = "Lions",
            slogan = "Courage in service",
            symbolName = "flame.fill",
            colorHex = "#D9432F",
            points = 180,
            penalties = listOf(TeamPenalty(id = UUID.randomUUID().toString(), reason = "Late to morning lineup", points = 10, createdAt = Date())),
            members = listOf(
                TeamMember(id = "preview-user", userId = "preview-user", displayName = "Preview Admin", church = "Central SDA", role = TeamMemberRole.Captain, personalScore = 35),
                TeamMember(id = "member-1", userId = "member-1", displayName = "Ana Silva", church = "Central SDA", personalScore = 22),
            ),
            createdAt = Date(),
            updatedAt = Date(),
        ),
        Team(
            id = "eagles",
            campingId = "preview-camping",
            name = "Eagles",
            slogan = "Higher together",
            symbolName = "paperplane.fill",
            colorHex = "#2364AA",
            points = 165,
            members = listOf(
                TeamMember(id = "member-2", userId = "member-2", displayName = "Marc Dubois", church = "Lyon SDA", role = TeamMemberRole.Captain, personalScore = 28),
                TeamMember(id = "member-3", userId = "member-3", displayName = "Joao Pereira", church = "Lisbon SDA", personalScore = 18),
            ),
            createdAt = Date(),
            updatedAt = Date(),
        ),
    ),
    private val staffRoles: MutableList<CampingStaffRole> = mutableListOf(
        CampingStaffRole(
            id = "worship",
            campingId = "preview-camping",
            name = "Worship Team",
            kind = StaffRoleKind.Worship,
            description = "Songs, prayer moments, and spiritual programming.",
            symbolName = "music.mic",
            colorHex = "#6A4C93",
            members = listOf(
                StaffRoleMember(id = "preview-user", userId = "preview-user", displayName = "Preview Admin", church = "Central SDA", title = "Lead"),
                StaffRoleMember(id = "member-1", userId = "member-1", displayName = "Ana Silva", church = "Central SDA"),
            ),
            capabilities = listOf(StaffCapability.ManageSchedule, StaffCapability.ManageAnnouncements),
            createdAt = Date(),
            updatedAt = Date(),
        ),
    ),
) : TeamService {
    private val teamsState = MutableStateFlow(teams.toList())
    private val staffRolesState = MutableStateFlow(staffRoles.toList())

    override fun observeTeams(campingId: String): Flow<List<Team>> =
        teamsState.map { list -> sorted(list.filter { it.campingId == campingId }) }

    override suspend fun loadTeams(campingId: String): List<Team> =
        sorted(teams.filter { it.campingId == campingId })

    override suspend fun saveTeam(draft: TeamDraft): Team {
        val now = Date()
        val existing = teams.firstOrNull { it.id == draft.id }
        val saved = Team(
            id = draft.id,
            campingId = draft.campingId,
            name = draft.name.trim(),
            slogan = draft.slogan,
            symbolName = draft.symbolName,
            colorHex = draft.colorHex,
            points = existing?.points ?: 0,
            penalties = existing?.penalties ?: emptyList(),
            members = existing?.members ?: emptyList(),
            photoUrl = draft.photoUrl?.trim()?.takeUnless { it.isBlank() },
            photoPublicId = draft.photoPublicId?.trim()?.takeUnless { it.isBlank() },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        teams.removeAll { it.id == draft.id }
        teams.add(saved)
        publish()
        return saved
    }

    override suspend fun deleteTeam(id: String, campingId: String) {
        teams.removeAll { it.id == id && it.campingId == campingId }
        publish()
    }

    override fun observeStaffRoles(campingId: String, memberUserId: String?): Flow<List<CampingStaffRole>> =
        staffRolesState.map { list ->
            sortedStaffRoles(
                list.filter { role ->
                    role.campingId == campingId &&
                        (memberUserId.isNullOrBlank() || role.containsUser(memberUserId))
                },
            )
        }

    override suspend fun loadStaffRoles(campingId: String, memberUserId: String?): List<CampingStaffRole> =
        sortedStaffRoles(
            staffRoles.filter { role ->
                role.campingId == campingId &&
                    (memberUserId.isNullOrBlank() || role.containsUser(memberUserId))
            },
        )

    override suspend fun saveStaffRole(draft: StaffRoleDraft): CampingStaffRole {
        val now = Date()
        val existing = staffRoles.firstOrNull { it.id == draft.id && it.campingId == draft.campingId }
        val saved = CampingStaffRole(
            id = draft.id,
            campingId = draft.campingId,
            name = draft.name.trim(),
            kind = draft.kind,
            description = draft.description,
            symbolName = draft.symbolName,
            colorHex = draft.colorHex,
            members = draft.members.distinctBy { it.userId },
            capabilities = draft.capabilities.distinct(),
            chatEnabled = draft.chatEnabled,
            createdByUid = existing?.createdByUid ?: draft.createdByUid,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        staffRoles.removeAll { it.id == draft.id && it.campingId == draft.campingId }
        staffRoles.add(saved)
        publish()
        return saved
    }

    override suspend fun deleteStaffRole(id: String, campingId: String) {
        staffRoles.removeAll { it.id == id && it.campingId == campingId }
        publish()
    }

    override suspend fun assignMember(member: TeamMember, toTeamId: String, campingId: String): List<Team> {
        val now = Date()
        val campingTeams = teams.filter { it.campingId == campingId }.toMutableList()
        for (i in campingTeams.indices) {
            val t = campingTeams[i]
            val members = t.members.filter { it.userId != member.userId }
                .let { if (t.id == toTeamId) it + member else it }
            campingTeams[i] = t.copy(members = TeamPayload.normalizeCaptaincy(members), updatedAt = now)
        }
        teams.removeAll { it.campingId == campingId }
        teams.addAll(campingTeams)
        publish()
        return sorted(campingTeams)
    }

    override suspend fun removeMember(memberId: String, fromTeamId: String, campingId: String): List<Team> {
        val idx = teams.indexOfFirst { it.id == fromTeamId && it.campingId == campingId }
        if (idx >= 0) {
            teams[idx] = teams[idx].copy(
                members = teams[idx].members.filter { it.id != memberId },
                updatedAt = Date(),
            )
        }
        publish()
        return sorted(teams.filter { it.campingId == campingId })
    }

    override suspend fun updateMemberRole(memberId: String, role: TeamMemberRole, teamId: String, campingId: String): List<Team> {
        val idx = teams.indexOfFirst { it.id == teamId && it.campingId == campingId }
        if (idx >= 0) {
            val members = teams[idx].members.map { if (it.id == memberId) it.copy(role = role) else it }
            teams[idx] = teams[idx].copy(members = TeamPayload.normalizeCaptaincy(members), updatedAt = Date())
        }
        publish()
        return sorted(teams.filter { it.campingId == campingId })
    }

    override suspend fun updateTeamScore(request: TeamScoreRequest): Team {
        val idx = teams.indexOfFirst { it.id == request.teamId && it.campingId == request.campingId }
        check(idx >= 0) { "Team not found." }
        teams[idx] = teams[idx].copy(points = teams[idx].points + request.points, updatedAt = Date())
        publish()
        return teams[idx]
    }

    override suspend fun applyPenalty(penalty: TeamPenalty, teamId: String, campingId: String): Team {
        val idx = teams.indexOfFirst { it.id == teamId && it.campingId == campingId }
        check(idx >= 0) { "Team not found." }
        teams[idx] = teams[idx].copy(penalties = teams[idx].penalties + penalty, updatedAt = Date())
        publish()
        return teams[idx]
    }

    override suspend fun updateMemberScore(memberId: String, delta: Int, teamId: String, campingId: String): Team {
        val idx = teams.indexOfFirst { it.id == teamId && it.campingId == campingId }
        check(idx >= 0) { "Team not found." }
        val updatedMembers = teams[idx].members.map { m ->
            if (m.id == memberId) m.copy(personalScore = m.personalScore + delta) else m
        }
        teams[idx] = teams[idx].copy(members = updatedMembers, updatedAt = Date())
        publish()
        return teams[idx]
    }

    private fun publish() {
        teamsState.value = teams.toList()
        staffRolesState.value = staffRoles.toList()
    }

    private fun sorted(list: List<Team>): List<Team> =
        list.sortedWith(compareByDescending<Team> { it.totalScore }.thenBy { it.name.lowercase() })

    private fun sortedStaffRoles(list: List<CampingStaffRole>): List<CampingStaffRole> =
        list.sortedWith(compareBy<CampingStaffRole> { it.name.lowercase() }.thenBy { it.id })
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TeamBindings {
    @Binds
    @Singleton
    abstract fun bindTeamService(impl: FirestoreTeamService): TeamService
}
