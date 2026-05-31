package fr.ziyon.campzone.data.venuemap

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.VenueMap
import fr.ziyon.campzone.data.model.VenueMapPayload
import fr.ziyon.campzone.data.model.toVenueMap
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
 * Persistence for the venue map at `campings/{campingID}/venueMap/config` (one
 * config doc per camp, the same single-doc pattern as `albumSettings`/
 * `schedule/config`). Mirrors [fr.ziyon.campzone.data.lodging.LodgingService]:
 * interface + Firestore + fake, hand-built `Map<String, Any?>` payload/parse so
 * `imageURL`/`imagePublicID` stay delete-when-empty and the embedded `points`
 * array (with omit-when-nil `imageX`/`imageY`/`latitude`/`longitude`) is
 * explicit. Read is open to any signed-in user; writes are gated
 * `canManageTeams` OR `canManageSchedule` at the call site (and by the rules).
 */
interface VenueMapService {
    fun observeMap(campingId: String): Flow<VenueMap>
    suspend fun loadMap(campingId: String): VenueMap
    suspend fun saveMap(map: VenueMap): VenueMap
}

class FirestoreVenueMapService @Inject constructor(
    private val db: FirebaseFirestore,
) : VenueMapService {

    override fun observeMap(campingId: String): Flow<VenueMap> = callbackFlow {
        val listener = configDocument(campingId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val value = snapshot?.data?.toVenueMap(campingId) ?: VenueMap(campingId = campingId)
            trySend(value)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun loadMap(campingId: String): VenueMap {
        val snapshot = configDocument(campingId).get().await()
        return snapshot.data?.toVenueMap(campingId) ?: VenueMap(campingId = campingId)
    }

    override suspend fun saveMap(map: VenueMap): VenueMap {
        val payload = VenueMapPayload.configPayload(
            venueMap = map,
            serverTimestamp = FieldValue.serverTimestamp(),
            deleteField = FieldValue.delete(),
        )
        configDocument(map.campingId).set(payload, SetOptions.merge()).await()
        return map
    }

    private fun configDocument(campingId: String) =
        db.collection("campings").document(campingId).collection("venueMap").document("config")
}

/** In-memory fake for previews and tests; mirrors the iOS `MockVenueMapService`. */
class FakeVenueMapService(
    initial: List<VenueMap> = emptyList(),
    private var shouldFail: Boolean = false,
) : VenueMapService {

    private val maps = MutableStateFlow(initial.associateBy { it.campingId })

    override fun observeMap(campingId: String): Flow<VenueMap> =
        maps.map { it[campingId] ?: VenueMap(campingId = campingId) }

    override suspend fun loadMap(campingId: String): VenueMap {
        failIfNeeded()
        return maps.value[campingId] ?: VenueMap(campingId = campingId)
    }

    override suspend fun saveMap(map: VenueMap): VenueMap {
        failIfNeeded()
        val stored = map.copy(updatedAt = Date())
        maps.update { it + (map.campingId to stored) }
        return stored
    }

    private fun failIfNeeded() {
        if (shouldFail) throw IllegalStateException("The fake venue-map service was configured to fail.")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VenueMapBindings {
    @Binds
    @Singleton
    abstract fun bindVenueMapService(impl: FirestoreVenueMapService): VenueMapService
}
