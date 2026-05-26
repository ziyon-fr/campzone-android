package fr.ziyon.campzone.data.camping

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingAttendeePayload
import fr.ziyon.campzone.data.model.CampingPayload
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.RegistrationSubmission
import fr.ziyon.campzone.data.model.TransportationBooking
import fr.ziyon.campzone.data.model.TransportationBookingPayload
import fr.ziyon.campzone.data.model.TransportationChoice
import fr.ziyon.campzone.data.model.toCampingAttendeeOrNull
import fr.ziyon.campzone.data.model.toCampingOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Read/write access to `campings/{id}` and its `registrations` subcollection
 * (`02-firestore-schema.md` §3). Mirrors the iOS `CampingServicing` contract.
 * All (de)serialization goes through the hand-mapped [Camping] / [CampingAttendee]
 * helpers — no POJO auto-mapping.
 */
interface CampingService {
    /** Live `campings` collection ordered by `startDate` (camping docs only, no attendees). */
    fun observeCampings(): Flow<List<Camping>>

    /** One-shot fetch of a single camping document. */
    suspend fun fetchCamping(id: String): Camping

    /**
     * Loads the `registrations` subcollection. Falls back to the caller's own
     * registration + the children they registered when the blanket list query
     * is denied by RBAC (mirrors the iOS fallback).
     */
    suspend fun loadAttendees(campingId: String): List<CampingAttendee>

    /** Create/update via the hand-built payload; stamps creator signature on first create. */
    suspend fun saveCamping(camping: Camping): Camping

    /** Writes only `{ registrationStatus: "cancelled", updatedAt }`. */
    suspend fun cancelCamping(id: String): Camping

    /** Hard delete (RBAC: creator or privileged manager). */
    suspend fun deleteCamping(id: String)

    /** Guidelines update path — writes only `{ guidelines, updatedAt }`. */
    suspend fun updateGuidelines(campingId: String, body: String): Camping

    /** B3 registration flow: writes registrations and ticketed transportation bookings in one batch. */
    suspend fun submitRegistrations(
        submissions: List<RegistrationSubmission>,
        campingId: String,
        user: fr.ziyon.campzone.data.auth.AuthenticatedUser,
    ): Camping

    /** Registration review path — writes only `{ registrationStatus, updatedAt }`. */
    suspend fun updateRegistrationStatus(
        attendeeId: String,
        status: RegistrationApprovalStatus,
        campingId: String,
    ): Camping

    /**
     * Hard deletes a registration and cascades to per-camping records keyed by
     * the attendee id: check-in record, transportation bookings, and team
     * membership.
     */
    suspend fun deleteAttendee(
        attendeeId: String,
        campingId: String,
    ): Camping
}

@Singleton
class FirebaseCampingService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : CampingService {

