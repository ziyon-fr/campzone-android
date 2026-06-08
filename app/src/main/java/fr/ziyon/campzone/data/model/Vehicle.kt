package fr.ziyon.campzone.data.model

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class VehicleStatus(val wireValue: String) {
    Pending("pending"),
    Confirmed("confirmed"),
    Arrived("arrived"),
    Cancelled("cancelled");

    companion object {
        fun fromWire(value: String?): VehicleStatus =
            entries.firstOrNull { it.wireValue == value } ?: Pending
    }
}

data class CampingVehicle(
    val id: String = UUID.randomUUID().toString(),
    val campingId: String,
    val ownerUserId: String,
    val userVehicleId: String? = null,
    val driverUserId: String,
    val driverRegistrationId: String,
    val driverName: String,
    val driverPhotoUrl: String? = null,
    val plateNumber: String,
    val brand: String? = null,
    val model: String? = null,
    val color: String? = null,
    val totalSeats: Int,
    val occupiedSeats: Int,
    val hasAvailableSeats: Boolean,
    val passengerRegistrationIds: List<String> = emptyList(),
    val passengerNames: List<String> = emptyList(),
    val pendingPassengerRegistrationIds: List<String> = emptyList(),
    val pendingPassengerNames: List<String> = emptyList(),
    val qrToken: String,
    val invitationCode: String? = null,
    val status: VehicleStatus = VehicleStatus.Pending,
    val arrivedAt: Date? = null,
    val checkedInByUid: String? = null,
    val notes: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
) {
    val availableSeats: Int
        get() = max(0, totalSeats - occupiedSeats)

    val hasArrived: Boolean
        get() = status == VehicleStatus.Arrived || arrivedAt != null

    val expectedRegisteredCount: Int
        get() = passengerRegistrationIds.size + 1

    val maskedPlate: String
        get() {
            val trimmed = plateNumber.trim()
            if (trimmed.length <= 2) return "*".repeat(max(trimmed.length, 2))
            return "*".repeat(trimmed.length - 2) + trimmed.takeLast(2)
        }

    val passengers: List<VehiclePassenger>
        get() = passengerRegistrationIds.mapIndexed { index, id ->
            VehiclePassenger(id = id, name = passengerNames.getOrElse(index) { "" })
        }

    val pendingPassengers: List<VehiclePassenger>
        get() = pendingPassengerRegistrationIds.mapIndexed { index, id ->
            VehiclePassenger(id = id, name = pendingPassengerNames.getOrElse(index) { "" })
        }

    fun includesPassenger(registrationId: String): Boolean =
        registrationId in passengerRegistrationIds

    fun involves(registrationId: String): Boolean =
        driverRegistrationId == registrationId || registrationId in passengerRegistrationIds

    val validationError: VehicleValidationError?
        get() = when {
            plateNumber.trim().isEmpty() -> VehicleValidationError.PlateRequired
            driverName.trim().isEmpty() -> VehicleValidationError.DriverNameRequired
            driverRegistrationId.trim().isEmpty() -> VehicleValidationError.DriverRegistrationRequired
            totalSeats !in MinSeats..MaxSeats -> VehicleValidationError.SeatsOutOfRange
            occupiedSeats < 1 -> VehicleValidationError.OccupiedBelowOne
            occupiedSeats > totalSeats -> VehicleValidationError.OccupiedExceedsTotal
            passengerRegistrationIds.toSet().size != passengerRegistrationIds.size ->
                VehicleValidationError.DuplicatePassengers
            driverRegistrationId in passengerRegistrationIds -> VehicleValidationError.DriverListedAsPassenger
            qrToken.trim().isEmpty() -> VehicleValidationError.TokenRequired
            else -> null
        }

    companion object {
        const val MinSeats = 1
        const val MaxSeats = 9
    }
}

data class VehiclePassenger(
    val id: String,
    val name: String,
)

