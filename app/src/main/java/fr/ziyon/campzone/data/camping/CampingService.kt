package fr.ziyon.campzone.data.camping

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingAttendeePayload
import fr.ziyon.campzone.data.model.CampingPayload
import fr.ziyon.campzone.data.model.CampingTemplateCloneDayOffset
import fr.ziyon.campzone.data.model.CampingTemplateCloneRequest
import fr.ziyon.campzone.data.model.DateKeys
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.RegistrationSubmission
import fr.ziyon.campzone.data.model.RegistrationTransportPayload
import fr.ziyon.campzone.data.model.TransportationBooking
import fr.ziyon.campzone.data.model.TransportationBookingPayload
import fr.ziyon.campzone.data.model.TransportationMode
import fr.ziyon.campzone.data.model.toCampingAttendeeOrNull
import fr.ziyon.campzone.data.model.toCampingOrNull
import fr.ziyon.campzone.data.model.dateValue
import fr.ziyon.campzone.data.model.templateClone
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import fr.ziyon.campzone.data.model.WinnerRevealPolicy

/**
 * Read/write access to `campings/{id}` and its `registrations` subcollection
 * (`02-firestore-schema.md` §3). Mirrors the iOS `CampingServicing` contract.
 * All (de)serialization goes through the hand-mapped [Camping] / [CampingAttendee]
 * helpers - no POJO auto-mapping.
 */
interface CampingService {
    /** Live `campings` collection ordered by `startDate` (camping docs only, no attendees). */
    fun observeCampings(): Flow<List<Camping>>

    /** IDs where the currently cached camping data says [userId] is approved. No Firestore query. */
    fun approvedCampingIds(forUserId: String?): Set<String>

    /** Live IDs where [userId] has an approved self/guardian registration. */
    fun observeApprovedCampingIds(forUserId: String?): Flow<Set<String>>

    /** Live registrations owned by [userId] for one camping. */
    fun observeUserAttendees(campingId: String, userId: String?): Flow<List<CampingAttendee>>

    /** Cached camping from an already-running observer/fetch, if present. No Firestore query. */
    fun cachedCamping(id: String): Camping?

    /** One-shot fetch of a single camping document. */
    suspend fun fetchCamping(id: String): Camping

    /**
     * Live single camping document (camping fields only — attendees are loaded
     * separately so the detail VM controls when the registrations query runs).
     * Powers real-time `winnerRevealPolicy`/score-visibility across the detail,
     * teams, and games screens.
     */
    fun observeCamping(id: String): Flow<Camping>

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

    /** Guidelines update path - writes only `{ guidelines, updatedAt }`. */
    suspend fun updateGuidelines(campingId: String, body: String): Camping

    /** Home pin path - writes only `{ isFeatured, updatedAt }` as a merge. */
    suspend fun setFeatured(campingId: String, isFeatured: Boolean): Camping

    /**
     * Creates a fresh camping from reusable setup content: schedule, team
     * shells, songbook, and guidelines. Live camp data is intentionally reset.
     */
    suspend fun cloneCampingTemplate(request: CampingTemplateCloneRequest): Camping

    /** Winner-reveal path - writes only `{ winnerRevealPolicy, updatedAt }`. Gated by `canRevealWinners`. */
    suspend fun updateWinnerReveal(campingId: String, policy: WinnerRevealPolicy): Camping

    /** B3 registration flow: writes registrations and ticketed transportation bookings in one batch. */
    suspend fun submitRegistrations(
        submissions: List<RegistrationSubmission>,
        campingId: String,
        user: fr.ziyon.campzone.data.auth.AuthenticatedUser,
    ): Camping

    /** Registration review path - writes only `{ registrationStatus, updatedAt }`. */
    suspend fun updateRegistrationStatus(
        attendeeId: String,
        status: RegistrationApprovalStatus,
        campingId: String,
    ): Camping