    override fun observeCampings(): Flow<List<Camping>> = callbackFlow {
        val registration = campings()
            .orderBy("startDate")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val campings = snapshot?.documents
                    ?.mapNotNull { document -> document.data?.toCampingOrNull(document.id) }
                    .orEmpty()
                launch {
                    trySend(campings.map { camping ->
                        camping.copy(attendees = loadAttendees(camping.id))
                    })
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun fetchCamping(id: String): Camping {
        val snapshot = campings().document(id).get().await()
        return snapshot.data?.toCampingOrNull(snapshot.id)
            ?.let { it.copy(attendees = loadAttendees(it.id)) }
            ?: error("Camping could not be found.")
    }

    override suspend fun loadAttendees(campingId: String): List<CampingAttendee> {
        val registrations = campings().document(campingId).collection(Registrations)
        return try {
            registrations
                .orderBy("displayName")
                .get()
                .await()
                .documents
                .mapNotNull { it.data?.toCampingAttendeeOrNull(it.id) }
        } catch (denied: FirebaseFirestoreException) {
            // Blanket list denied for non-leadership: fetch only what RBAC allows
            // (own self-registration + children registered by this guardian).
            val uid = auth.currentUser?.uid ?: return emptyList()
            val byId = linkedMapOf<String, CampingAttendee>()

            runCatching { registrations.document(uid).get().await() }
                .getOrNull()
                ?.let { it.data?.toCampingAttendeeOrNull(it.id) }
                ?.let { byId[it.id] = it }

            runCatching { registrations.whereEqualTo("userID", uid).get().await() }
                .getOrNull()
                ?.documents
                ?.mapNotNull { it.data?.toCampingAttendeeOrNull(it.id) }
                ?.forEach { byId[it.id] = it }

            runCatching { registrations.whereEqualTo("guardianID", uid).get().await() }
                .getOrNull()
                ?.documents
                ?.mapNotNull { it.data?.toCampingAttendeeOrNull(it.id) }
                ?.forEach { byId[it.id] = it }

            byId.values.sortedBy { it.displayName.lowercase() }
        }
    }

    override suspend fun saveCamping(camping: Camping): Camping {
        val document = campings().document(camping.id)
        val exists = document.get().await().exists()
        val stamped = if (!exists && camping.createdByUid.isNullOrBlank()) {
            camping.copy(
                createdByUid = auth.currentUser?.uid,
                createdByName = auth.currentUser?.displayName?.takeUnless { it.isBlank() },
            )
        } else {
            camping
        }
        document
            .set(
                CampingPayload.campingPayload(
                    camping = stamped,
                    serverTimestamp = FieldValue.serverTimestamp(),
                    deleteField = FieldValue.delete(),
                    includeCreatedAt = !exists,
                ),
                SetOptions.merge(),
            )
            .await()
        return fetchCamping(camping.id)
    }

    override suspend fun cancelCamping(id: String): Camping {
        campings().document(id)
            .set(CampingPayload.cancelPayload(FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
        return fetchCamping(id)
    }

    override suspend fun deleteCamping(id: String) {
        campings().document(id).delete().await()
    }

    override suspend fun updateGuidelines(campingId: String, body: String): Camping {
        campings().document(campingId)
            .set(
                CampingPayload.guidelinesPayload(body, FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            .await()
        return fetchCamping(campingId)
    }

    override suspend fun submitRegistrations(
        submissions: List<RegistrationSubmission>,
        campingId: String,
        user: fr.ziyon.campzone.data.auth.AuthenticatedUser,
    ): Camping {
        require(submissions.isNotEmpty()) { "Select at least one participant." }

        val campingDocument = campings().document(campingId)
        val campingSnapshot = campingDocument.get().await()
        val camping = campingSnapshot.data?.toCampingOrNull(campingSnapshot.id)
            ?: error("Camping could not be found.")
        require(camping.acceptsRegistrations) {
            "Registration is not open for this camping."
        }

        val registrations = campingDocument.collection(Registrations)
        val existingRegistrationSnapshot = registrations.get().await()
        val existingIds = existingRegistrationSnapshot.documents.map { it.id }.toSet()
        val approvedCount = existingRegistrationSnapshot.documents.count {
            it.getString("registrationStatus") == RegistrationApprovalStatus.Approved.wireValue
        }
        val waitlistNewRegistrations = camping.participantCapacity?.let { approvedCount >= it } ?: false

        val batch = firestore.batch()
        var didWrite = false
        submissions
            .filterNot { it.participant.id in existingIds }
            .forEach { submission ->
                val bookingId = if (submission.transportationChoice == TransportationChoice.ProvidedBus) {
                    "${submission.participant.id}-bus"
                } else {
                    null
                }
                val attendee = attendee(
                    submission = submission,
                    user = user,
                    bookingId = bookingId,
                    status = if (waitlistNewRegistrations) {
                        RegistrationApprovalStatus.Waitlisted
                    } else {
                        RegistrationApprovalStatus.Pending
                    },
                )
                val registrationPayload = CampingAttendeePayload.registrationPayload(
                    attendee = attendee,
                    serverTimestamp = FieldValue.serverTimestamp(),
                    includeCreatedAt = true,
                ).toMutableMap().apply {
                    put("campingID", campingId)
                }
                batch.set(
                    registrations.document(attendee.id),
                    registrationPayload,
                    SetOptions.merge(),
                )

                if (bookingId != null) {
                    val booking = TransportationBooking(
                        id = bookingId,
                        campingId = campingId,
                        registrationId = attendee.id,
                        participantId = attendee.id,
                        participantKind = attendee.participantKind,
                        participantName = attendee.displayName,
                        guardianId = attendee.guardianId,
                        userId = attendee.userId,
                        transportationOptionId = attendee.transportationOptionId,
                        transportationOptionName = attendee.transportationOptionName,
                        validFrom = camping.startDate,
                        validUntil = camping.endDate,
                        ticketToken = makeTicketToken(),
                    )
                    batch.set(
                        campingDocument.collection(TransportationBookings).document(booking.id),
                        TransportationBookingPayload.createPayload(
                            booking = booking,
                            serverTimestamp = FieldValue.serverTimestamp(),
                        ),
                        SetOptions.merge(),
                    )
                }

                didWrite = true
            }

        if (didWrite) {
            batch.commit().await()
        }
        return fetchCamping(campingId)
    }

    override suspend fun updateRegistrationStatus(
        attendeeId: String,
        status: RegistrationApprovalStatus,
        campingId: String,
    ): Camping {
        campings().document(campingId)
            .collection(Registrations)
            .document(attendeeId)
            .set(
                CampingAttendeePayload.statusUpdatePayload(
                    status = status,
                    serverTimestamp = FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
        return fetchCamping(campingId)
    }

    override suspend fun deleteAttendee(
        attendeeId: String,
        campingId: String,
    ): Camping {
        val campingDocument = campings().document(campingId)
        val registrations = campingDocument.collection(Registrations)

        registrations.document(attendeeId).delete().await()

        runCatching {
            campingDocument.collection(CheckIns)
                .document(attendeeId)
                .delete()
                .await()
        }

        runCatching {
            campingDocument.collection(TransportationBookings)
                .whereEqualTo("participantID", attendeeId)
                .get()
                .await()
                .documents
                .forEach { it.reference.delete().await() }
        }

        runCatching {
            campingDocument.collection(Teams)
                .get()
                .await()
                .documents
                .forEach { teamDocument ->
                    @Suppress("UNCHECKED_CAST")
                    val members = teamDocument.data?.get("members") as? List<Map<String, Any?>>
                        ?: return@forEach
                    val remaining = members.filter { member ->
                        member["id"] != attendeeId && member["userID"] != attendeeId
                    }
                    if (remaining.size != members.size) {
                        teamDocument.reference.update(
                            mapOf(
                                "members" to remaining,
                                "memberUserIDs" to remaining.mapNotNull { member ->
                                    (member["userID"] ?: member["id"]) as? String
                                },
                                "updatedAt" to FieldValue.serverTimestamp(),
                            ),
                        ).await()
                    }
                }
        }

        promoteWaitlistedAttendeeIfSpotOpened(campingDocument)
        return fetchCamping(campingId)
    }

    private suspend fun promoteWaitlistedAttendeeIfSpotOpened(
        campingDocument: com.google.firebase.firestore.DocumentReference,
    ) {
        val capacity = campingDocument.get().await().getLong("participantCapacity")?.toInt()
            ?: return
        val registrations = campingDocument.collection(Registrations)
        val approvedCount = registrations
            .whereEqualTo("registrationStatus", RegistrationApprovalStatus.Approved.wireValue)
            .get()
            .await()
            .size()
        if (approvedCount >= capacity) return

        val nextWaitlisted = registrations
            .whereEqualTo("registrationStatus", RegistrationApprovalStatus.Waitlisted.wireValue)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?: return

        nextWaitlisted.reference
            .set(
                CampingAttendeePayload.statusUpdatePayload(
                    status = RegistrationApprovalStatus.Pending,
                    serverTimestamp = FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    private fun attendee(
        submission: RegistrationSubmission,
        user: fr.ziyon.campzone.data.auth.AuthenticatedUser,
        bookingId: String?,
        status: RegistrationApprovalStatus,
    ): CampingAttendee {
        val participant = submission.participant
        val isSelfRegistration = participant.kind == RegistrationParticipantKind.SelfParticipant
        val age = if (isSelfRegistration) {
            user.age ?: participant.age.takeIf { it > 0 } ?: AdultFallbackAge
        } else {
            participant.age
        }
        val preferredLanguage = if (isSelfRegistration) {
            user.preferredLanguage.ifBlank { participant.preferredLanguage }
        } else {
            participant.preferredLanguage
        }
        val languages = if (isSelfRegistration) {
            user.preferredLanguage.takeUnless { it.isBlank() }?.let(::listOf)
                ?: participant.languages
        } else {
            participant.languages
        }

        return CampingAttendee(
            id = participant.id,
            userId = if (isSelfRegistration) user.uid else participant.id,
            displayName = if (isSelfRegistration) user.preferredDisplayName else participant.displayName,
            church = if (isSelfRegistration) {
                user.church.ifBlank { participant.church }
            } else {
                participant.church
            },
            age = age,
            gender = if (isSelfRegistration) user.gender ?: participant.gender else participant.gender,
            preferredLanguage = preferredLanguage,
            languages = languages,
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
            registrationStatus = status,
            photoUrl = if (isSelfRegistration) user.photoUrl ?: participant.photoUrl else participant.photoUrl,
        )
    }

    private fun campings() = firestore.collection(Campings)

    private companion object {
        const val Campings = "campings"
        const val Registrations = "registrations"
        const val CheckIns = "checkIns"
        const val TransportationBookings = "transportationBookings"
        const val Teams = "teams"
        const val AdultFallbackAge = 18

        fun makeTicketToken(): String = "${UUID.randomUUID()}-${UUID.randomUUID()}"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CampingBindings {
    @Binds
    abstract fun bindCampingService(service: FirebaseCampingService): CampingService
}
