package fr.ziyon.campzone.data.camping

import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.RegistrationSubmission
import fr.ziyon.campzone.data.model.TransportationChoice
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** In-memory [CampingService] for ViewModel tests and previews. */
class FakeCampingService(
    initial: List<Camping> = emptyList(),
    attendeesByCamping: Map<String, List<CampingAttendee>> = emptyMap(),
    var shouldFail: Boolean = false,
    private val attendeesFail: Boolean = false,
) : CampingService {

    private val campings = MutableStateFlow(initial)
    private val attendeeStore = attendeesByCamping
        .mapValues { it.value.toMutableList() }
        .toMutableMap()
    val saved = mutableListOf<Camping>()
    val deleted = mutableListOf<String>()
    val submitted = mutableListOf<RegistrationSubmission>()
    val reviewed = mutableListOf<Pair<String, RegistrationApprovalStatus>>()
    val deletedAttendees = mutableListOf<String>()

    override fun observeCampings(): Flow<List<Camping>> =
        if (shouldFail) {
            flow { throw RuntimeException("Stream failed") }
        } else {
            campings.map { list -> list.map(::withAttendees) }
        }

    override suspend fun fetchCamping(id: String): Camping =
        campings.value.firstOrNull { it.id == id }?.let(::withAttendees) ?: error("Camping not found")

    override suspend fun loadAttendees(campingId: String): List<CampingAttendee> {
        if (attendeesFail) throw RuntimeException("Attendees denied")
        return attendeeStore[campingId].orEmpty()
    }

    override suspend fun saveCamping(camping: Camping): Camping {
        saved += camping
        campings.value = campings.value.filterNot { it.id == camping.id } + camping
        return camping
    }

    override suspend fun cancelCamping(id: String): Camping {
        val updated = fetchCamping(id).copy(registrationStatus = CampingRegistrationStatus.Cancelled)
        campings.value = campings.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun deleteCamping(id: String) {
        deleted += id
        campings.value = campings.value.filterNot { it.id == id }
    }

    override suspend fun updateGuidelines(campingId: String, body: String): Camping {
        val updated = fetchCamping(campingId).copy(guidelines = body)
        campings.value = campings.value.map { if (it.id == campingId) updated else it }
        return updated
    }

    override suspend fun submitRegistrations(
        submissions: List<RegistrationSubmission>,
        campingId: String,
        user: fr.ziyon.campzone.data.auth.AuthenticatedUser,
    ): Camping {
        submitted += submissions
        val camping = fetchCamping(campingId)
        if (!camping.acceptsRegistrations) error("Registration is not open for this camping.")
        val list = attendeeStore.getOrPut(campingId) { mutableListOf() }
        val waitlistNewRegistrations = camping.participantCapacity?.let {
            list.count { attendee -> attendee.registrationStatus == RegistrationApprovalStatus.Approved } >= it
        } ?: false

        submissions
            .filterNot { submission -> list.any { it.id == submission.participant.id } }
            .forEach { submission ->
                val participant = submission.participant
                val isSelf = participant.kind == RegistrationParticipantKind.SelfParticipant
                val bookingId = if (submission.transportationChoice == TransportationChoice.ProvidedBus) {
                    "${participant.id}-bus"
                } else {
                    null
                }
                list += CampingAttendee(
                    id = participant.id,
                    userId = if (isSelf) user.uid else participant.id,
                    displayName = if (isSelf) user.preferredDisplayName else participant.displayName,
                    church = if (isSelf) user.church else participant.church,
                    age = if (isSelf) user.age ?: participant.age else participant.age,
                    languages = if (isSelf) {
                        user.preferredLanguage.takeUnless { it.isBlank() }?.let(::listOf).orEmpty()
                    } else {
                        participant.languages
                    },
                    registrationStatus = if (waitlistNewRegistrations) {
                        RegistrationApprovalStatus.Waitlisted
                    } else {
                        RegistrationApprovalStatus.Pending
                    },
                    gender = if (isSelf) user.gender ?: participant.gender else participant.gender,
                    preferredLanguage = if (isSelf) user.preferredLanguage else participant.preferredLanguage,
                    participantKind = participant.kind,
                    guardianId = participant.guardianId,
                    emergencyContactName = participant.emergencyContactName,
                    emergencyContactPhone = participant.emergencyContactPhone,
                    medicalNotes = participant.medicalNotes,
                    guardianConsentAt = participant.guardianConsentAt,
                    transportationChoice = submission.transportationChoice,
                    transportationBookingId = bookingId,
                    transportationOptionId = submission.transportationOptionId,
                    transportationOptionName = submission.transportationOptionName,
                    photoUrl = if (isSelf) user.photoUrl ?: participant.photoUrl else participant.photoUrl,
                )
            }
        return fetchCamping(campingId)
    }

    override suspend fun updateRegistrationStatus(
        attendeeId: String,
        status: RegistrationApprovalStatus,
        campingId: String,
    ): Camping {
        if (shouldFail) error("Registration update failed")
        reviewed += attendeeId to status
        val list = attendeeStore.getOrPut(campingId) { mutableListOf() }
        attendeeStore[campingId] = list
            .map { attendee ->
                if (attendee.id == attendeeId) attendee.copy(registrationStatus = status) else attendee
            }
            .toMutableList()
        return fetchCamping(campingId)
    }

    override suspend fun deleteAttendee(
        attendeeId: String,
        campingId: String,
    ): Camping {
        if (shouldFail) error("Attendee delete failed")
        deletedAttendees += attendeeId
        val list = attendeeStore.getOrPut(campingId) { mutableListOf() }
        val removed = list.removeAll { it.id == attendeeId }
        if (removed) {
            val camping = fetchCamping(campingId)
            val approvedCount = list.count {
                it.registrationStatus == RegistrationApprovalStatus.Approved
            }
            val capacity = camping.participantCapacity
            if (capacity != null && approvedCount < capacity) {
                val waitlistedIndex = list.indexOfFirst {
                    it.registrationStatus == RegistrationApprovalStatus.Waitlisted
                }
                if (waitlistedIndex >= 0) {
                    list[waitlistedIndex] = list[waitlistedIndex].copy(
                        registrationStatus = RegistrationApprovalStatus.Pending,
                    )
                }
            }
        }
        return fetchCamping(campingId)
    }

    private fun withAttendees(camping: Camping): Camping =
        camping.copy(attendees = attendeeStore[camping.id] ?: camping.attendees)
}
