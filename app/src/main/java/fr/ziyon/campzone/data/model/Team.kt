package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.data.auth.CampingAgeGroup
import fr.ziyon.campzone.data.auth.UserGender
import java.util.Date

/**
 * `campings/{id}/teams/{teamId}` (`02-firestore-schema.md` §5.1). Doc ID is a
 * client UUID == [id] (no `id` field on the wire). Every write rewrites the
 * whole doc and re-derives `memberUserIDs` (the RBAC team-chat membership check
 * reads it). `totalScore` is computed on read, never stored.
 */
data class Team(
    val id: String,
    val campingId: String,
    val name: String = "",
    val slogan: String = "",
    val symbolName: String = DEFAULT_SYMBOL,
    val colorHex: String = DEFAULT_COLOR,
    val points: Int = 0,
    val penalties: List<TeamPenalty> = emptyList(),
    val members: List<TeamMember> = emptyList(),
    val photoUrl: String? = null,
    val photoPublicId: String? = null,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val memberUserIds: List<String>
        get() = members.map { it.userId }

    val totalScore: Int
        get() = points + members.sumOf { it.personalScore } - penalties.sumOf { it.points }

    companion object {
        const val DEFAULT_SYMBOL = "shield.lefthalf.filled"
        const val DEFAULT_COLOR = "#2E7D32"
    }
}

data class TeamMember(
    val id: String,
    val userId: String,
    val displayName: String,
    val church: String,
    val age: Int? = null,
    val role: TeamMemberRole = TeamMemberRole.Member,
    val personalScore: Int = 0,
    val notificationUserId: String? = null,
    val ageGroup: CampingAgeGroup? = null,
    val gender: UserGender? = null,
    val preferredLanguage: String = "",
    val languages: List<String> = emptyList(),
    val photoUrl: String? = null,
)

data class TeamPenalty(
    val id: String,
    val reason: String,
    val points: Int,
    val createdAt: Date,
)

/**
 * `campings/{id}/staffRoles/{roleId}`. These are organizer-defined ministry
 * roles for the camp operations team (kitchen, games, reception, worship,
 * custom, etc.). They are deliberately separate from game teams and global
 * `UserRole`; membership is scoped to one camping.
 */
data class CampingStaffRole(
    val id: String,
    val campingId: String,
    val name: String,
    val kind: StaffRoleKind = StaffRoleKind.Custom,
    val description: String = "",
    val symbolName: String = DEFAULT_SYMBOL,
    val colorHex: String = DEFAULT_COLOR,
    val members: List<StaffRoleMember> = emptyList(),
    val capabilities: List<StaffCapability> = emptyList(),
    val chatEnabled: Boolean = true,
    val createdByUid: String? = null,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val memberUserIds: List<String>
        get() = members.map { it.userId }.distinct()

    fun containsUser(userId: String?): Boolean =
        !userId.isNullOrBlank() && memberUserIds.contains(userId)

    companion object {
        const val DEFAULT_SYMBOL = "person.2.badge.gearshape.fill"
        const val DEFAULT_COLOR = "#4F7CAC"
    }
}

data class StaffRoleMember(
    val id: String,
    val userId: String,
    val displayName: String,
    val church: String = "",
    val title: String = "",
    val notificationUserId: String? = null,
    val preferredLanguage: String = "",
    val photoUrl: String? = null,
)

fun CampingAttendee.toTeamMember(role: TeamMemberRole = TeamMemberRole.Member): TeamMember =
    TeamMember(
        id = userId,
        userId = userId,
        displayName = displayName,
        church = church,
        role = role,
        personalScore = 0,
        notificationUserId = guardianId?.trim()?.takeUnless { it.isBlank() } ?: userId,
        ageGroup = ageGroup,
        gender = gender,
        preferredLanguage = preferredLanguage,
        languages = languages,
        photoUrl = photoUrl,
    )

fun TeamMember.toStaffRoleMember(title: String = ""): StaffRoleMember =
    StaffRoleMember(
        id = id,
        userId = userId,
        displayName = displayName,
        church = church,
        title = title,
        notificationUserId = notificationUserId,
        preferredLanguage = preferredLanguage,
        photoUrl = photoUrl,
    )

