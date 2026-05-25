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
