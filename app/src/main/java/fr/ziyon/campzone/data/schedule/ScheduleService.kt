package fr.ziyon.campzone.data.schedule

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.BuildConfig
import fr.ziyon.campzone.data.model.CampDay
import fr.ziyon.campzone.data.model.CampingSchedule
import fr.ziyon.campzone.data.model.DateKeys
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.SchedulePayload
import fr.ziyon.campzone.data.model.ScheduleReminderTiming
import fr.ziyon.campzone.data.model.dateValue
import fr.ziyon.campzone.data.model.rawStringValue
import fr.ziyon.campzone.data.model.stringValue
import fr.ziyon.campzone.data.model.toCampDayOrNull
import fr.ziyon.campzone.data.model.toProgramOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject


 //* Read/write access to `campings/{id}/schedule/**` (`02-firestore-schema.md` §4).
 //* All writes are hand-built `Map<String, Any?>` - no POJO auto-mapping.


interface ScheduleService {
    suspend fun loadSchedule(campingId: String): CampingSchedule
    suspend fun saveReminderTiming(timing: ScheduleReminderTiming, campingId: String): CampingSchedule
    suspend fun saveProgram(program: Program): CampingSchedule
    suspend fun saveDayTitle(title: String, dayId: String, campingId: String): CampingSchedule
    suspend fun deleteProgram(programId: String, campingId: String): CampingSchedule
    /** Re-files every program under the canonical date-derived day id. Idempotent. */
    suspend fun normalizeDays(campingId: String): CampingSchedule
}

// ──────────────────────────── Firestore implementation ────────────────────────

@Singleton
class FirestoreScheduleService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ScheduleService {

    override suspend fun loadSchedule(campingId: String): CampingSchedule {
        val scheduleDoc = scheduleDoc(campingId)
        val scheduleSnap = scheduleDoc.get().await()
        val daySnaps = scheduleDoc.collection(DAYS).get().await()

        val days = daySnaps.documents.mapNotNull { daySnap ->
            val data = daySnap.data ?: return@mapNotNull null
            val day = data.toCampDayOrNull(daySnap.id) ?: return@mapNotNull null
            val programSnaps = daySnap.reference.collection(PROGRAMS).get().await()
            val programs = programSnaps.documents.mapNotNull { progSnap ->
                progSnap.data?.toProgramOrNull(progSnap.id)
            }.sortedBy { it.startDate }
            day.copy(programs = programs)
        }

        val configData = scheduleSnap.data ?: emptyMap()
        return CampingSchedule(
            campingId = campingId,
            reminderTiming = ScheduleReminderTiming.fromWire(configData[REMINDER_TIMING] as? String),
            days = days,
        )
    }

    override suspend fun saveReminderTiming(
        timing: ScheduleReminderTiming,
        campingId: String,
    ): CampingSchedule {
        val doc = scheduleDoc(campingId)
        val snap = doc.get().await()
        val ts = FieldValue.serverTimestamp()
        val payload = mutableMapOf<String, Any?>(
            CAMPING_ID to campingId,
            REMINDER_TIMING to timing.wireValue,
            UPDATED_AT to ts,
        )
        if (!snap.exists()) payload[CREATED_AT] = ts
        doc.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()
        dispatchScheduleReminders(campingId, timing)
        return loadSchedule(campingId)
    }

