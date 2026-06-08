package fr.ziyon.campzone.data.vehicle

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingVehicle
import fr.ziyon.campzone.data.model.UserVehicle
import fr.ziyon.campzone.data.model.UserVehiclePayload
import fr.ziyon.campzone.data.model.VehicleCheckIn
import fr.ziyon.campzone.data.model.VehicleMutation
import fr.ziyon.campzone.data.model.VehiclePayload
import fr.ziyon.campzone.data.model.VehicleStatus
import fr.ziyon.campzone.data.model.toCampingAttendeeOrNull
import fr.ziyon.campzone.data.model.toCampingVehicleOrNull
import fr.ziyon.campzone.data.model.toUserVehicleOrNull
import fr.ziyon.campzone.data.model.toVehicleCheckInOrNull
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

interface VehicleService {
    fun vehicles(campingId: String): Flow<List<CampingVehicle>>
    suspend fun vehicle(campingId: String, vehicleId: String): CampingVehicle
    suspend fun vehicleByToken(campingId: String, token: String): CampingVehicle?
    suspend fun vehicleByInvitationCode(campingId: String, code: String): CampingVehicle?
    suspend fun createVehicle(vehicle: CampingVehicle): CampingVehicle
    suspend fun updateVehicle(vehicle: CampingVehicle): CampingVehicle
    suspend fun cancelVehicle(campingId: String, vehicleId: String): CampingVehicle
    suspend fun deleteVehicle(campingId: String, vehicleId: String)
    suspend fun addPassenger(
        campingId: String,
        vehicleId: String,
        registrationId: String,
        name: String,
    ): CampingVehicle
    suspend fun removePassenger(campingId: String, vehicleId: String, registrationId: String): CampingVehicle
    suspend fun requestJoin(campingId: String, vehicleId: String, registrationId: String, name: String): CampingVehicle
    suspend fun withdrawJoinRequest(campingId: String, vehicleId: String, registrationId: String): CampingVehicle
    suspend fun approvePassenger(campingId: String, vehicleId: String, registrationId: String): CampingVehicle
    suspend fun denyPassenger(campingId: String, vehicleId: String, registrationId: String): CampingVehicle
    suspend fun checkInVehicle(checkIn: VehicleCheckIn): CampingVehicle
    suspend fun checkIns(campingId: String, vehicleId: String): List<VehicleCheckIn>
    suspend fun vehiclesWithAvailableSeats(campingId: String): List<CampingVehicle>
    suspend fun peopleNeedingTransport(campingId: String): List<CampingAttendee>
}

interface UserVehicleService {
    fun vehicles(userId: String): Flow<List<UserVehicle>>
    suspend fun loadVehicles(userId: String): List<UserVehicle>
    suspend fun saveVehicle(vehicle: UserVehicle): UserVehicle
    suspend fun deleteVehicle(userId: String, vehicleId: String)
    suspend fun setDefault(userId: String, vehicleId: String): List<UserVehicle>
}