enum class VehicleValidationError(val message: String) {
    PlateRequired("Enter the car's plate number."),
    DriverNameRequired("Enter the driver's name."),
    DriverRegistrationRequired("The driver must be a registered participant."),
    SeatsOutOfRange("Total seats must be between 1 and 9."),
    OccupiedExceedsTotal("People in the car can't exceed total seats."),
    OccupiedBelowOne("The car must include at least the driver."),
    DuplicatePassengers("A passenger is listed more than once."),
    DriverListedAsPassenger("The driver can't also be a passenger."),
    TokenRequired("The vehicle is missing its secure code."),
}

data class VehicleCheckIn(
    val id: String = UUID.randomUUID().toString(),
    val campingId: String,
    val vehicleId: String,
    val scannedToken: String,
    val checkedInByUid: String,
    val checkedInByName: String? = null,
    val checkedInAt: Date = Date(),
    val expectedPassengerCount: Int,
    val actualPassengerCount: Int,
    val presentRegistrationIds: List<String>,
    val missingRegistrationIds: List<String>,
    val plateNumberConfirmed: Boolean,
    val notes: String? = null,
) {
    val everyoneArrived: Boolean
        get() = missingRegistrationIds.isEmpty()
}

data class UserVehicle(
    val id: String = UUID.randomUUID().toString(),
    val ownerUserId: String,
    val nickname: String? = null,
    val plateNumber: String,
    val brand: String? = null,
    val model: String? = null,
    val color: String? = null,
    val defaultTotalSeats: Int = 5,
    val isDefault: Boolean = false,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
) {
    val clampedTotalSeats: Int
        get() = defaultTotalSeats.coerceIn(CampingVehicle.MinSeats, CampingVehicle.MaxSeats)

    val displayTitle: String
        get() {
            val cleanedNickname = nickname.clean()
            if (cleanedNickname != null) return cleanedNickname
            val brandModel = listOfNotNull(brand.clean(), model.clean()).joinToString(" ")
            return brandModel.ifBlank { plateNumber }
        }

    val detailSubtitle: String
        get() = listOfNotNull(brand.clean(), model.clean(), color.clean()).joinToString(" - ")

    val validationError: VehicleValidationError?
        get() = when {
            plateNumber.trim().isEmpty() -> VehicleValidationError.PlateRequired
            defaultTotalSeats !in CampingVehicle.MinSeats..CampingVehicle.MaxSeats ->
                VehicleValidationError.SeatsOutOfRange
            else -> null
        }
}

data class VehicleCheckInPayload(val token: String) {
    fun encoded(): String = "$Scheme://$Host/${token.asUrlSegment()}"

    fun webUrl(): String = "https://$WebHost/checkin/vehicle/${token.asUrlSegment()}"

    companion object {
        const val Scheme = "campzone"
        const val Host = "vehicle-checkin"
        const val WebHost = "campzone.app"

        fun decode(scannedValue: String?): VehicleCheckInPayload? {
            val trimmed = scannedValue?.trim()?.takeUnless { it.isBlank() } ?: return null
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            val host = (uri.host ?: uri.authority)?.lowercase(Locale.ROOT).orEmpty()

            if (scheme == Scheme && host == Host) {
                val token = uri.rawPath
                    ?.split("/")
                    ?.firstOrNull { it.isNotBlank() }
                    ?.decodeUrlComponent()
                    ?: parseQuery(uri.rawQuery)["t"]
                return token?.takeUnless { it.isBlank() }?.let(::VehicleCheckInPayload)
            }

            if (scheme == "https" && host == WebHost) {
                val parts = uri.rawPath
                    ?.split("/")
                    ?.mapNotNull { it.takeIf(String::isNotBlank)?.decodeUrlComponent() }
                    .orEmpty()
                if (parts.size == 3 && parts[0] == "checkin" && parts[1] == "vehicle") {
                    return parts[2].takeUnless { it.isBlank() }?.let(::VehicleCheckInPayload)
                }
            }

            return null
        }
    }
}

