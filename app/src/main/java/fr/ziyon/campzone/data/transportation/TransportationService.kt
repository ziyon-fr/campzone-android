package fr.ziyon.campzone.data.transportation

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingTransportationOption
import fr.ziyon.campzone.data.model.TransportationBoardingStatus
import fr.ziyon.campzone.data.model.TransportationBooking
import fr.ziyon.campzone.data.model.TransportationBookingPayload
import fr.ziyon.campzone.data.model.TransportationCheckpoint
import fr.ziyon.campzone.data.model.TransportationLeg
import fr.ziyon.campzone.data.model.TransportationPaymentStatus
import fr.ziyon.campzone.data.model.TransportationScanEvent
import fr.ziyon.campzone.data.model.toTransportationBookingOrNull
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

interface TransportationService {
    suspend fun loadBookings(campingId: String): List<TransportationBooking>
    suspend fun loadUserBookings(campingId: String, userId: String): List<TransportationBooking>
    suspend fun booking(campingId: String, bookingId: String): TransportationBooking
    suspend fun createBooking(
        campingId: String,
        attendee: CampingAttendee,
        option: CampingTransportationOption?,
        validFrom: Date,
        validUntil: Date,
    ): TransportationBooking
    suspend fun updatePaymentStatus(
        campingId: String,
        bookingId: String,
        status: TransportationPaymentStatus,
        reviewerId: String,
    ): TransportationBooking
    suspend fun cancelBooking(
        campingId: String,
        bookingId: String,
        reviewerId: String,
        reason: String?,
    ): TransportationBooking
    suspend fun markBoarded(
        campingId: String,
        bookingId: String,
        reviewerId: String,
        leg: TransportationLeg,
        reviewerName: String?,
        location: String?,
    ): TransportationBooking
    suspend fun markArrived(
        campingId: String,
        bookingId: String,
        reviewerId: String,
        leg: TransportationLeg,
        reviewerName: String?,
        location: String?,
    ): TransportationBooking
}