// region decode

internal fun Map<String, Any?>.toTeamOrNull(documentId: String): Team? =
    Team(
        id = documentId,
        campingId = stringValue("campingID").orEmpty(),
        name = rawStringValue("name").orEmpty(),
        slogan = rawStringValue("slogan").orEmpty(),
        symbolName = stringValue("symbolName") ?: Team.DEFAULT_SYMBOL,
        colorHex = stringValue("colorHex") ?: Team.DEFAULT_COLOR,
        points = intValue("points") ?: 0,
        penalties = mapListValue("penalties").mapNotNull { it.toTeamPenaltyOrNull() },
        members = mapListValue("members").mapNotNull { it.toTeamMemberOrNull() },
        photoUrl = stringValue("photoURL"),
        photoPublicId = stringValue("photoPublicID"),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )

internal fun Map<String, Any?>.toTeamMemberOrNull(): TeamMember? {
    val userId = stringValue("userID") ?: stringValue("id") ?: return null
    return TeamMember(
        id = stringValue("id") ?: userId,
        userId = userId,
        displayName = stringValue("displayName") ?: "Participant",
        church = rawStringValue("church").orEmpty(),
        age = intValue("age"),
        role = TeamMemberRole.fromWire(stringValue("role")),
        personalScore = intValue("personalScore") ?: 0,
        notificationUserId = stringValue("notificationUserID"),
        ageGroup = CampingAgeGroup.fromWire(stringValue("ageGroup")),
        gender = UserGender.fromWire(stringValue("gender")),
        preferredLanguage = rawStringValue("preferredLanguage").orEmpty(),
        languages = stringListValue("languages"),
        photoUrl = stringValue("photoURL"),
    )
}

internal fun Map<String, Any?>.toTeamPenaltyOrNull(): TeamPenalty? {
    val id = stringValue("id") ?: return null
    val createdAt = dateValue("createdAt") ?: return null
    return TeamPenalty(
        id = id,
        reason = rawStringValue("reason").orEmpty(),
        points = intValue("points") ?: 0,
        createdAt = createdAt,
    )
}