object VehicleTokenFactory {
    private val secureRandom = SecureRandom()
    private val inviteAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()

    fun makeToken(): String =
        (UUID.randomUUID().toString() + UUID.randomUUID().toString())
            .replace("-", "")
            .lowercase(Locale.ROOT)

    fun makeInvitationCode(length: Int = 6): String =
        buildString {
            repeat(length) {
                append(inviteAlphabet[secureRandom.nextInt(inviteAlphabet.size)])
            }
        }
}

object VehicleMutation {
    fun addingPassenger(
        vehicle: CampingVehicle,
        registrationId: String,
        name: String,
    ): CampingVehicle {
        var updated = removePending(vehicle, registrationId)
        if (
            registrationId == updated.driverRegistrationId ||
            registrationId in updated.passengerRegistrationIds
        ) {
            return updated.copy(updatedAt = Date())
        }
        val passengerIds = updated.passengerRegistrationIds + registrationId
        val passengerNames = updated.passengerNames + name
        val occupied = min(updated.totalSeats, updated.occupiedSeats + 1)
        return updated.copy(
            passengerRegistrationIds = passengerIds,
            passengerNames = passengerNames,
            occupiedSeats = occupied,
            updatedAt = Date(),
        )
    }

    fun removingPassenger(
        vehicle: CampingVehicle,
        registrationId: String,
    ): CampingVehicle {
        var updated = vehicle
        val index = updated.passengerRegistrationIds.indexOf(registrationId)
        if (index >= 0) {
            updated = updated.copy(
                passengerRegistrationIds = updated.passengerRegistrationIds.filterIndexed { i, _ -> i != index },
                passengerNames = updated.passengerNames.filterIndexed { i, _ -> i != index },
                occupiedSeats = max(1, updated.occupiedSeats - 1),
            )
        }
        updated = removePending(updated, registrationId)
        return updated.copy(
            updatedAt = Date(),
        )
    }

    fun addingPending(
        vehicle: CampingVehicle,
        registrationId: String,
        name: String,
    ): CampingVehicle {
        if (
            registrationId == vehicle.driverRegistrationId ||
            registrationId in vehicle.passengerRegistrationIds ||
            registrationId in vehicle.pendingPassengerRegistrationIds
        ) {
            return vehicle
        }
        return vehicle.copy(
            pendingPassengerRegistrationIds = vehicle.pendingPassengerRegistrationIds + registrationId,
            pendingPassengerNames = vehicle.pendingPassengerNames + name,
            updatedAt = Date(),
        )
    }

    fun removePending(
        vehicle: CampingVehicle,
        registrationId: String,
    ): CampingVehicle {
        val index = vehicle.pendingPassengerRegistrationIds.indexOf(registrationId)
        if (index < 0) return vehicle
        return vehicle.copy(
            pendingPassengerRegistrationIds = vehicle.pendingPassengerRegistrationIds
                .filterIndexed { i, _ -> i != index },
            pendingPassengerNames = vehicle.pendingPassengerNames
                .filterIndexed { i, _ -> i != index },
            updatedAt = Date(),
        )
    }
}

sealed interface VehicleScanResult {
    data class Resolved(val vehicle: CampingVehicle) : VehicleScanResult
    data class AlreadyArrived(val vehicle: CampingVehicle) : VehicleScanResult
    data class Cancelled(val vehicle: CampingVehicle) : VehicleScanResult
    data object WrongCamping : VehicleScanResult
    data object UnknownVehicle : VehicleScanResult
    data object Malformed : VehicleScanResult
}

