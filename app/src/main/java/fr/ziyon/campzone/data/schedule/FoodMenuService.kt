package fr.ziyon.campzone.data.schedule

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.DateKeys
import fr.ziyon.campzone.data.model.FoodMenuEntry
import fr.ziyon.campzone.data.model.FoodMenuPayload
import fr.ziyon.campzone.data.model.FoodMenuProgramSync
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.SchedulePayload
import fr.ziyon.campzone.data.model.toFoodMenuEntryOrNull
import fr.ziyon.campzone.data.model.toProgramOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Read/write `campings/{id}/foodMenu/{entryId}` plus the two-way
 * Menu↔Program sync (`02-firestore-schema.md` §4.4–4.5). Every save
 * writes **both** the food-menu doc and the generated program doc so the
 * schedule stays in sync without a Firestore trigger.
 */
interface FoodMenuService {
    suspend fun loadMenu(campingId: String): List<FoodMenuEntry>
    suspend fun saveEntry(entry: FoodMenuEntry): List<FoodMenuEntry>
    suspend fun deleteEntry(entryId: String, campingId: String): List<FoodMenuEntry>
}

// ──────────────────────────── Firestore implementation ────────────────────────

@Singleton
class FirestoreFoodMenuService @Inject constructor(
    private val firestore: FirebaseFirestore,
) : FoodMenuService {

    override suspend fun loadMenu(campingId: String): List<FoodMenuEntry> {
        val snaps = foodMenuCol(campingId)
            .orderBy("date", Query.Direction.ASCENDING)
            .get()
            .await()
        return snaps.documents.mapNotNull { snap ->
            snap.data?.toFoodMenuEntryOrNull(snap.id, campingId)
        }
    }

    override suspend fun saveEntry(entry: FoodMenuEntry): List<FoodMenuEntry> {
        val entryId = DateKeys.foodMenuId(entry.date, entry.meal)
        val canonical = entry.copy(id = entryId)
        val ts = FieldValue.serverTimestamp()

        // 1. Write food-menu doc (merge)
        val entryDoc = foodMenuCol(entry.campingId).document(entryId)
        val entrySnap = entryDoc.get().await()
        val entryPayload = FoodMenuPayload.entryPayload(canonical).toMutableMap()
        entryPayload[UPDATED_AT] = ts
        if (!entrySnap.exists()) entryPayload[CREATED_AT] = ts
        entryDoc.set(entryPayload, com.google.firebase.firestore.SetOptions.merge()).await()

        // 2. Sync → Program (upsert the generated program)
        saveGeneratedProgram(canonical, ts)

        return loadMenu(entry.campingId)
    }

    override suspend fun deleteEntry(entryId: String, campingId: String): List<FoodMenuEntry> {
        // 1. Delete food-menu doc
        foodMenuCol(campingId).document(entryId).delete().await()

        // 2. Delete generated program (best-effort: find its parent day and delete it)
        deleteGeneratedProgram(entryId, campingId)

        return loadMenu(campingId)
    }

    // ── Sync helpers ──────────────────────────────────────────────────────────

    private suspend fun saveGeneratedProgram(entry: FoodMenuEntry, ts: Any) {
        val programId = DateKeys.menuProgramId(entry.id)
        val dayId = DateKeys.campDayId(entry.campingId, entry.date)
        val scheduleDoc = scheduleDoc(entry.campingId)
        val daysCol = scheduleDoc.collection(DAYS)

        // Find the existing generated program (may be under a different day if date changed)
        val existing = findProgramAcrossDays(programId, entry.campingId)

        // Build the synced program (preserves leader-edited scheduling fields)
        val program = FoodMenuProgramSync.programFor(entry, existing)
        val canonicalDayId = DateKeys.campDayId(entry.campingId, program.startDate)

        // Touch schedule/config
        scheduleDoc.set(
            mapOf(CAMPING_ID to entry.campingId, UPDATED_AT to ts),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()

        // If date changed, delete from old day
        if (existing != null && existing.campDayId != canonicalDayId) {
            daysCol.document(existing.campDayId).collection(PROGRAMS).document(programId).delete().await()
        }

        // Upsert day
        val dayDoc = daysCol.document(canonicalDayId)
        val daySnap = dayDoc.get().await()
        val dayPayload = SchedulePayload.dayPayload(
            campingId = entry.campingId,
            date = DateKeys.startOfDay(program.startDate),
            serverTimestamp = ts,
            includeCreatedAt = !daySnap.exists(),
        )
        dayDoc.set(dayPayload, com.google.firebase.firestore.SetOptions.merge()).await()

        // Upsert program
        val progDoc = dayDoc.collection(PROGRAMS).document(programId)
        val progSnap = progDoc.get().await()
        val progPayload = SchedulePayload.programPayload(
            program = program,
            serverTimestamp = ts,
            deleteField = FieldValue.delete(),
            includeCreatedAt = !progSnap.exists(),
        )
        progDoc.set(progPayload, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    private suspend fun deleteGeneratedProgram(entryId: String, campingId: String) {
        val programId = DateKeys.menuProgramId(entryId)
        try {
            val daySnaps = scheduleDoc(campingId).collection(DAYS).get().await()
            for (daySnap in daySnaps.documents) {
                val progRef = daySnap.reference.collection(PROGRAMS).document(programId)
                if (progRef.get().await().exists()) {
                    progRef.delete().await()
                    break
                }
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    private suspend fun findProgramAcrossDays(programId: String, campingId: String): Program? {
        val daySnaps = scheduleDoc(campingId).collection(DAYS).get().await()
        for (daySnap in daySnaps.documents) {
            val progSnap = daySnap.reference.collection(PROGRAMS).document(programId).get().await()
            if (progSnap.exists()) {
                return progSnap.data?.toProgramOrNull(progSnap.id)
            }
        }
        return null
    }

    // ── Path helpers ──────────────────────────────────────────────────────────

    private fun foodMenuCol(campingId: String) =
        firestore.collection(CAMPINGS).document(campingId).collection(FOOD_MENU)

    private fun scheduleDoc(campingId: String) =
        firestore.collection(CAMPINGS).document(campingId)
            .collection(SCHEDULE).document(CONFIG)

    private companion object {
        const val CAMPINGS = "campings"
        const val FOOD_MENU = "foodMenu"
        const val SCHEDULE = "schedule"
        const val CONFIG = "config"
        const val DAYS = "days"
        const val PROGRAMS = "programs"
        const val CAMPING_ID = "campingID"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
    }
}

// ──────────────────────────── Fake (preview / test) ──────────────────────────

class FakeFoodMenuService(
    private val entries: MutableMap<String, MutableList<FoodMenuEntry>> = mutableMapOf(),
    var shouldFail: Boolean = false,
) : FoodMenuService {

    override suspend fun loadMenu(campingId: String): List<FoodMenuEntry> {
        check()
        return (entries[campingId] ?: emptyList()).sortedBy { it.date }
    }

    override suspend fun saveEntry(entry: FoodMenuEntry): List<FoodMenuEntry> {
        check()
        val campingEntries = entries.getOrPut(entry.campingId) { mutableListOf() }
        val canonical = entry.copy(id = DateKeys.foodMenuId(entry.date, entry.meal))
        campingEntries.removeIf { it.id == canonical.id }
        campingEntries.add(canonical)
        return campingEntries.sortedBy { it.date }
    }

    override suspend fun deleteEntry(entryId: String, campingId: String): List<FoodMenuEntry> {
        check()
        entries[campingId]?.removeIf { it.id == entryId }
        return (entries[campingId] ?: emptyList()).sortedBy { it.date }
    }

    private fun check() {
        if (shouldFail) throw IllegalStateException("FakeFoodMenuService: simulated failure")
    }
}

// ──────────────────────────── Hilt bindings ──────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
abstract class FoodMenuBindings {
    @Binds
    abstract fun bindFoodMenuService(impl: FirestoreFoodMenuService): FoodMenuService
}
