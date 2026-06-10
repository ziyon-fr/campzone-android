package fr.ziyon.campzone.data.guardian

import fr.ziyon.campzone.data.family.ChildParticipant
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CheckInRecord
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.Team
import fr.ziyon.campzone.data.model.TeamMember
import fr.ziyon.campzone.data.model.WinnerRevealPolicy
import java.util.Date

/**
 * Read-only "how is my child doing at camp" surface for guardians. Nothing here
 * is persisted — it is aggregated from collections the guardian can already
 * read (their children's check-in docs, the camp's teams, the public schedule),
 * mirroring the iOS `GuardianUpdate`. Per-program attendance is a leader-facing
 * operational record; the guardian program section intentionally remains the
 * live camp schedule.
 */
data class GuardianUpdatesData(
    /** Fetched child registration docs (per-doc reads), a fallback when the broad
     *  `Camping.attendees` snapshot is incomplete for a guardian. */
    val childRegistrations: List<CampingAttendee> = emptyList(),
    /** Check-in records for the guardian's children only (fetched by doc id). */
    val checkIns: List<CheckInRecord> = emptyList(),
    val teams: List<Team> = emptyList(),
    /** All camp programs, ascending by start time. */
    val programs: List<Program> = emptyList(),
) {
    val sortedPrograms: List<Program> get() = programs.sortedBy { it.startDate }

    fun checkIn(attendeeId: String): CheckInRecord? =
        checkIns.firstOrNull { it.attendeeId == attendeeId }

    /** The team a participant belongs to, matched by `TeamMember.userID`. */
    fun team(userId: String): Team? =
        teams.firstOrNull { team -> team.members.any { it.userId == userId } }

    /** Programs currently in progress (`start <= now < end`). */
    fun currentPrograms(now: Date = Date()): List<Program> =
        sortedPrograms.filter { !now.before(it.startDate) && now.before(it.endDate) }

    /** The next program that has not started yet, if any. */
    fun upcomingProgram(now: Date = Date()): Program? =
        sortedPrograms.firstOrNull { it.startDate.after(now) }
}

/** Derived, per-child presentation snapshot. Pure value. */
data class GuardianChildUpdate(
    val attendee: CampingAttendee,
    val checkIn: CheckInRecord?,
    val team: Team?,
    val teamMember: TeamMember?,
    /** Mirrors the winner-reveal gate so a guardian never sees scores before the
     *  camp's reveal policy allows it. */
    val scoresVisible: Boolean,
) {
    val id: String get() = attendee.id

    val isCheckedIn: Boolean get() = checkIn != null

    /** Personal score, or `null` when scores are hidden or the child has no team. */
    val personalScore: Int?
        get() = if (scoresVisible && teamMember != null) teamMember.personalScore else null

    companion object {
        /** Builds per-child snapshots: every `child` attendee the guardian
         *  registered for this camp (from the camp roster, fetched registration
         *  docs, then the family fallback), sorted by name. */
        fun snapshots(
            camping: Camping,
            guardianId: String,
            data: GuardianUpdatesData,
            fallbackChildren: List<CampingAttendee> = emptyList(),
            now: Date = Date(),
        ): List<GuardianChildUpdate> {
            val scoresVisible = !(camping.winnerRevealPolicy ?: WinnerRevealPolicy())
                .areScoresHidden(camping.endDate, now)

            fun mine(list: List<CampingAttendee>) = list.filter {
                it.participantKind == RegistrationParticipantKind.Child && it.guardianId == guardianId
            }

            val byId = LinkedHashMap<String, CampingAttendee>()
            for (attendee in mine(camping.attendees)) byId[attendee.id] = attendee
            for (attendee in mine(data.childRegistrations)) byId.putIfAbsent(attendee.id, attendee)
            for (attendee in mine(fallbackChildren)) byId.putIfAbsent(attendee.id, attendee)

            return byId.values
                .sortedBy { it.displayName.lowercase() }
                .map { attendee ->
                    val team = data.team(attendee.userId)
                    GuardianChildUpdate(
                        attendee = attendee,
                        checkIn = data.checkIn(attendee.id),
                        team = team,
                        teamMember = team?.members?.firstOrNull { it.userId == attendee.userId },
                        scoresVisible = scoresVisible,
                    )
                }
        }
    }
}

/** Maps a guardian's [ChildParticipant] to an approved child [CampingAttendee]
 *  candidate (used as a fallback when the broad roster read is unavailable). */
fun ChildParticipant.toCampAttendee(): CampingAttendee = CampingAttendee(
    id = id,
    userId = id,
    displayName = displayName,
    church = church,
    age = age,
    languages = languages,
    registrationStatus = RegistrationApprovalStatus.Approved,
    gender = gender,
    preferredLanguage = preferredLanguage,
    participantKind = RegistrationParticipantKind.Child,
    guardianId = guardianId,
    emergencyContactName = emergencyContactName,
    emergencyContactPhone = emergencyContactPhone,
    medicalNotes = medicalNotes,
    guardianConsentAt = guardianConsentAt,
    photoUrl = photoUrl,
)