internal fun Map<String, Any?>.toCampingVehicleOrNull(documentId: String): CampingVehicle? {
    val campingId = stringValue("campingID") ?: return null
    val ownerUserId = stringValue("ownerUserID") ?: return null
    val driverUserId = stringValue("driverUserID") ?: return null
    val driverRegistrationId = stringValue("driverRegistrationID") ?: return null
    val driverName = rawStringValue("driverName")?.trim().orEmpty()
    val plateNumber = rawStringValue("plateNumber")?.trim().orEmpty()
    val totalSeats = intValue("totalSeats") ?: 1
    val occupiedSeats = intValue("occupiedSeats") ?: 1
    val qrToken = stringValue("qrToken") ?: return null

    return CampingVehicle(
        id = stringValue("id") ?: documentId,
        campingId = campingId,
        ownerUserId = ownerUserId,
        userVehicleId = stringValue("userVehicleID"),
        driverUserId = driverUserId,
        driverRegistrationId = driverRegistrationId,
        driverName = driverName,
        driverPhotoUrl = stringValue("driverPhotoURL"),
        plateNumber = plateNumber,
        brand = stringValue("brand"),
        model = stringValue("model"),
        color = stringValue("color"),
        totalSeats = totalSeats.coerceIn(CampingVehicle.MinSeats, CampingVehicle.MaxSeats),
        occupiedSeats = occupiedSeats.coerceIn(1, totalSeats.coerceAtLeast(1)),
        hasAvailableSeats = boolValue("hasAvailableSeats") ?: (occupiedSeats < totalSeats),
        passengerRegistrationIds = stringListValue("passengerRegistrationIDs").distinct(),
        passengerNames = rawStringListValue("passengerNames"),
        pendingPassengerRegistrationIds = stringListValue("pendingPassengerRegistrationIDs").distinct(),
        pendingPassengerNames = rawStringListValue("pendingPassengerNames"),
        qrToken = qrToken,
        invitationCode = stringValue("invitationCode")?.uppercase(Locale.ROOT),
        status = VehicleStatus.fromWire(stringValue("status")),
        arrivedAt = dateValue("arrivedAt"),
        checkedInByUid = stringValue("checkedInByUID"),
        notes = stringValue("notes"),
        createdAt = dateValue("createdAt") ?: Date(),
        updatedAt = dateValue("updatedAt") ?: Date(),
    )
}

internal fun Map<String, Any?>.toVehicleCheckInOrNull(documentId: String): VehicleCheckIn? {
    val campingId = stringValue("campingID") ?: return null
    val vehicleId = stringValue("vehicleID") ?: return null
    val scannedToken = stringValue("scannedToken") ?: return null
    val checkedInByUid = stringValue("checkedInByUID") ?: return null
    val checkedInAt = dateValue("checkedInAt") ?: return null

    return VehicleCheckIn(
        id = stringValue("id") ?: documentId,
        campingId = campingId,
        vehicleId = vehicleId,
        scannedToken = scannedToken,
        checkedInByUid = checkedInByUid,
        checkedInByName = stringValue("checkedInByName"),
        checkedInAt = checkedInAt,
        expectedPassengerCount = intValue("expectedPassengerCount") ?: 0,
        actualPassengerCount = intValue("actualPassengerCount") ?: 0,
        presentRegistrationIds = stringListValue("presentRegistrationIDs"),
        missingRegistrationIds = stringListValue("missingRegistrationIDs"),
        plateNumberConfirmed = boolValue("plateNumberConfirmed") ?: false,
        notes = stringValue("notes"),
    )
}

internal fun Map<String, Any?>.toUserVehicleOrNull(documentId: String): UserVehicle? {
    val ownerUserId = stringValue("ownerUserID") ?: return null
    val plateNumber = rawStringValue("plateNumber")?.trim().orEmpty()
    return UserVehicle(
        id = stringValue("id") ?: documentId,
        ownerUserId = ownerUserId,
        nickname = stringValue("nickname"),
        plateNumber = plateNumber,
        brand = stringValue("brand"),
        model = stringValue("model"),
        color = stringValue("color"),
        defaultTotalSeats = (intValue("defaultTotalSeats") ?: 5)
            .coerceIn(CampingVehicle.MinSeats, CampingVehicle.MaxSeats),
        isDefault = boolValue("isDefault") ?: false,
        createdAt = dateValue("createdAt") ?: Date(),
        updatedAt = dateValue("updatedAt") ?: Date(),
    )
}

