package fr.ziyon.campzone.data.checkin

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.CheckInRecord
import fr.ziyon.campzone.data.model.CheckInRecordPayload
import fr.ziyon.campzone.data.model.toCheckInRecordOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Read/write access to `campings/{id}/checkIns/{attendeeId}`
 * (`02-firestore-schema.md` §7.1). Mirrors the iOS `CheckInServicing` contract.
 * All (de)serialization goes through the hand-mapped [CheckInRecord] helpers -
 * no POJO auto-mapping.
 */
interface CheckInService {
    /** All check-ins for a camping, newest first. */
    suspend fun loadRecords(campingId: String): List<CheckInRecord>

    /** Live single-record stream for surfaces such as the Home pass card. */
    fun observeRecord(campingId: String, attendeeId: String): Flow<CheckInRecord?>

    /**
     * Fetches a single attendee's check-in by document id. Used where a blanket
     * collection read is denied (e.g. a guardian may only read their own
     * children's check-in docs, not the whole `checkIns` collection).
     */
    suspend fun loadRecord(campingId: String, attendeeId: String): CheckInRecord?

    /** Records a check-in. Doc ID == `attendeeId` (one per attendee per camp). */
    suspend fun recordCheckIn(record: CheckInRecord)
}

@Singleton
class FirestoreCheckInService @Inject constructor(
    private val db: FirebaseFirestore,
) : CheckInService {

    override suspend fun loadRecords(campingId: String): List<CheckInRecord> {
        val snapshot = checkInsCollection(campingId)
            .orderBy(Field.CheckedInAt, Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.mapNotNull { document ->
            @Suppress("UNCHECKED_CAST")
            (document.data as? Map<String, Any?>)?.toCheckInRecordOrNull(document.id)
        }
    }

    override fun observeRecord(campingId: String, attendeeId: String): Flow<CheckInRecord?> = callbackFlow {
        val listener = checkInDocument(campingId, attendeeId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                val record = (snapshot?.data as? Map<String, Any?>)
                    ?.toCheckInRecordOrNull(snapshot.id)
                trySend(record)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun loadRecord(campingId: String, attendeeId: String): CheckInRecord? {
        val snapshot = checkInDocument(campingId, attendeeId).get().await()
        @Suppress("UNCHECKED_CAST")
        return (snapshot.data as? Map<String, Any?>)?.toCheckInRecordOrNull(snapshot.id)
    }

    override suspend fun recordCheckIn(record: CheckInRecord) {
        require(record.campingId.isNotBlank()) { "Camping is required." }
        require(record.attendeeId.isNotBlank()) { "Attendee is required." }
        val payload = CheckInRecordPayload.checkInPayload(record, FieldValue.serverTimestamp())
        // Doc ID == attendeeID: one check-in per attendee per camp; a full set overwrites.
        checkInDocument(record.campingId, record.attendeeId)
            .set(payload)
            .await()
    }

    private fun checkInsCollection(campingId: String) =
        db.collection(Collection.Campings).document(campingId).collection(Collection.CheckIns)

    private fun checkInDocument(campingId: String, attendeeId: String) =
        checkInsCollection(campingId).document(attendeeId)

    private object Collection {
        const val Campings = "campings"
        const val CheckIns = "checkIns"
    }

    private object Field {
        const val CheckedInAt = "checkedInAt"
    }
}

class FakeCheckInService(
    records: List<CheckInRecord> = emptyList(),
    var shouldFail: Boolean = false,
) : CheckInService {
    private val recordsByCamping = MutableStateFlow(records.groupBy { it.campingId }
        .mapValues { it.value.toMutableList() }.toMutableMap()
    )

    private fun check() {
        if (shouldFail) throw IllegalStateException("FakeCheckInService configured to fail.")
    }

    override suspend fun loadRecords(campingId: String): List<CheckInRecord> {
        check()
        return (recordsByCamping.value[campingId] ?: emptyList())
            .sortedByDescending { it.checkedInAt ?: Date(0) }
    }

    override fun observeRecord(campingId: String, attendeeId: String): Flow<CheckInRecord?> =
        kotlinx.coroutines.flow.flow {
            if (shouldFail) throw IllegalStateException("FakeCheckInService configured to fail.")
            recordsByCamping.collect { map ->
                emit(map[campingId]?.firstOrNull { it.attendeeId == attendeeId })
            }
        }

    override suspend fun loadRecord(campingId: String, attendeeId: String): CheckInRecord? {
        check()
        return recordsByCamping.value[campingId]?.firstOrNull { it.attendeeId == attendeeId }
    }

    override suspend fun recordCheckIn(record: CheckInRecord) {
        check()
        val store = recordsByCamping.value.toMutableMap()
        val list = store.getOrPut(record.campingId) { mutableListOf() }
        list.removeAll { it.attendeeId == record.attendeeId }
        list.add(record)
        recordsByCamping.value = store
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CheckInBindings {
    @Binds
    @Singleton
    abstract fun bindCheckInService(impl: FirestoreCheckInService): CheckInService
}