internal fun Map<String, Any?>.toStaffRoleOrNull(documentId: String): CampingStaffRole? {
    val campingId = stringValue("campingID") ?: return null
    val name = rawStringValue("name") ?: return null
    return CampingStaffRole(
        id = stringValue("id") ?: documentId,
        campingId = campingId,
        name = name,
        kind = StaffRoleKind.fromWire(stringValue("kind")),
        description = rawStringValue("description").orEmpty(),
        symbolName = stringValue("symbolName") ?: CampingStaffRole.DEFAULT_SYMBOL,
        colorHex = stringValue("colorHex") ?: CampingStaffRole.DEFAULT_COLOR,
        members = mapListValue("members").mapNotNull { it.toStaffRoleMemberOrNull() },
        capabilities = rawStringListValue("capabilities").mapNotNull(StaffCapability::fromWire),
        chatEnabled = boolValue("chatEnabled") ?: true,
        createdByUid = stringValue("createdByUID"),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

internal fun Map<String, Any?>.toStaffRoleMemberOrNull(): StaffRoleMember? {
    val userId = stringValue("userID") ?: stringValue("id") ?: return null
    return StaffRoleMember(
        id = stringValue("id") ?: userId,
        userId = userId,
        displayName = rawStringValue("displayName") ?: "Staff member",
        church = rawStringValue("church").orEmpty(),
        title = rawStringValue("title").orEmpty(),
        notificationUserId = stringValue("notificationUserID"),
        preferredLanguage = rawStringValue("preferredLanguage").orEmpty(),
        photoUrl = stringValue("photoURL"),
    )
}

// endregion

internal object TeamPayload {

    /** Full-doc rewrite. `memberUserIDs` is always re-derived; captaincy is normalised. */
    fun teamPayload(
        team: Team,
        serverTimestamp: Any,
        deleteField: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val members = normalizeCaptaincy(team.members)
        val payload = linkedMapOf<String, Any?>(
            "campingID" to team.campingId,
            "name" to team.name.trim(),
            "slogan" to team.slogan,
            "symbolName" to team.symbolName,
            "colorHex" to team.colorHex,
            "points" to team.points,
            "penalties" to team.penalties.map(::penaltyMap),
            "members" to members.map(::memberMap),
            "memberUserIDs" to members.map { it.userId },
            "updatedAt" to serverTimestamp,
        )
        payload["photoURL"] = team.photoUrl?.trim()?.takeUnless { it.isBlank() } ?: deleteField
        payload["photoPublicID"] = team.photoPublicId?.trim()?.takeUnless { it.isBlank() } ?: deleteField
        if (includeCreatedAt) payload["createdAt"] = serverTimestamp
        return payload
    }

    fun memberMap(member: TeamMember): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "id" to member.id,
            "userID" to member.userId,
            "displayName" to member.displayName,
            "church" to member.church,
            "role" to member.role.wireValue,
            "personalScore" to member.personalScore,
            "preferredLanguage" to member.preferredLanguage,
            "languages" to member.languages,
        )
        member.age?.let { map["age"] = it }
        member.notificationUserId?.trim()?.takeUnless { it.isBlank() }
            ?.let { map["notificationUserID"] = it }
        member.ageGroup?.let { map["ageGroup"] = it.wireValue }
        member.gender?.let { map["gender"] = it.wireValue }
        member.photoUrl?.trim()?.takeUnless { it.isBlank() }?.let { map["photoURL"] = it }
        return map
    }

    fun penaltyMap(penalty: TeamPenalty): Map<String, Any?> =
        linkedMapOf(
            "id" to penalty.id,
            "reason" to penalty.reason,
            "points" to penalty.points.coerceAtLeast(0),
            "createdAt" to penalty.createdAt,
        )

    /** At most one captain and one vice-captain; extras demoted to member. */
    fun normalizeCaptaincy(members: List<TeamMember>): List<TeamMember> {
        var captainSeen = false
        var viceSeen = false
        return members.map { member ->
            when (member.role) {
                TeamMemberRole.Captain -> if (captainSeen) {
                    member.copy(role = TeamMemberRole.Member)
                } else {
                    captainSeen = true
                    member
                }
                TeamMemberRole.ViceCaptain -> if (viceSeen) {
                    member.copy(role = TeamMemberRole.Member)
                } else {
                    viceSeen = true
                    member
                }
                TeamMemberRole.Member -> member
            }
        }
    }
}

internal object StaffRolePayload {

    fun staffRolePayload(
        role: CampingStaffRole,
        serverTimestamp: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val members = role.members.distinctBy { it.userId }
        val payload = linkedMapOf<String, Any?>(
            "id" to role.id,
            "campingID" to role.campingId,
            "name" to role.name.trim(),
            "kind" to role.kind.wireValue,
            "description" to role.description,
            "symbolName" to role.symbolName,
            "colorHex" to role.colorHex,
            "members" to members.map(::memberMap),
            "memberUserIDs" to members.map { it.userId },
            "capabilities" to role.capabilities.map { it.wireValue }.distinct(),
            "chatEnabled" to role.chatEnabled,
            "updatedAt" to serverTimestamp,
        )
        role.createdByUid?.trim()?.takeUnless { it.isBlank() }?.let { payload["createdByUID"] = it }
        if (includeCreatedAt) payload["createdAt"] = serverTimestamp
        return payload
    }

    fun memberMap(member: StaffRoleMember): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "id" to member.id,
            "userID" to member.userId,
            "displayName" to member.displayName,
            "church" to member.church,
            "title" to member.title,
            "preferredLanguage" to member.preferredLanguage,
        )
        member.notificationUserId?.trim()?.takeUnless { it.isBlank() }
            ?.let { map["notificationUserID"] = it }
        member.photoUrl?.trim()?.takeUnless { it.isBlank() }?.let { map["photoURL"] = it }
        return map
    }
}
