package fr.ziyon.campzone.data.lodging

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.LodgingPayload
import fr.ziyon.campzone.data.model.LodgingUnit
import fr.ziyon.campzone.data.model.toLodgingUnitOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

/**
 * Persistence for lodging units at `campings/{campingID}/lodging/{unitID}`.
 * Mirrors [fr.ziyon.campzone.data.teams.TeamService]: interface + Firestore +
 * fake, manual `Map<String, Any?>` payload/parse (so `Date`↔`Timestamp` and
 * the create-only `createdAt` stay explicit). Read is open to any signed-in
 * user; writes are gated `canManageTeams` at the call site (and by the rules).
 */
interface LodgingService {
    fun observeUnits(campingId: String): Flow<List<LodgingUnit>>
    suspend fun loadUnits(campingId: String): List<LodgingUnit>
    suspend fun saveUnit(unit: LodgingUnit): LodgingUnit
    suspend fun deleteUnit(id: String, campingId: String)
    suspend fun setOccupants(unitId: String, campingId: String, occupantIds: List<String>): List<LodgingUnit>

    /** Batch-writes occupants for every unit in one pass (one-tap auto-allocate). */
    suspend fun applyAllocation(
        occupantsByUnitId: Map<String, List<String>>,
        campingId: String,
    ): List<LodgingUnit>
}

class FirestoreLodgingService @Inject constructor(
    private val db: FirebaseFirestore,
) : LodgingService {

    override fun observeUnits(campingId: String): Flow<List<LodgingUnit>> = callbackFlow {
        val listener = lodgingCollection(campingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val units = snapshot?.documents
                    ?.mapNotNull { doc -> doc.data?.toLodgingUnitOrNull(doc.id) }
                    ?.sortedWith(unitComparator)
                    ?: emptyList()
                trySend(units)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun loadUnits(campingId: String): List<LodgingUnit> {
        val snapshot = lodgingCollection(campingId).get().await()
        return snapshot.documents
            .mapNotNull { it.data?.toLodgingUnitOrNull(it.id) }
            .sortedWith(unitComparator)
    }

    override suspend fun saveUnit(unit: LodgingUnit): LodgingUnit {
        val document = lodgingCollection(unit.campingId).document(unit.id)
        val existing = document.get().await()
        val payload = LodgingPayload.unitPayload(
            unit = unit,
            serverTimestamp = FieldValue.serverTimestamp(),
            includeCreatedAt = !existing.exists(),
        )
        document.set(payload, SetOptions.merge()).await()
        val saved = document.get().await()
        return saved.data?.toLodgingUnitOrNull(unit.id)
            ?: throw IllegalStateException("Lodging unit could not be loaded after save.")
    }

    override suspend fun deleteUnit(id: String, campingId: String) {
        lodgingCollection(campingId).document(id).delete().await()
    }

    override suspend fun setOccupants(
        unitId: String,
        campingId: String,
        occupantIds: List<String>,
    ): List<LodgingUnit> {
        lodgingCollection(campingId).document(unitId).set(
            mapOf(
                "occupantIDs" to occupantIds,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        return loadUnits(campingId)
    }

    override suspend fun applyAllocation(
        occupantsByUnitId: Map<String, List<String>>,
        campingId: String,
    ): List<LodgingUnit> {
        if (occupantsByUnitId.isEmpty()) return loadUnits(campingId)
        val batch = db.batch()
        occupantsByUnitId.forEach { (unitId, occupantIds) ->
            batch.set(
                lodgingCollection(campingId).document(unitId),
                mapOf(
                    "occupantIDs" to occupantIds,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
        }
        batch.commit().await()
        return loadUnits(campingId)
    }

    private fun lodgingCollection(campingId: String) =
        db.collection("campings").document(campingId).collection("lodging")

    private val unitComparator: Comparator<LodgingUnit> =
        compareBy { it.name.lowercase() }
}

class FakeLodgingService(
    initial: List<LodgingUnit> = emptyList(),
    private var shouldFail: Boolean = false,
) : LodgingService {
    private val units = MutableStateFlow(initial)

    override fun observeUnits(campingId: String): Flow<List<LodgingUnit>> =
        units.map { list -> sorted(list, campingId) }

    override suspend fun loadUnits(campingId: String): List<LodgingUnit> {
        failIfNeeded()
        return sorted(units.value, campingId)
    }

    override suspend fun saveUnit(unit: LodgingUnit): LodgingUnit {
        failIfNeeded()
        val stored = unit.copy(updatedAt = Date())
        units.update { list ->
            if (list.any { it.id == unit.id }) list.map { if (it.id == unit.id) stored else it } else list + stored
        }
        return stored
    }

    override suspend fun deleteUnit(id: String, campingId: String) {
        failIfNeeded()
        units.update { list -> list.filterNot { it.id == id && it.campingId == campingId } }
    }

    override suspend fun setOccupants(
        unitId: String,
        campingId: String,
        occupantIds: List<String>,
    ): List<LodgingUnit> {
        failIfNeeded()
        units.update { list ->
            list.map { unit ->
                if (unit.id == unitId && unit.campingId == campingId) {
                    unit.copy(occupantIds = occupantIds, updatedAt = Date())
                } else {
                    unit
                }
            }
        }
        return sorted(units.value, campingId)
    }

    override suspend fun applyAllocation(
        occupantsByUnitId: Map<String, List<String>>,
        campingId: String,
    ): List<LodgingUnit> {
        failIfNeeded()
        units.update { list ->
            list.map { unit ->
                val occupants = occupantsByUnitId[unit.id]
                if (unit.campingId == campingId && occupants != null) {
                    unit.copy(occupantIds = occupants, updatedAt = Date())
                } else {
                    unit
                }
            }
        }
        return sorted(units.value, campingId)
    }

    private fun sorted(list: List<LodgingUnit>, campingId: String): List<LodgingUnit> =
        list.filter { it.campingId == campingId }.sortedBy { it.name.lowercase() }

    private fun failIfNeeded() {
        if (shouldFail) throw IllegalStateException("The fake lodging service was configured to fail.")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LodgingBindings {
    @Binds
    @Singleton
    abstract fun bindLodgingService(impl: FirestoreLodgingService): LodgingService
}