internal object VehiclePayload {
    fun vehiclePayload(
        vehicle: CampingVehicle,
        serverTimestamp: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "id" to vehicle.id,
            "campingID" to vehicle.campingId,
            "ownerUserID" to vehicle.ownerUserId,
            "driverUserID" to vehicle.driverUserId,
            "driverRegistrationID" to vehicle.driverRegistrationId,
            "driverName" to vehicle.driverName.trim(),
            "plateNumber" to vehicle.plateNumber.trim().uppercase(Locale.ROOT),
            "totalSeats" to vehicle.totalSeats.coerceIn(CampingVehicle.MinSeats, CampingVehicle.MaxSeats),
            "occupiedSeats" to vehicle.occupiedSeats.coerceIn(
                CampingVehicle.MinSeats,
                vehicle.totalSeats.coerceIn(CampingVehicle.MinSeats, CampingVehicle.MaxSeats),
            ),
            "hasAvailableSeats" to vehicle.hasAvailableSeats,
            "passengerRegistrationIDs" to vehicle.passengerRegistrationIds.distinct(),
            "passengerNames" to vehicle.passengerNames,
            "pendingPassengerRegistrationIDs" to vehicle.pendingPassengerRegistrationIds.distinct(),
            "pendingPassengerNames" to vehicle.pendingPassengerNames,
            "qrToken" to vehicle.qrToken.trim(),
            "status" to vehicle.status.wireValue,
            "updatedAt" to serverTimestamp,
        )
        vehicle.userVehicleId.clean()?.let { payload["userVehicleID"] = it }
        vehicle.driverPhotoUrl.clean()?.let { payload["driverPhotoURL"] = it }
        vehicle.brand.clean()?.let { payload["brand"] = it }
        vehicle.model.clean()?.let { payload["model"] = it }
        vehicle.color.clean()?.let { payload["color"] = it }
        vehicle.invitationCode.clean()?.let { payload["invitationCode"] = it.uppercase(Locale.ROOT) }
        vehicle.arrivedAt?.let { payload["arrivedAt"] = it }
        vehicle.checkedInByUid.clean()?.let { payload["checkedInByUID"] = it }
        vehicle.notes.clean()?.let { payload["notes"] = it }
        if (includeCreatedAt) payload["createdAt"] = serverTimestamp
        return payload
    }

    fun updatePayload(
        vehicle: CampingVehicle,
        serverTimestamp: Any,
        deleteField: Any,
    ): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "plateNumber" to vehicle.plateNumber.trim().uppercase(Locale.ROOT),
            "brand" to (vehicle.brand.clean() ?: deleteField),
            "model" to (vehicle.model.clean() ?: deleteField),
            "color" to (vehicle.color.clean() ?: deleteField),
            "totalSeats" to vehicle.totalSeats.coerceIn(CampingVehicle.MinSeats, CampingVehicle.MaxSeats),
            "occupiedSeats" to vehicle.occupiedSeats.coerceIn(
                CampingVehicle.MinSeats,
                vehicle.totalSeats.coerceIn(CampingVehicle.MinSeats, CampingVehicle.MaxSeats),
            ),
            "hasAvailableSeats" to vehicle.hasAvailableSeats,
            "passengerRegistrationIDs" to vehicle.passengerRegistrationIds.distinct(),
            "passengerNames" to vehicle.passengerNames,
            "pendingPassengerRegistrationIDs" to vehicle.pendingPassengerRegistrationIds.distinct(),
            "pendingPassengerNames" to vehicle.pendingPassengerNames,
            "notes" to (vehicle.notes.clean() ?: deleteField),
            "status" to vehicle.status.wireValue,
            "updatedAt" to serverTimestamp,
        )

    fun cancelPayload(serverTimestamp: Any): Map<String, Any?> =
        linkedMapOf(
            "status" to VehicleStatus.Cancelled.wireValue,
            "hasAvailableSeats" to false,
            "updatedAt" to serverTimestamp,
        )

    fun pendingPayload(
        vehicle: CampingVehicle,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "pendingPassengerRegistrationIDs" to vehicle.pendingPassengerRegistrationIds.distinct(),
            "pendingPassengerNames" to vehicle.pendingPassengerNames,
            "updatedAt" to serverTimestamp,
        )

    fun arrivalPayload(
        checkIn: VehicleCheckIn,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "status" to VehicleStatus.Arrived.wireValue,
            "arrivedAt" to checkIn.checkedInAt,
            "checkedInByUID" to checkIn.checkedInByUid,
            "updatedAt" to serverTimestamp,
        )

    fun checkInPayload(checkIn: VehicleCheckIn): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "id" to checkIn.id,
            "campingID" to checkIn.campingId,
            "vehicleID" to checkIn.vehicleId,
            "scannedToken" to checkIn.scannedToken,
            "checkedInByUID" to checkIn.checkedInByUid,
            "checkedInAt" to checkIn.checkedInAt,
            "expectedPassengerCount" to checkIn.expectedPassengerCount,
            "actualPassengerCount" to checkIn.actualPassengerCount,
            "presentRegistrationIDs" to checkIn.presentRegistrationIds,
            "missingRegistrationIDs" to checkIn.missingRegistrationIds,
            "plateNumberConfirmed" to checkIn.plateNumberConfirmed,
        )
        checkIn.checkedInByName.clean()?.let { payload["checkedInByName"] = it }
        checkIn.notes.clean()?.let { payload["notes"] = it }
        return payload
    }
}