    override suspend fun saveProgram(program: Program): CampingSchedule {
        val scheduleDoc = scheduleDoc(program.campingId)
        val daysCol = scheduleDoc.collection(DAYS)
        val ts = FieldValue.serverTimestamp()

        val dayId = DateKeys.campDayId(program.campingId, program.startDate)
        val dayDate = DateKeys.startOfDay(program.startDate)
        val stored = program.copy(campDayId = dayId)

        // 1. Touch config doc
        scheduleDoc.set(
            mapOf(CAMPING_ID to program.campingId, UPDATED_AT to ts),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()

        // 2. Remove this program from any other day it may currently live under
        removeProgramElsewhere(stored.id, keepDayId = dayId, campingId = program.campingId)

        // 3. Upsert day
        val dayDoc = daysCol.document(dayId)
        val daySnap = dayDoc.get().await()
        val dayPayload = mutableMapOf<String, Any?>(
            CAMPING_ID to program.campingId,
            "date" to dayDate,
            UPDATED_AT to ts,
        )
        if (!daySnap.exists()) {
            dayPayload[CREATED_AT] = ts
            dayPayload["title"] = ""  // written only on first create
        }
        dayDoc.set(dayPayload, com.google.firebase.firestore.SetOptions.merge()).await()

        // 4. Upsert program
        val progDoc = dayDoc.collection(PROGRAMS).document(stored.id)
        val progSnap = progDoc.get().await()
        val progPayload = SchedulePayload.programPayload(
            program = stored,
            serverTimestamp = ts,
            deleteField = FieldValue.delete(),
            includeCreatedAt = !progSnap.exists(),
        )
        progDoc.set(progPayload, com.google.firebase.firestore.SetOptions.merge()).await()

        // 5. Prune ghost days
        pruneEmptyDays(program.campingId)

        return loadSchedule(program.campingId)
    }

    override suspend fun saveDayTitle(
        title: String,
        dayId: String,
        campingId: String,
    ): CampingSchedule {
        val trimmed = title.trim()
        val scheduleDoc = scheduleDoc(campingId)
        val dayDoc = scheduleDoc.collection(DAYS).document(dayId)
        val daySnap = dayDoc.get().await()
        val ts = FieldValue.serverTimestamp()

        scheduleDoc.set(
            mapOf(CAMPING_ID to campingId, UPDATED_AT to ts),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()

        val payload = mutableMapOf<String, Any?>(
            CAMPING_ID to campingId,
            "title" to trimmed,
            UPDATED_AT to ts,
        )
        DateKeys.dateFromCampDayId(dayId)?.let { canonicalDate ->
            payload["date"] = canonicalDate
        }
        if (!daySnap.exists()) payload[CREATED_AT] = ts

        dayDoc.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()
        pruneEmptyDays(campingId)
        return loadSchedule(campingId)
    }

    override suspend fun deleteProgram(programId: String, campingId: String): CampingSchedule {
        val schedule = loadSchedule(campingId)
        val parentDay = schedule.days.firstOrNull { day -> day.programs.any { it.id == programId } }
            ?: return schedule

        scheduleDoc(campingId)
            .collection(DAYS)
            .document(parentDay.id)
            .collection(PROGRAMS)
            .document(programId)
            .delete()
            .await()

        pruneEmptyDays(campingId)
        return loadSchedule(campingId)
    }

    override suspend fun normalizeDays(campingId: String): CampingSchedule {
        val daysCol = scheduleDoc(campingId).collection(DAYS)
        val daySnaps = daysCol.get().await()
        val ts = FieldValue.serverTimestamp()

        for (daySnap in daySnaps.documents) {
            val sourceTitle = (daySnap.data?.get("title") as? String).orEmpty()
            val progSnaps = daySnap.reference.collection(PROGRAMS).get().await()

            for (progSnap in progSnaps.documents) {
                val prog = progSnap.data?.toProgramOrNull(progSnap.id) ?: continue
                val canonicalDayId = DateKeys.campDayId(campingId, prog.startDate)
                if (canonicalDayId == daySnap.id) continue

                val targetDay = daysCol.document(canonicalDayId)
                val targetSnap = targetDay.get().await()
                val dayPayload = mutableMapOf<String, Any?>(
                    CAMPING_ID to campingId,
                    "date" to DateKeys.startOfDay(prog.startDate),
                    UPDATED_AT to ts,
                )
                if (!targetSnap.exists()) {
                    dayPayload[CREATED_AT] = ts
                    dayPayload["title"] = sourceTitle
                }
                targetDay.set(dayPayload, com.google.firebase.firestore.SetOptions.merge()).await()

                val movedPayload = (progSnap.data ?: emptyMap()).toMutableMap()
                movedPayload["campDayID"] = canonicalDayId
                targetDay.collection(PROGRAMS)
                    .document(progSnap.id)
                    .set(movedPayload, com.google.firebase.firestore.SetOptions.merge())
                    .await()
                progSnap.reference.delete().await()
            }
        }

        pruneEmptyDays(campingId)
        return loadSchedule(campingId)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun removeProgramElsewhere(
        programId: String,
        keepDayId: String,
        campingId: String,
    ) {
        val daySnaps = scheduleDoc(campingId).collection(DAYS).get().await()
        for (daySnap in daySnaps.documents) {
            if (daySnap.id == keepDayId) continue
            val ref = daySnap.reference.collection(PROGRAMS).document(programId)
            if (ref.get().await().exists()) ref.delete().await()
        }
    }

    private suspend fun pruneEmptyDays(campingId: String) {
        val daySnaps = scheduleDoc(campingId).collection(DAYS).get().await()
        for (daySnap in daySnaps.documents) {
            val title = (daySnap.data?.get("title") as? String).orEmpty().trim()
            if (title.isNotBlank()) continue
            val programs = daySnap.reference.collection(PROGRAMS).limit(1).get().await()
            if (programs.isEmpty) daySnap.reference.delete().await()
        }
    }

    private fun scheduleDoc(campingId: String) =
        firestore.collection(CAMPINGS).document(campingId)
            .collection(SCHEDULE).document(CONFIG)

    /** Best-effort - does not throw on failure. */
    private suspend fun dispatchScheduleReminders(
        campingId: String,
        timing: ScheduleReminderTiming,
    ) {
        try {
            val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: return
            withContext(Dispatchers.IO) {
                val body = JSONObject()
                    .put("appID", APP_ID)
                    .put("campingID", campingId)
                    .put("reminderTiming", timing.wireValue)
                val conn = (URL("${BuildConfig.BACKEND_BASE_URL}/notifications/reminders")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                conn.inputStream.use { } // consume
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    private companion object {
        const val CAMPINGS = "campings"
        const val SCHEDULE = "schedule"
        const val CONFIG = "config"
        const val DAYS = "days"
        const val PROGRAMS = "programs"
        const val CAMPING_ID = "campingID"
        const val REMINDER_TIMING = "reminderTiming"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
        const val APP_ID = "campzone"
    }
}

// ──────────────────────────── Fake (preview / test) ──────────────────────────

class FakeScheduleService(
    private val schedules: MutableMap<String, CampingSchedule> = mutableMapOf(),
    var shouldFail: Boolean = false,
) : ScheduleService {

    override suspend fun loadSchedule(campingId: String): CampingSchedule =
        check().let { schedules[campingId] ?: CampingSchedule(campingId) }

    override suspend fun saveReminderTiming(
        timing: ScheduleReminderTiming,
        campingId: String,
    ): CampingSchedule {
        check()
        val updated = loadSchedule(campingId).copy(reminderTiming = timing)
        schedules[campingId] = updated
        return updated
    }

    override suspend fun saveProgram(program: Program): CampingSchedule {
        check()
        val schedule = loadSchedule(program.campingId).toMutable()
        val dayId = DateKeys.campDayId(program.campingId, program.startDate)
        val stored = program.copy(campDayId = dayId)

        val days = schedule.days.map { day ->
            day.copy(programs = day.programs.filter { it.id != stored.id })
        }.toMutableList()

        val dayIndex = days.indexOfFirst { it.id == dayId }
        if (dayIndex >= 0) {
            days[dayIndex] = days[dayIndex].copy(
                programs = days[dayIndex].programs + stored,
            )
        } else {
            days.add(
                CampDay(
                    id = dayId,
                    campingId = program.campingId,
                    date = DateKeys.startOfDay(program.startDate),
                    programs = listOf(stored),
                ),
            )
        }

        val pruned = days.filter { it.programs.isNotEmpty() || it.title.isNotBlank() }
        val updated = schedule.copy(days = pruned)
        schedules[program.campingId] = updated
        return updated
    }

    override suspend fun saveDayTitle(
        title: String,
        dayId: String,
        campingId: String,
    ): CampingSchedule {
        check()
        val trimmed = title.trim()
        val schedule = loadSchedule(campingId)
        val existingIndex = schedule.days.indexOfFirst { it.id == dayId }
        val date = schedule.days.getOrNull(existingIndex)?.date
            ?: DateKeys.dateFromCampDayId(dayId)
            ?: Date()
        val days = schedule.days.toMutableList()
        if (existingIndex >= 0) {
            days[existingIndex] = days[existingIndex].copy(title = trimmed, hasCustomTitle = trimmed.isNotBlank())
        } else {
            days.add(
                CampDay(
                    id = dayId,
                    campingId = campingId,
                    date = date,
                    title = trimmed,
                    hasCustomTitle = trimmed.isNotBlank(),
                ),
            )
        }
        val updated = schedule.copy(days = days.filter { it.programs.isNotEmpty() || it.title.isNotBlank() })
        schedules[campingId] = updated
        return updated
    }

    override suspend fun deleteProgram(programId: String, campingId: String): CampingSchedule {
        check()
        val schedule = loadSchedule(campingId)
        val days = schedule.days.map { day ->
            day.copy(programs = day.programs.filter { it.id != programId })
        }.filter { it.programs.isNotEmpty() || it.title.isNotBlank() }
        val updated = schedule.copy(days = days)
        schedules[campingId] = updated
        return updated
    }

    override suspend fun normalizeDays(campingId: String): CampingSchedule = loadSchedule(campingId)

    private fun check() {
        if (shouldFail) throw IllegalStateException("FakeScheduleService: simulated failure")
    }

    private fun CampingSchedule.toMutable() = this
}

// ──────────────────────────── Hilt bindings ──────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleBindings {
    @Binds
    abstract fun bindScheduleService(impl: FirestoreScheduleService): ScheduleService
}
