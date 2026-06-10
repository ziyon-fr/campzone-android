package fr.ziyon.campzone.data.attendance

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.ProgramAttendanceRecord
import fr.ziyon.campzone.data.model.ProgramAttendanceRecordPayload
import fr.ziyon.campzone.data.model.toProgramAttendanceRecordOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

interface ProgramAttendanceService {
    suspend fun loadRecords(campingId: String, programId: String): List<ProgramAttendanceRecord>
    suspend fun recordAttendance(record: ProgramAttendanceRecord)
    suspend fun updateAttendance(record: ProgramAttendanceRecord)
    suspend fun deleteAttendance(campingId: String, programId: String, attendeeId: String)
}

@Singleton
class FirestoreProgramAttendanceService @Inject constructor(
    private val db: FirebaseFirestore,
) : ProgramAttendanceService {

    override suspend fun loadRecords(campingId: String, programId: String): List<ProgramAttendanceRecord> {
        val snapshot = recordsCollection(campingId, programId)
            .orderBy(Field.CheckedInAt, Query.Direction.DESCENDING)
            .limit(RECORD_LIMIT)
            .get()
            .await()
        return snapshot.documents.mapNotNull { document ->
            @Suppress("UNCHECKED_CAST")
            (document.data as? Map<String, Any?>)?.toProgramAttendanceRecordOrNull(document.id)
        }
    }

    override suspend fun recordAttendance(record: ProgramAttendanceRecord) {
        recordsCollection(record.campingId, record.programId)
            .document(record.attendeeId)
            .set(
                ProgramAttendanceRecordPayload.attendancePayload(
                    record = record,
                    serverTimestamp = FieldValue.serverTimestamp(),
                    includeCreatedAt = true,
                ),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun updateAttendance(record: ProgramAttendanceRecord) {
        recordsCollection(record.campingId, record.programId)
            .document(record.attendeeId)
            .set(
                ProgramAttendanceRecordPayload.attendancePayload(
                    record = record,
                    serverTimestamp = FieldValue.serverTimestamp(),
                    includeCreatedAt = false,
                ),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun deleteAttendance(campingId: String, programId: String, attendeeId: String) {
        recordsCollection(campingId, programId)
            .document(attendeeId)
            .delete()
            .await()
    }

    private fun recordsCollection(campingId: String, programId: String) =
        db.collection(Collection.Campings)
            .document(campingId)
            .collection(Collection.ProgramAttendance)
            .document(programId)
            .collection(Collection.Records)

    private object Collection {
        const val Campings = "campings"
        const val ProgramAttendance = "programAttendance"
        const val Records = "records"
    }

    private object Field {
        const val CheckedInAt = "checkedInAt"
    }

    private companion object {
        const val RECORD_LIMIT = 500L
    }
}

class FakeProgramAttendanceService(
    records: List<ProgramAttendanceRecord> = emptyList(),
    var shouldFail: Boolean = false,
) : ProgramAttendanceService {
    private val recordsByProgram = records
        .groupBy { key(it.campingId, it.programId) }
        .mapValues { it.value.toMutableList() }
        .toMutableMap()

    private fun check() {
        if (shouldFail) error("FakeProgramAttendanceService configured to fail.")
    }

    override suspend fun loadRecords(campingId: String, programId: String): List<ProgramAttendanceRecord> {
        check()
        return recordsByProgram[key(campingId, programId)].orEmpty()
            .sortedByDescending { it.checkedInAt }
    }

    override suspend fun recordAttendance(record: ProgramAttendanceRecord) {
        check()
        val list = recordsByProgram.getOrPut(key(record.campingId, record.programId)) { mutableListOf() }
        list.removeAll { it.attendeeId == record.attendeeId }
        list.add(record.copy(checkedInAt = record.checkedInAt.takeUnless { it.time <= 0 } ?: Date()))
    }

    override suspend fun updateAttendance(record: ProgramAttendanceRecord) {
        recordAttendance(record)
    }

    override suspend fun deleteAttendance(campingId: String, programId: String, attendeeId: String) {
        check()
        recordsByProgram[key(campingId, programId)]?.removeAll { it.attendeeId == attendeeId }
    }

    private fun key(campingId: String, programId: String) = "$campingId/$programId"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ProgramAttendanceBindings {
    @Binds
    @Singleton
    abstract fun bindProgramAttendanceService(
        impl: FirestoreProgramAttendanceService,
    ): ProgramAttendanceService
}