internal object UserVehiclePayload {
    fun userVehiclePayload(
        vehicle: UserVehicle,
        serverTimestamp: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "id" to vehicle.id,
            "ownerUserID" to vehicle.ownerUserId,
            "plateNumber" to vehicle.plateNumber.trim().uppercase(Locale.ROOT),
            "defaultTotalSeats" to vehicle.defaultTotalSeats.coerceIn(
                CampingVehicle.MinSeats,
                CampingVehicle.MaxSeats,
            ),
            "isDefault" to vehicle.isDefault,
            "updatedAt" to serverTimestamp,
        )
        vehicle.nickname.clean()?.let { payload["nickname"] = it }
        vehicle.brand.clean()?.let { payload["brand"] = it }
        vehicle.model.clean()?.let { payload["model"] = it }
        vehicle.color.clean()?.let { payload["color"] = it }
        if (includeCreatedAt) payload["createdAt"] = serverTimestamp
        return payload
    }
}

internal object RegistrationTransportPayload {
    fun payload(
        transportationMode: TransportationMode?,
        vehicleId: String?,
        isDriver: Boolean,
        needsTransportHelp: Boolean,
        notes: String?,
        serverTimestamp: Any,
        deleteField: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "transportationMode" to (transportationMode?.wireValue ?: deleteField),
            "vehicleID" to (vehicleId.clean() ?: deleteField),
            "isDriver" to isDriver,
            "needsTransportHelp" to needsTransportHelp,
            "transportationNotes" to (notes.clean() ?: deleteField),
            "updatedAt" to serverTimestamp,
        )
}

private fun parseQuery(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    return rawQuery.split("&").mapNotNull { part ->
        val pieces = part.split("=", limit = 2)
        val key = pieces.getOrNull(0)?.decodeUrlComponent()?.takeUnless { it.isBlank() }
        val value = pieces.getOrNull(1)?.decodeUrlComponent()?.takeUnless { it.isBlank() }
        if (key != null && value != null) key to value else null
    }.toMap()
}

private fun String.decodeUrlComponent(): String =
    runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrDefault(this)

private fun String.asUrlSegment(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private fun String?.clean(): String? =
    this?.trim()?.takeUnless { it.isBlank() }