    /**
     * Vehicle QR transport-intent path - writes only the deployed rules
     * allowlist: transportationMode, vehicleID, isDriver, needsTransportHelp,
     * transportationNotes, updatedAt.
     */
    suspend fun updateRegistrationTransport(
        campingId: String,
        attendeeId: String,
        transportationMode: TransportationMode?,
        vehicleId: String?,
        isDriver: Boolean,
        needsTransportHelp: Boolean,
        notes: String?,
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

private class FirestoreTemplateCloneBatch(
    private val firestore: FirebaseFirestore,
) {
    private var batch: WriteBatch = firestore.batch()
    private var writeCount = 0

    fun setData(
        document: com.google.firebase.firestore.DocumentReference,
        data: Map<String, Any?>,
    ) {
        check(writeCount < MAX_WRITES) {
            "This template has too much content to clone in one pass. Reduce the template size and try again."
        }
        batch.set(document, data, SetOptions.merge())
        writeCount += 1
    }

    suspend fun commit() {
        if (writeCount == 0) return
        batch.commit().await()
        batch = firestore.batch()
        writeCount = 0
    }

    private companion object {
        const val MAX_WRITES = 480
    }
}

@Singleton
class FirebaseCampingService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : CampingService {
    @Volatile
    private var latestCampings: List<Camping> = emptyList()

    override fun observeCampings(): Flow<List<Camping>> = callbackFlow {
        val campingDocuments = linkedMapOf<String, Camping>()
        val attendeesByCampingId = mutableMapOf<String, List<CampingAttendee>>()
        val attendeeListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()

        fun publish() {
            val loaded = campingDocuments.values.map { camping ->
                camping.copy(attendees = attendeesByCampingId[camping.id].orEmpty())
            }
            cacheCampings(loaded)
            trySend(loaded)
        }

        val registration = campings()
            .orderBy("startDate")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val activeIds = snapshot?.documents.orEmpty().mapTo(mutableSetOf()) { it.id }
                (campingDocuments.keys - activeIds).forEach { removedId ->
                    campingDocuments.remove(removedId)
                    attendeesByCampingId.remove(removedId)
                    attendeeListeners.remove(removedId)?.remove()
                }
                snapshot?.documents.orEmpty().forEach { document ->
                    val camping = document.data?.toCampingOrNull(document.id) ?: return@forEach
                    campingDocuments[camping.id] = camping
                    if (attendeeListeners[camping.id] == null) {
                        attendeeListeners[camping.id] = document.reference
                            .collection(Registrations)
                            .orderBy("displayName")
                            .addSnapshotListener { attendeeSnapshot, attendeeError ->
                                if (attendeeError != null) {
                                    launch {
                                        attendeesByCampingId[camping.id] = loadAttendees(camping.id)
                                        publish()
                                    }
                                    return@addSnapshotListener
                                }
                                attendeesByCampingId[camping.id] = attendeeSnapshot?.documents
                                    ?.mapNotNull { it.data?.toCampingAttendeeOrNull(it.id) }
                                    .orEmpty()
                                publish()
                            }
                    }
                }
                publish()
            }
        awaitClose {
            registration.remove()
            attendeeListeners.values.forEach { it.remove() }
        }
    }

    override fun approvedCampingIds(forUserId: String?): Set<String> =
        latestCampings
            .filter { camping -> camping.hasApprovedRegistrationForUser(forUserId) }
            .mapTo(mutableSetOf()) { it.id }

    override fun observeApprovedCampingIds(forUserId: String?): Flow<Set<String>> {
        val uid = forUserId?.takeUnless { it.isBlank() } ?: return flowOf(emptySet())
        return combine(
            observeRegistrationCampingIds(
                field = UserId,
                userId = uid,
                approvedOnly = true,
            ),
            observeRegistrationCampingIds(
                field = GuardianId,
                userId = uid,
                approvedOnly = true,
            ),
        ) { owned, guardian -> owned + guardian }
    }

    override fun observeUserAttendees(campingId: String, userId: String?): Flow<List<CampingAttendee>> {
        val uid = userId?.takeUnless { it.isBlank() } ?: return flowOf(emptyList())
        return combine(
            observeRegistrationRows(campingId, UserId, uid),
            observeRegistrationRows(campingId, GuardianId, uid),
        ) { owned, guardian ->
            (owned + guardian)
                .distinctBy { it.id }
                .sortedBy { it.displayName.lowercase() }
        }
    }

    override fun cachedCamping(id: String): Camping? =
        latestCampings.firstOrNull { it.id == id }

    override suspend fun fetchCamping(id: String): Camping {
        val snapshot = campings().document(id).get().await()
        return snapshot.data?.toCampingOrNull(snapshot.id)
            ?.let { it.copy(attendees = loadAttendees(it.id)) }
            ?.also(::cacheCamping)
            ?: error("Camping could not be found.")
    }

    override fun observeCamping(id: String): Flow<Camping> = callbackFlow {
        val registration = campings().document(id)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val camping = snapshot?.data?.toCampingOrNull(snapshot.id)
                if (camping != null) trySend(camping)
            }
        awaitClose { registration.remove() }
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

    private fun observeRegistrationCampingIds(
        field: String,
        userId: String,
        approvedOnly: Boolean,
    ): Flow<Set<String>> = callbackFlow {
        var query: Query = firestore.collectionGroup(Registrations)
            .whereEqualTo(field, userId)
        if (approvedOnly) {
            query = query.whereEqualTo(RegistrationStatus, RegistrationApprovalStatus.Approved.wireValue)
        }
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val campingIds = snapshot?.documents
                ?.mapNotNull { document -> document.reference.parent.parent?.id }
                ?.toSet()
                .orEmpty()
            trySend(campingIds)
        }
        awaitClose { registration.remove() }
    }