@Singleton
class FirestoreTransportationService @Inject constructor(
    private val firestore: FirebaseFirestore,
) : TransportationService {
    override suspend fun loadBookings(campingId: String): List<TransportationBooking> =
        collection(campingId)
            .orderBy(Field.ParticipantName)
            .get()
            .await()
            .documents
            .mapNotNull { snapshot -> snapshot.data?.toTransportationBookingOrNull(snapshot.id) }

    override suspend fun loadUserBookings(campingId: String, userId: String): List<TransportationBooking> {
        val user = collection(campingId)
            .whereEqualTo(Field.UserId, userId)
            .get()
            .await()
            .documents
        val guardian = collection(campingId)
            .whereEqualTo(Field.GuardianId, userId)
            .get()
            .await()
            .documents
        return (user + guardian)
            .mapNotNull { snapshot -> snapshot.data?.toTransportationBookingOrNull(snapshot.id) }
            .distinctBy { it.id }
            .sortedBy { it.participantName.lowercase() }
    }

    override suspend fun booking(campingId: String, bookingId: String): TransportationBooking {
        val snapshot = collection(campingId).document(bookingId).get().await()
        return snapshot.data?.toTransportationBookingOrNull(snapshot.id)
            ?: error("Transportation booking could not be found.")
    }

    override suspend fun createBooking(
        campingId: String,
        attendee: CampingAttendee,
        option: CampingTransportationOption?,
        validFrom: Date,
        validUntil: Date,
    ): TransportationBooking {
        val bookingId = "${attendee.id}-bus"
        val booking = TransportationBooking(
            id = bookingId,
            campingId = campingId,
            registrationId = attendee.id,
            participantId = attendee.id,
            participantKind = attendee.participantKind,
            participantName = attendee.displayName,
            guardianId = attendee.guardianId,
            userId = attendee.userId,
            transportationOptionId = option?.id ?: attendee.transportationOptionId,
            transportationOptionName = option?.resolvedName ?: attendee.transportationOptionName,
            validFrom = validFrom,
            validUntil = validUntil,
            ticketToken = makeTicketToken(),
        )
        // RBAC create literal: always written `unpaid`. Free options are flipped
        // to `waived` by the manager via the update path (see ViewModel).
        collection(campingId)
            .document(bookingId)
            .set(
                TransportationBookingPayload.createPayload(
                    booking = booking,
                    serverTimestamp = FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
        return booking(campingId, bookingId)
    }

    override suspend fun updatePaymentStatus(
        campingId: String,
        bookingId: String,
        status: TransportationPaymentStatus,
        reviewerId: String,
    ): TransportationBooking {
        collection(campingId)
            .document(bookingId)
            .set(
                TransportationBookingPayload.updatePaymentStatusPayload(
                    status = status,
                    reviewerId = reviewerId,
                    serverTimestamp = FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
        return booking(campingId, bookingId)
    }

    override suspend fun cancelBooking(
        campingId: String,
        bookingId: String,
        reviewerId: String,
        reason: String?,
    ): TransportationBooking {
        collection(campingId)
            .document(bookingId)
            .set(
                TransportationBookingPayload.cancelPayload(
                    reviewerId = reviewerId,
                    reason = reason,
                    serverTimestamp = FieldValue.serverTimestamp(),
                    deleteField = FieldValue.delete(),
                ),
                SetOptions.merge(),
            )
            .await()
        return booking(campingId, bookingId)
    }

    override suspend fun markBoarded(
        campingId: String,
        bookingId: String,
        reviewerId: String,
        leg: TransportationLeg,
        reviewerName: String?,
        location: String?,
    ): TransportationBooking {
        collection(campingId)
            .document(bookingId)
            .set(
                TransportationBookingPayload.markBoardedPayload(
                    leg = leg,
                    reviewerId = reviewerId,
                    reviewerName = reviewerName,
                    location = location,
                    now = Date(),
                    serverTimestamp = FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
        return booking(campingId, bookingId)
    }

    override suspend fun markArrived(
        campingId: String,
        bookingId: String,
        reviewerId: String,
        leg: TransportationLeg,
        reviewerName: String?,
        location: String?,
    ): TransportationBooking {
        collection(campingId)
            .document(bookingId)
            .set(
                TransportationBookingPayload.markArrivedPayload(
                    leg = leg,
                    reviewerId = reviewerId,
                    reviewerName = reviewerName,
                    location = location,
                    now = Date(),
                    serverTimestamp = FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
        return booking(campingId, bookingId)
    }

    private fun collection(campingId: String) = firestore
        .collection(Collection.Campings)
        .document(campingId)
        .collection(Collection.TransportationBookings)

    private object Collection {
        const val Campings = "campings"
        const val TransportationBookings = "transportationBookings"
    }

    private object Field {
        const val UserId = "userID"
        const val GuardianId = "guardianID"
        const val ParticipantName = "participantName"
    }

    private companion object {
        fun makeTicketToken(): String = "${UUID.randomUUID()}-${UUID.randomUUID()}"
    }
}

class FakeTransportationService(
    bookings: List<TransportationBooking> = emptyList(),
    var shouldFail: Boolean = false,
) : TransportationService {
    private val store = bookings.associateBy { it.id }.toMutableMap()

    override suspend fun loadBookings(campingId: String): List<TransportationBooking> {
        checkFailure()
        return store.values.filter { it.campingId == campingId }.sortedBy { it.participantName.lowercase() }
    }

    override suspend fun loadUserBookings(campingId: String, userId: String): List<TransportationBooking> {
        checkFailure()
        return loadBookings(campingId)
            .filter { it.userId == userId || it.guardianId == userId }
            .distinctBy { it.id }
    }

    override suspend fun booking(campingId: String, bookingId: String): TransportationBooking {
        checkFailure()
        return store[bookingId]?.takeIf { it.campingId == campingId }
            ?: error("Transportation booking could not be found.")
    }

    override suspend fun createBooking(
        campingId: String,
        attendee: CampingAttendee,
        option: CampingTransportationOption?,
        validFrom: Date,
        validUntil: Date,
    ): TransportationBooking {
        checkFailure()
        val booking = TransportationBooking(
            id = "${attendee.id}-bus",
            campingId = campingId,
            registrationId = attendee.id,
            participantId = attendee.id,
            participantKind = attendee.participantKind,
            participantName = attendee.displayName,
            guardianId = attendee.guardianId,
            userId = attendee.userId,
            transportationOptionId = option?.id ?: attendee.transportationOptionId,
            transportationOptionName = option?.resolvedName ?: attendee.transportationOptionName,
            validFrom = validFrom,
            validUntil = validUntil,
            ticketToken = UUID.randomUUID().toString(),
        )
        store[booking.id] = booking
        return booking
    }

    override suspend fun updatePaymentStatus(
        campingId: String,
        bookingId: String,
        status: TransportationPaymentStatus,
        reviewerId: String,
    ): TransportationBooking {
        checkFailure()
        val now = Date()
        val booking = booking(campingId, bookingId).copy(
            paymentStatus = status,
            paymentUpdatedBy = reviewerId,
            paymentUpdatedAt = now,
            updatedAt = now,
        )
        store[booking.id] = booking
        return booking
    }

    override suspend fun cancelBooking(
        campingId: String,
        bookingId: String,
        reviewerId: String,
        reason: String?,
    ): TransportationBooking {
        checkFailure()
        val now = Date()
        val booking = booking(campingId, bookingId).copy(
            isActive = false,
            canceledBy = reviewerId,
            canceledAt = now,
            cancelReason = reason?.trim()?.takeUnless { it.isBlank() },
            updatedAt = now,
        )
        store[booking.id] = booking
        return booking
    }

    override suspend fun markBoarded(
        campingId: String,
        bookingId: String,
        reviewerId: String,
        leg: TransportationLeg,
        reviewerName: String?,
        location: String?,
    ): TransportationBooking {
        checkFailure()
        val now = Date()
        val current = booking(campingId, bookingId)
        val event = TransportationScanEvent(
            leg = leg,
            checkpoint = TransportationCheckpoint.Departure,
            at = now,
            by = reviewerId,
            byName = reviewerName,
            location = location,
        )
        val booking = current.copy(
            scanHistory = current.scanHistory + event,
            boardingStatus = if (leg == TransportationLeg.Outbound) {
                TransportationBoardingStatus.Boarded
            } else {
                current.boardingStatus
            },
            boardedBy = if (leg == TransportationLeg.Outbound) reviewerId else current.boardedBy,
            boardedAt = if (leg == TransportationLeg.Outbound) now else current.boardedAt,
            updatedAt = now,
        )
        store[booking.id] = booking
        return booking
    }

    override suspend fun markArrived(
        campingId: String,
        bookingId: String,
        reviewerId: String,
        leg: TransportationLeg,
        reviewerName: String?,
        location: String?,
    ): TransportationBooking {
        checkFailure()
        val now = Date()
        val current = booking(campingId, bookingId)
        val event = TransportationScanEvent(
            leg = leg,
            checkpoint = TransportationCheckpoint.Arrival,
            at = now,
            by = reviewerId,
            byName = reviewerName,
            location = location,
        )
        val booking = current.copy(
            scanHistory = current.scanHistory + event,
            arrivedBy = if (leg == TransportationLeg.Outbound) reviewerId else current.arrivedBy,
            arrivedAt = if (leg == TransportationLeg.Outbound) now else current.arrivedAt,
            updatedAt = now,
        )
        store[booking.id] = booking
        return booking
    }

    private fun checkFailure() {
        if (shouldFail) error("Transportation service failed.")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TransportationBindings {
    @Binds
    @Singleton
    abstract fun bindTransportationService(service: FirestoreTransportationService): TransportationService
}