@Singleton
class FirestoreVehicleService @Inject constructor(
    private val firestore: FirebaseFirestore,
) : VehicleService {
    override fun vehicles(campingId: String): Flow<List<CampingVehicle>> = callbackFlow {
        val listener = collection(campingId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val vehicles = snapshot?.documents
                ?.mapNotNull { it.data?.toCampingVehicleOrNull(it.id) }
                .orEmpty()
                .sortedForVehicleDisplay()
            trySend(vehicles)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun vehicle(campingId: String, vehicleId: String): CampingVehicle {
        val snapshot = collection(campingId).document(vehicleId).get().await()
        return snapshot.data?.toCampingVehicleOrNull(snapshot.id)
            ?: error("Vehicle could not be found.")
    }

    override suspend fun vehicleByToken(campingId: String, token: String): CampingVehicle? =
        collection(campingId)
            .whereEqualTo(Field.QrToken, token.trim())
            .limit(1)
            .get()
            .await()
            .documents
            .firstNotNullOfOrNull { it.data?.toCampingVehicleOrNull(it.id) }

    override suspend fun vehicleByInvitationCode(campingId: String, code: String): CampingVehicle? =
        collection(campingId)
            .whereEqualTo(Field.InvitationCode, code.trim().uppercase(Locale.ROOT))
            .limit(1)
            .get()
            .await()
            .documents
            .firstNotNullOfOrNull { it.data?.toCampingVehicleOrNull(it.id) }
            ?.takeIf { it.status != VehicleStatus.Cancelled }

    override suspend fun createVehicle(vehicle: CampingVehicle): CampingVehicle {
        vehicle.validationError?.let { error(it.message) }
        require(vehicle.status == VehicleStatus.Pending) { "New vehicles must start pending." }
        collection(vehicle.campingId).document(vehicle.id)
            .set(
                VehiclePayload.vehiclePayload(
                    vehicle = vehicle,
                    serverTimestamp = FieldValue.serverTimestamp(),
                    includeCreatedAt = true,
                ),
                SetOptions.merge(),
            )
            .await()
        return vehicle(vehicle.campingId, vehicle.id)
    }

    override suspend fun updateVehicle(vehicle: CampingVehicle): CampingVehicle {
        vehicle.validationError?.let { error(it.message) }
        collection(vehicle.campingId).document(vehicle.id)
            .set(
                VehiclePayload.updatePayload(
                    vehicle = vehicle.copy(updatedAt = Date()),
                    serverTimestamp = FieldValue.serverTimestamp(),
                    deleteField = FieldValue.delete(),
                ),
                SetOptions.merge(),
            )
            .await()
        return vehicle(vehicle.campingId, vehicle.id)
    }

    override suspend fun cancelVehicle(campingId: String, vehicleId: String): CampingVehicle {
        collection(campingId).document(vehicleId)
            .set(VehiclePayload.cancelPayload(FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
        return vehicle(campingId, vehicleId)
    }

    override suspend fun deleteVehicle(campingId: String, vehicleId: String) {
        collection(campingId).document(vehicleId).delete().await()
    }

    override suspend fun addPassenger(
        campingId: String,
        vehicleId: String,
        registrationId: String,
        name: String,
    ): CampingVehicle {
        val updated = VehicleMutation.addingPassenger(vehicle(campingId, vehicleId), registrationId, name)
        return updateVehicle(updated)
    }

    override suspend fun removePassenger(
        campingId: String,
        vehicleId: String,
        registrationId: String,
    ): CampingVehicle {
        val updated = VehicleMutation.removingPassenger(vehicle(campingId, vehicleId), registrationId)
        return updateVehicle(updated)
    }

    override suspend fun requestJoin(
        campingId: String,
        vehicleId: String,
        registrationId: String,
        name: String,
    ): CampingVehicle {
        val updated = VehicleMutation.addingPending(vehicle(campingId, vehicleId), registrationId, name)
        collection(campingId).document(vehicleId)
            .set(VehiclePayload.pendingPayload(updated, FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
        return updated
    }

    override suspend fun withdrawJoinRequest(
        campingId: String,
        vehicleId: String,
        registrationId: String,
    ): CampingVehicle {
        val updated = VehicleMutation.removePending(vehicle(campingId, vehicleId), registrationId)
        collection(campingId).document(vehicleId)
            .set(VehiclePayload.pendingPayload(updated, FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
        return updated
    }

    override suspend fun approvePassenger(
        campingId: String,
        vehicleId: String,
        registrationId: String,
    ): CampingVehicle {
        val vehicle = vehicle(campingId, vehicleId)
        val name = vehicle.pendingPassengers.firstOrNull { it.id == registrationId }?.name.orEmpty()
        return updateVehicle(VehicleMutation.addingPassenger(vehicle, registrationId, name))
    }

    override suspend fun denyPassenger(
        campingId: String,
        vehicleId: String,
        registrationId: String,
    ): CampingVehicle =
        updateVehicle(VehicleMutation.removePending(vehicle(campingId, vehicleId), registrationId))

    override suspend fun checkInVehicle(checkIn: VehicleCheckIn): CampingVehicle {
        checkInsCollection(checkIn.campingId, checkIn.vehicleId)
            .document(checkIn.id)
            .set(VehiclePayload.checkInPayload(checkIn))
            .await()

        collection(checkIn.campingId).document(checkIn.vehicleId)
            .set(VehiclePayload.arrivalPayload(checkIn, FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
        return vehicle(checkIn.campingId, checkIn.vehicleId)
    }

    override suspend fun checkIns(campingId: String, vehicleId: String): List<VehicleCheckIn> =
        checkInsCollection(campingId, vehicleId)
            .get()
            .await()
            .documents
            .mapNotNull { it.data?.toVehicleCheckInOrNull(it.id) }
            .sortedByDescending { it.checkedInAt }

    override suspend fun vehiclesWithAvailableSeats(campingId: String): List<CampingVehicle> =
        collection(campingId)
            .whereEqualTo(Field.HasAvailableSeats, true)
            .get()
            .await()
            .documents
            .mapNotNull { it.data?.toCampingVehicleOrNull(it.id) }
            .filter { it.status != VehicleStatus.Cancelled && it.availableSeats > 0 }
            .sortedForVehicleDisplay()

    override suspend fun peopleNeedingTransport(campingId: String): List<CampingAttendee> =
        firestore.collection(Collection.Campings)
            .document(campingId)
            .collection(Collection.Registrations)
            .whereEqualTo(Field.NeedsTransportHelp, true)
            .get()
            .await()
            .documents
            .mapNotNull { it.data?.toCampingAttendeeOrNull(it.id) }
            .sortedBy { it.displayName.lowercase() }

    private fun collection(campingId: String) = firestore
        .collection(Collection.Campings)
        .document(campingId)
        .collection(Collection.Vehicles)

    private fun checkInsCollection(campingId: String, vehicleId: String) =
        collection(campingId).document(vehicleId).collection(Collection.VehicleCheckIns)
}

@Singleton
class FirestoreUserVehicleService @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UserVehicleService {
    override fun vehicles(userId: String): Flow<List<UserVehicle>> = callbackFlow {
        val listener = collection(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val vehicles = snapshot?.documents
                ?.mapNotNull { it.data?.toUserVehicleOrNull(it.id) }
                .orEmpty()
                .sortedForUserVehicleDisplay()
            trySend(vehicles)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun loadVehicles(userId: String): List<UserVehicle> =
        collection(userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.data?.toUserVehicleOrNull(it.id) }
            .sortedForUserVehicleDisplay()

    override suspend fun saveVehicle(vehicle: UserVehicle): UserVehicle {
        vehicle.validationError?.let { error(it.message) }
        val document = collection(vehicle.ownerUserId).document(vehicle.id)
        val exists = document.get().await().exists()
        document
            .set(
                UserVehiclePayload.userVehiclePayload(
                    vehicle = vehicle,
                    serverTimestamp = FieldValue.serverTimestamp(),
                    includeCreatedAt = !exists,
                ),
                SetOptions.merge(),
            )
            .await()
        if (vehicle.isDefault) {
            setDefault(vehicle.ownerUserId, vehicle.id)
        }
        return loadVehicles(vehicle.ownerUserId).firstOrNull { it.id == vehicle.id } ?: vehicle
    }

    override suspend fun deleteVehicle(userId: String, vehicleId: String) {
        collection(userId).document(vehicleId).delete().await()
    }

    override suspend fun setDefault(userId: String, vehicleId: String): List<UserVehicle> {
        val snapshot = collection(userId).get().await()
        val batch = firestore.batch()
        snapshot.documents.forEach { document ->
            batch.set(
                document.reference,
                mapOf(
                    "isDefault" to (document.id == vehicleId),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
        }
        batch.commit().await()
        return loadVehicles(userId)
    }

    private fun collection(userId: String) = firestore
        .collection(Collection.Users)
        .document(userId)
        .collection(Collection.Vehicles)
}

class FakeVehicleService(
    vehicles: List<CampingVehicle> = emptyList(),
    checkIns: List<VehicleCheckIn> = emptyList(),
    private val needingTransport: List<CampingAttendee> = emptyList(),
    var shouldFail: Boolean = false,
) : VehicleService {
    private val store = vehicles.associateBy { it.id }.toMutableMap()
    private val checkInStore = checkIns.groupBy { it.vehicleId }.mapValues { it.value.toMutableList() }.toMutableMap()

    override fun vehicles(campingId: String): Flow<List<CampingVehicle>> =
        flowOf(store.values.filter { it.campingId == campingId }.sortedForVehicleDisplay())

    override suspend fun vehicle(campingId: String, vehicleId: String): CampingVehicle {
        checkFailure()
        return store[vehicleId]?.takeIf { it.campingId == campingId } ?: error("Vehicle could not be found.")
    }

    override suspend fun vehicleByToken(campingId: String, token: String): CampingVehicle? {
        checkFailure()
        return store.values.firstOrNull { it.campingId == campingId && it.qrToken == token }
    }

    override suspend fun vehicleByInvitationCode(campingId: String, code: String): CampingVehicle? {
        checkFailure()
        return store.values.firstOrNull {
            it.campingId == campingId &&
                it.invitationCode.equals(code.trim(), ignoreCase = true) &&
                it.status != VehicleStatus.Cancelled
        }
    }

    override suspend fun createVehicle(vehicle: CampingVehicle): CampingVehicle {
        checkFailure()
        vehicle.validationError?.let { error(it.message) }
        store[vehicle.id] = vehicle
        return vehicle
    }

    override suspend fun updateVehicle(vehicle: CampingVehicle): CampingVehicle {
        checkFailure()
        vehicle.validationError?.let { error(it.message) }
        val updated = vehicle.copy(updatedAt = Date())
        store[vehicle.id] = updated
        return updated
    }

    override suspend fun cancelVehicle(campingId: String, vehicleId: String): CampingVehicle {
        val updated = vehicle(campingId, vehicleId).copy(
            status = VehicleStatus.Cancelled,
            hasAvailableSeats = false,
            updatedAt = Date(),
        )
        store[updated.id] = updated
        return updated
    }

    override suspend fun deleteVehicle(campingId: String, vehicleId: String) {
        checkFailure()
        store.remove(vehicleId)
    }

    override suspend fun addPassenger(
        campingId: String,
        vehicleId: String,
        registrationId: String,
        name: String,
    ): CampingVehicle =
        updateVehicle(VehicleMutation.addingPassenger(vehicle(campingId, vehicleId), registrationId, name))

    override suspend fun removePassenger(
        campingId: String,
        vehicleId: String,
        registrationId: String,
    ): CampingVehicle =
        updateVehicle(VehicleMutation.removingPassenger(vehicle(campingId, vehicleId), registrationId))

    override suspend fun requestJoin(
        campingId: String,
        vehicleId: String,
        registrationId: String,
        name: String,
    ): CampingVehicle =
        updateVehicle(VehicleMutation.addingPending(vehicle(campingId, vehicleId), registrationId, name))

    override suspend fun withdrawJoinRequest(
        campingId: String,
        vehicleId: String,
        registrationId: String,
    ): CampingVehicle =
        updateVehicle(VehicleMutation.removePending(vehicle(campingId, vehicleId), registrationId))

    override suspend fun approvePassenger(campingId: String, vehicleId: String, registrationId: String): CampingVehicle {
        val vehicle = vehicle(campingId, vehicleId)
        val name = vehicle.pendingPassengers.firstOrNull { it.id == registrationId }?.name.orEmpty()
        return updateVehicle(VehicleMutation.addingPassenger(vehicle, registrationId, name))
    }

    override suspend fun denyPassenger(campingId: String, vehicleId: String, registrationId: String): CampingVehicle =
        updateVehicle(VehicleMutation.removePending(vehicle(campingId, vehicleId), registrationId))

    override suspend fun checkInVehicle(checkIn: VehicleCheckIn): CampingVehicle {
        checkFailure()
        checkInStore.getOrPut(checkIn.vehicleId) { mutableListOf() }.add(checkIn)
        val updated = vehicle(checkIn.campingId, checkIn.vehicleId).copy(
            status = VehicleStatus.Arrived,
            arrivedAt = checkIn.checkedInAt,
            checkedInByUid = checkIn.checkedInByUid,
            updatedAt = Date(),
        )
        store[updated.id] = updated
        return updated
    }

    override suspend fun checkIns(campingId: String, vehicleId: String): List<VehicleCheckIn> {
        checkFailure()
        return checkInStore[vehicleId].orEmpty()
            .filter { it.campingId == campingId }
            .sortedByDescending { it.checkedInAt }
    }

    override suspend fun vehiclesWithAvailableSeats(campingId: String): List<CampingVehicle> {
        checkFailure()
        return store.values
            .filter { it.campingId == campingId && it.status != VehicleStatus.Cancelled && it.hasAvailableSeats && it.availableSeats > 0 }
            .sortedForVehicleDisplay()
    }

    override suspend fun peopleNeedingTransport(campingId: String): List<CampingAttendee> {
        checkFailure()
        return needingTransport.filter { it.needsTransportHelp }.sortedBy { it.displayName.lowercase() }
    }

    private fun checkFailure() {
        if (shouldFail) error("Vehicle service failed.")
    }
}

class FakeUserVehicleService(
    vehicles: List<UserVehicle> = emptyList(),
    var shouldFail: Boolean = false,
) : UserVehicleService {
    private val store = vehicles.associateBy { it.id }.toMutableMap()

    override fun vehicles(userId: String): Flow<List<UserVehicle>> =
        flowOf(store.values.filter { it.ownerUserId == userId }.sortedForUserVehicleDisplay())

    override suspend fun loadVehicles(userId: String): List<UserVehicle> {
        checkFailure()
        return store.values.filter { it.ownerUserId == userId }.sortedForUserVehicleDisplay()
    }

    override suspend fun saveVehicle(vehicle: UserVehicle): UserVehicle {
        checkFailure()
        vehicle.validationError?.let { error(it.message) }
        val updated = vehicle.copy(updatedAt = Date())
        if (updated.isDefault) {
            store.replaceAll { _, item ->
                if (item.ownerUserId == updated.ownerUserId) item.copy(isDefault = false) else item
            }
        }
        store[updated.id] = updated
        return updated
    }

    override suspend fun deleteVehicle(userId: String, vehicleId: String) {
        checkFailure()
        store.remove(vehicleId)
    }

    override suspend fun setDefault(userId: String, vehicleId: String): List<UserVehicle> {
        checkFailure()
        store.replaceAll { _, item ->
            if (item.ownerUserId == userId) item.copy(isDefault = item.id == vehicleId, updatedAt = Date()) else item
        }
        return loadVehicles(userId)
    }

    private fun checkFailure() {
        if (shouldFail) error("User vehicle service failed.")
    }
}

private fun List<CampingVehicle>.sortedForVehicleDisplay(): List<CampingVehicle> =
    sortedWith(
        compareBy<CampingVehicle> { it.status == VehicleStatus.Cancelled }
            .thenBy { it.hasArrived }
            .thenBy { it.driverName.lowercase() },
    )

private fun List<UserVehicle>.sortedForUserVehicleDisplay(): List<UserVehicle> =
    sortedWith(compareByDescending<UserVehicle> { it.isDefault }.thenBy { it.displayTitle.lowercase() })

private object Collection {
    const val Campings = "campings"
    const val Registrations = "registrations"
    const val Users = "users"
    const val Vehicles = "vehicles"
    const val VehicleCheckIns = "checkins"
}

private object Field {
    const val QrToken = "qrToken"
    const val InvitationCode = "invitationCode"
    const val HasAvailableSeats = "hasAvailableSeats"
    const val NeedsTransportHelp = "needsTransportHelp"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleBindings {
    @Binds
    abstract fun bindVehicleService(service: FirestoreVehicleService): VehicleService

    @Binds
    abstract fun bindUserVehicleService(service: FirestoreUserVehicleService): UserVehicleService
}