    private fun observeRegistrationRows(
        campingId: String,
        field: String,
        userId: String,
    ): Flow<List<CampingAttendee>> = callbackFlow {
        val registration = campings().document(campingId)
            .collection(Registrations)
            .whereEqualTo(field, userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val attendees = snapshot?.documents
                    ?.mapNotNull { it.data?.toCampingAttendeeOrNull(it.id) }
                    .orEmpty()
                trySend(attendees)
            }
        awaitClose { registration.remove() }
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

    override suspend fun setFeatured(campingId: String, isFeatured: Boolean): Camping {
        campings().document(campingId)
            .set(
                CampingPayload.featuredPayload(isFeatured, FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            .await()
        return fetchCamping(campingId)
    }

    override suspend fun cloneCampingTemplate(request: CampingTemplateCloneRequest): Camping {
        val sourceSnapshot = campings().document(request.sourceCampingId).get().await()
        val source = sourceSnapshot.data?.toCampingOrNull(sourceSnapshot.id)
            ?: error("Template camp could not be found.")
        val cloned = source.templateClone(request)
        val dayOffset = CampingTemplateCloneDayOffset.daysBetween(
            source.startDate,
            request.startDate,
        )
        val currentUser = auth.currentUser
        val stamped = cloned.copy(
            createdByUid = currentUser?.uid,
            createdByName = currentUser?.displayName?.takeUnless { it.isBlank() },
        )

        val writer = FirestoreTemplateCloneBatch(firestore)
        val campingPayload = CampingPayload.campingPayload(
            camping = stamped,
            serverTimestamp = FieldValue.serverTimestamp(),
            deleteField = FieldValue.delete(),
            includeCreatedAt = true,
        ).toMutableMap().apply {
            put("guidelines", stamped.guidelines)
            put("isFeatured", false)
        }
        writer.setData(campings().document(stamped.id), campingPayload)

        if (request.options.includeSchedule) {
            cloneScheduleContent(
                sourceCampingId = request.sourceCampingId,
                targetCampingId = stamped.id,
                dayOffset = dayOffset,
                writer = writer,
            )
        }
        if (request.options.includeTeams) {
            cloneTeamShells(
                sourceCampingId = request.sourceCampingId,
                targetCampingId = stamped.id,
                writer = writer,
            )
        }
        if (request.options.includeSongbook) {
            cloneSongbook(
                sourceCampingId = request.sourceCampingId,
                targetCampingId = stamped.id,
                writer = writer,
            )
        }

        writer.commit()
        return fetchCamping(stamped.id)
    }

    override suspend fun updateWinnerReveal(campingId: String, policy: WinnerRevealPolicy): Camping {
        campings().document(campingId)
            .update(CampingPayload.winnerRevealPolicyPayload(policy, FieldValue.serverTimestamp()))
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
                val selectedOption = camping.transportationOption(submission.transportationOptionId)
                val bookingId = if (selectedOption?.issuesTicket == true) {
                    "${submission.participant.id}-transport"
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

    override suspend fun updateRegistrationTransport(
        campingId: String,
        attendeeId: String,
        transportationMode: TransportationMode?,
        vehicleId: String?,
        isDriver: Boolean,
        needsTransportHelp: Boolean,
        notes: String?,
    ): Camping {
        campings().document(campingId)
            .collection(Registrations)
            .document(attendeeId)
            .set(
                RegistrationTransportPayload.payload(
                    transportationMode = transportationMode,
                    vehicleId = vehicleId,
                    isDriver = isDriver,
                    needsTransportHelp = needsTransportHelp,
                    notes = notes,
                    serverTimestamp = FieldValue.serverTimestamp(),
                    deleteField = FieldValue.delete(),
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

    private suspend fun cloneScheduleContent(
        sourceCampingId: String,
        targetCampingId: String,
        dayOffset: Int,
        writer: FirestoreTemplateCloneBatch,
    ) {
        val sourceSchedule = campings().document(sourceCampingId)
            .collection(Schedule)
            .document(Config)
        val targetSchedule = campings().document(targetCampingId)
            .collection(Schedule)
            .document(Config)

        val scheduleData = (sourceSchedule.get().await().data ?: emptyMap()).toMutableMap()
        scheduleData["campingID"] = targetCampingId
        stampClonedData(scheduleData)
        writer.setData(targetSchedule, scheduleData)

        val daySnapshots = sourceSchedule.collection(Days).get().await()
        for (daySnapshot in daySnapshots.documents) {
            val dayData = (daySnapshot.data ?: emptyMap()).toMutableMap()
            val sourceDate = dayData.dateValue("date")
                ?: DateKeys.dateFromCampDayId(daySnapshot.id)
                ?: Date()
            val targetDate = CampingTemplateCloneDayOffset.shift(sourceDate, dayOffset)
            val targetDayId = DateKeys.campDayId(targetCampingId, targetDate)
            dayData["campingID"] = targetCampingId
            dayData["date"] = targetDate
            stampClonedData(dayData)

            val targetDay = targetSchedule.collection(Days).document(targetDayId)
            writer.setData(targetDay, dayData)

            val programSnapshots = daySnapshot.reference.collection(Programs).get().await()
            for (programSnapshot in programSnapshots.documents) {
                val programData = (programSnapshot.data ?: emptyMap()).toMutableMap()
                val sourceStart = programData.dateValue("startDate") ?: sourceDate
                val sourceEnd = programData.dateValue("endDate") ?: sourceStart
                programData["campingID"] = targetCampingId
                programData["campDayID"] = targetDayId
                programData["startDate"] = CampingTemplateCloneDayOffset.shift(sourceStart, dayOffset)
                programData["endDate"] = CampingTemplateCloneDayOffset.shift(sourceEnd, dayOffset)
                programData.remove("venuePointID")
                programData.remove("linkedGameID")
                stampClonedData(programData)

                writer.setData(
                    targetDay.collection(Programs).document(programSnapshot.id),
                    programData,
                )
            }
        }
    }

    private suspend fun cloneTeamShells(
        sourceCampingId: String,
        targetCampingId: String,
        writer: FirestoreTemplateCloneBatch,
    ) {
        val teamSnapshots = campings().document(sourceCampingId)
            .collection(Teams)
            .get()
            .await()
        for (teamSnapshot in teamSnapshots.documents) {
            val data = (teamSnapshot.data ?: emptyMap()).toMutableMap()
            data["campingID"] = targetCampingId
            data["points"] = 0
            data["penalties"] = emptyList<Map<String, Any?>>()
            data["members"] = emptyList<Map<String, Any?>>()
            data["memberUserIDs"] = emptyList<String>()
            stampClonedData(data)
            writer.setData(
                campings().document(targetCampingId).collection(Teams).document(teamSnapshot.id),
                data,
            )
        }
    }

    private suspend fun cloneSongbook(
        sourceCampingId: String,
        targetCampingId: String,
        writer: FirestoreTemplateCloneBatch,
    ) {
        val songSnapshots = campings().document(sourceCampingId)
            .collection(Songs)
            .orderBy("orderIndex")
            .limit(SONGBOOK_CLONE_LIMIT)
            .get()
            .await()
        for (songSnapshot in songSnapshots.documents) {
            val data = (songSnapshot.data ?: emptyMap()).toMutableMap()
            data["favoriteUserIDs"] = emptyList<String>()
            stampClonedData(data)
            writer.setData(
                campings().document(targetCampingId).collection(Songs).document(songSnapshot.id),
                data,
            )
        }
    }

    private fun stampClonedData(data: MutableMap<String, Any?>) {
        data["createdAt"] = FieldValue.serverTimestamp()
        data["updatedAt"] = FieldValue.serverTimestamp()
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

    private fun cacheCampings(campings: List<Camping>) {
        latestCampings = campings
    }

    private fun cacheCamping(camping: Camping) {
        latestCampings = latestCampings
            .filterNot { it.id == camping.id }
            .plus(camping)
    }

    private companion object {
        const val Campings = "campings"
        const val Registrations = "registrations"
        const val CheckIns = "checkIns"
        const val TransportationBookings = "transportationBookings"
        const val Teams = "teams"
        const val UserId = "userID"
        const val GuardianId = "guardianID"
        const val RegistrationStatus = "registrationStatus"
        const val Schedule = "schedule"
        const val Config = "config"
        const val Days = "days"
        const val Programs = "programs"
        const val Songs = "songs"
        const val AdultFallbackAge = 18
        const val SONGBOOK_CLONE_LIMIT = 500L

        fun makeTicketToken(): String = "${UUID.randomUUID()}-${UUID.randomUUID()}"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CampingBindings {
    @Binds
    abstract fun bindCampingService(service: FirebaseCampingService): CampingService
}

/** Minimal in-memory [CampingService] for Compose previews and fakes in the main source set. */
class PreviewCampingService(private val camping: Camping? = null) : CampingService {
    override fun observeCampings() = kotlinx.coroutines.flow.flowOf(listOfNotNull(camping))
    override fun approvedCampingIds(forUserId: String?) =
        listOfNotNull(camping)
            .filter { it.hasApprovedRegistrationForUser(forUserId) }
            .mapTo(mutableSetOf()) { it.id }
    override fun observeApprovedCampingIds(forUserId: String?) =
        kotlinx.coroutines.flow.flowOf(approvedCampingIds(forUserId))
    override fun observeUserAttendees(campingId: String, userId: String?) =
        kotlinx.coroutines.flow.flowOf(
            camping
                ?.takeIf { it.id == campingId }
                ?.attendees
                ?.filter { attendee ->
                    attendee.userId == userId ||
                        attendee.guardianId == userId ||
                        attendee.id == userId
                }
                .orEmpty(),
        )
    override fun cachedCamping(id: String) = camping?.takeIf { it.id == id }
    override suspend fun fetchCamping(id: String) = camping ?: error("No camping")
    override fun observeCamping(id: String) = kotlinx.coroutines.flow.flowOf(camping ?: error("No camping"))
    override suspend fun loadAttendees(campingId: String) = emptyList<fr.ziyon.campzone.data.model.CampingAttendee>()
    override suspend fun saveCamping(camping: Camping) = camping
    override suspend fun cancelCamping(id: String) = camping ?: error("No camping")
    override suspend fun deleteCamping(id: String) = Unit
    override suspend fun updateGuidelines(campingId: String, body: String) =
        camping?.copy(guidelines = body) ?: error("No camping")
    override suspend fun setFeatured(campingId: String, isFeatured: Boolean) =
        camping?.copy(isFeatured = isFeatured) ?: error("No camping")
    override suspend fun cloneCampingTemplate(request: CampingTemplateCloneRequest) =
        camping?.templateClone(request) ?: error("No camping")
    override suspend fun updateWinnerReveal(campingId: String, policy: WinnerRevealPolicy) =
        camping?.copy(winnerRevealPolicy = policy) ?: error("No camping")
    override suspend fun submitRegistrations(
        submissions: List<fr.ziyon.campzone.data.model.RegistrationSubmission>,
        campingId: String,
        user: fr.ziyon.campzone.data.auth.AuthenticatedUser,
    ) = camping ?: error("No camping")
    override suspend fun updateRegistrationStatus(
        attendeeId: String,
        status: fr.ziyon.campzone.data.model.RegistrationApprovalStatus,
        campingId: String,
    ) = camping ?: error("No camping")
    override suspend fun updateRegistrationTransport(
        campingId: String,
        attendeeId: String,
        transportationMode: fr.ziyon.campzone.data.model.TransportationMode?,
        vehicleId: String?,
        isDriver: Boolean,
        needsTransportHelp: Boolean,
        notes: String?,
    ) = camping ?: error("No camping")
    override suspend fun deleteAttendee(attendeeId: String, campingId: String) = camping ?: error("No camping")
}
