package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.data.vehicle.FakeVehicleService
import fr.ziyon.campzone.data.vehicle.VehicleSeatUnavailableMessage
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleModelTest {

    @Test
    fun vehicleStatusRawValuesMatchInteropContract() {
        assertEquals(
            listOf("pending", "confirmed", "arrived", "cancelled"),
            VehicleStatus.entries.map { it.wireValue },
        )
        assertEquals(VehicleStatus.Pending, VehicleStatus.fromWire("futureStatus"))
    }

    @Test
    fun tokenAndInvitationCodeHaveIosShape() {
        val first = VehicleTokenFactory.makeToken()
        val second = VehicleTokenFactory.makeToken()

        assertEquals(64, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(first, second)

        val code = VehicleTokenFactory.makeInvitationCode()
        assertEquals(6, code.length)
        assertTrue(code.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
        assertFalse(code.any { it in "IO01" })
    }

    @Test
    fun vehicleQrPayloadDecodesOnlySupportedForms() {
        val token = "a".repeat(64)

        assertEquals(
            token,
            VehicleCheckInPayload.decode("campzone://vehicle-checkin/$token")?.token,
        )
        assertEquals(
            token,
            VehicleCheckInPayload.decode("campzone://vehicle-checkin?t=$token")?.token,
        )
        assertEquals(
            token,
            VehicleCheckInPayload.decode("https://campzone.app/checkin/vehicle/$token")?.token,
        )
        assertNull(VehicleCheckInPayload.decode("https://example.com/checkin/vehicle/$token"))
        assertNull(VehicleCheckInPayload.decode("http://campzone.app/checkin/vehicle/$token"))
        assertNull(VehicleCheckInPayload.decode("campzone://check-in/$token"))
        assertNull(VehicleCheckInPayload.decode(""))
    }

    @Test
    fun validationAndDerivedFieldsProtectPrivacyAndSeatBounds() {
        val vehicle = sampleVehicle(
            plateNumber = "AB-123-CD",
            totalSeats = 5,
            occupiedSeats = 3,
            passengerRegistrationIds = listOf("p1", "p2"),
        )

        assertEquals(2, vehicle.availableSeats)
        assertEquals(3, vehicle.accountedOccupiedSeats)
        assertEquals(3, vehicle.expectedRegisteredCount)
        assertEquals("*******CD", vehicle.maskedPlate)
        assertNull(vehicle.validationError)

        assertEquals(
            VehicleValidationError.PlateRequired,
            vehicle.copy(plateNumber = " ").validationError,
        )
        assertEquals(
            VehicleValidationError.OccupiedExceedsTotal,
            vehicle.copy(totalSeats = 2, occupiedSeats = 3).validationError,
        )
        assertEquals(
            VehicleValidationError.DuplicatePassengers,
            vehicle.copy(passengerRegistrationIds = listOf("p1", "p1")).validationError,
        )
        assertEquals(
            VehicleValidationError.DriverListedAsPassenger,
            vehicle.copy(passengerRegistrationIds = listOf("driver-reg")).validationError,
        )
    }

    @Test
    fun occupiedSeatsAlwaysCountsDriverAndApprovedPassengers() {
        val vehicle = sampleVehicle(
            totalSeats = 5,
            occupiedSeats = 3,
            passengerRegistrationIds = listOf("p1", "p2", "p3"),
            passengerNames = listOf("One", "Two", "Three"),
        )

        assertEquals(4, vehicle.expectedRegisteredCount)
        assertEquals(4, vehicle.accountedOccupiedSeats)
        assertEquals(1, vehicle.availableSeats)
        assertEquals(1, vehicle.offeredSeatCount)
    }

    @Test
    fun offeredSeatCountFallsBackToAvailableWhenUnset() {
        val vehicle = sampleVehicle(totalSeats = 5, occupiedSeats = 3)

        assertNull(vehicle.offeredSeats)
        assertEquals(2, vehicle.offeredSeatCount)
        assertEquals(0, vehicle.copy(hasAvailableSeats = false).offeredSeatCount)
    }

    @Test
    fun offeredSeatCountRespectsExplicitCap() {
        val vehicle = sampleVehicle(totalSeats = 5, occupiedSeats = 3, offeredSeats = 1)

        assertEquals(1, vehicle.offeredSeatCount)
        assertEquals(2, vehicle.copy(offeredSeats = 9).offeredSeatCount)
        assertEquals(0, vehicle.copy(offeredSeats = 0).offeredSeatCount)
    }

    @Test
    fun passengerMutationsKeepListsAlignedAndOccupancyBounded() {
        val vehicle = sampleVehicle(
            totalSeats = 3,
            occupiedSeats = 1,
            pendingPassengerRegistrationIds = listOf("p1"),
            pendingPassengerNames = listOf("Joao"),
        )

        val approved = VehicleMutation.addingPassenger(vehicle, "p1", "Joao")
        assertEquals(listOf("p1"), approved.passengerRegistrationIds)
        assertEquals(listOf("Joao"), approved.passengerNames)
        assertEquals(emptyList<String>(), approved.pendingPassengerRegistrationIds)
        assertEquals(2, approved.occupiedSeats)

        val capped = VehicleMutation.addingPassenger(approved, "p2", "Maria")
        assertEquals(3, capped.occupiedSeats)
        assertEquals(listOf("p1", "p2"), capped.passengerRegistrationIds)

        val removed = VehicleMutation.removingPassenger(capped, "p1")
        assertEquals(listOf("p2"), removed.passengerRegistrationIds)
        assertEquals(listOf("Maria"), removed.passengerNames)
        assertEquals(2, removed.occupiedSeats)
    }

    @Test
    fun passengerMutationsRequireOfferedSeatAndPreservePendingRequests() {
        val notOffering = sampleVehicle(
            totalSeats = 5,
            occupiedSeats = 1,
            hasAvailableSeats = false,
        )

        val pendingWhenNotOffering = VehicleMutation.addingPending(notOffering, "p1", "Joao")
        assertEquals(emptyList<String>(), pendingWhenNotOffering.pendingPassengerRegistrationIds)

        val exhaustedOffer = notOffering.copy(
            hasAvailableSeats = true,
            offeredSeats = 0,
            pendingPassengerRegistrationIds = listOf("p1"),
            pendingPassengerNames = listOf("Joao"),
        )

        val approvedWhenOfferExhausted = VehicleMutation.addingPassenger(exhaustedOffer, "p1", "Joao")
        assertEquals(emptyList<String>(), approvedWhenOfferExhausted.passengerRegistrationIds)
        assertEquals(listOf("p1"), approvedWhenOfferExhausted.pendingPassengerRegistrationIds)
        assertEquals(1, approvedWhenOfferExhausted.occupiedSeats)
    }

    @Test
    fun removingPassengerRepairsLegacyPassengerOnlyOccupiedSeats() {
        val vehicle = sampleVehicle(
            totalSeats = 5,
            occupiedSeats = 3,
            passengerRegistrationIds = listOf("p1", "p2", "p3"),
            passengerNames = listOf("One", "Two", "Three"),
            offeredSeats = 1,
        )

        val removed = VehicleMutation.removingPassenger(vehicle, "p1")

        assertEquals(listOf("p2", "p3"), removed.passengerRegistrationIds)
        assertEquals(3, removed.occupiedSeats)
        assertEquals(2, removed.availableSeats)
        assertEquals(2, removed.offeredSeats)
    }

    @Test
    fun passengerMutationsUpdateExplicitOfferedSeatsButKeepLegacyNil() {
        val cappedOffer = sampleVehicle(
            totalSeats = 5,
            occupiedSeats = 1,
            offeredSeats = 2,
        )

        val first = VehicleMutation.addingPassenger(cappedOffer, "p1", "Joao")
        assertEquals(1, first.offeredSeats)
        val second = VehicleMutation.addingPassenger(first, "p2", "Maria")
        assertEquals(0, second.offeredSeats)
        val third = VehicleMutation.addingPassenger(second, "p3", "Alex")
        assertEquals(0, third.offeredSeats)
        assertEquals(listOf("p1", "p2"), third.passengerRegistrationIds)
        assertEquals(0, third.offeredSeatCount)

        val restored = VehicleMutation.removingPassenger(third, "p2")
        assertEquals(1, restored.offeredSeats)

        val legacy = VehicleMutation.addingPassenger(
            sampleVehicle(totalSeats = 5, occupiedSeats = 1, offeredSeats = null),
            "p1",
            "Joao",
        )
        assertNull(legacy.offeredSeats)
        assertEquals(3, legacy.offeredSeatCount)
    }

    @Test
    fun vehicleServiceRejectsPassengerChangesWhenOfferUnavailable() = runTest {
        val closedOffer = sampleVehicle(
            hasAvailableSeats = false,
            pendingPassengerRegistrationIds = listOf("p1"),
            pendingPassengerNames = listOf("Joao"),
        )
        val service = FakeVehicleService(vehicles = listOf(closedOffer))

        val requestError = runCatching {
            service.requestJoin(closedOffer.campingId, closedOffer.id, "p2", "Maria")
        }.exceptionOrNull()
        assertEquals(VehicleSeatUnavailableMessage, requestError?.message)

        val addError = runCatching {
            service.addPassenger(closedOffer.campingId, closedOffer.id, "p2", "Maria")
        }.exceptionOrNull()
        assertEquals(VehicleSeatUnavailableMessage, addError?.message)

        val approveError = runCatching {
            service.approvePassenger(closedOffer.campingId, closedOffer.id, "p1")
        }.exceptionOrNull()
        assertEquals(VehicleSeatUnavailableMessage, approveError?.message)
        assertEquals(listOf("p1"), service.vehicle(closedOffer.campingId, closedOffer.id).pendingPassengerRegistrationIds)
    }

    @Test
    fun assignmentConflictIncludesOtherActiveDriversPassengersAndPendingRequests() {
        val target = sampleVehicle(id = "veh-1")
        val otherPassenger = sampleVehicle(
            id = "veh-2",
            passengerRegistrationIds = listOf("p1"),
            passengerNames = listOf("Joao"),
        )
        val otherPending = sampleVehicle(
            id = "veh-3",
            pendingPassengerRegistrationIds = listOf("p2"),
            pendingPassengerNames = listOf("Maria"),
        )
        val cancelled = sampleVehicle(
            id = "veh-4",
            driverUserId = "cancelled-driver",
            driverRegistrationId = "p3",
            status = VehicleStatus.Cancelled,
        )
        val vehicles = listOf(target, otherPassenger, otherPending, cancelled)

        assertTrue(VehicleMutation.hasActiveAssignmentConflict(vehicles, listOf("p1"), excludingVehicleId = target.id))
        assertTrue(VehicleMutation.hasActiveAssignmentConflict(vehicles, listOf("p2"), excludingVehicleId = target.id))
        assertFalse(VehicleMutation.hasActiveAssignmentConflict(vehicles, listOf("p3"), excludingVehicleId = target.id))
        assertFalse(VehicleMutation.hasActiveAssignmentConflict(vehicles, listOf("p1"), excludingVehicleId = otherPassenger.id))
    }

    @Test
    fun vehiclePayloadUsesExactFirestoreKeysAndOmitsTokenFromArrivalPatch() {
        val vehicle = sampleVehicle(invitationCode = "abc234", offeredSeats = 2)
        val payload = VehiclePayload.vehiclePayload(vehicle, TS, includeCreatedAt = true)

        assertEquals("camp-1", payload["campingID"])
        assertEquals("driver-user", payload["driverUserID"])
        assertEquals("driver-reg", payload["driverRegistrationID"])
        assertEquals("AB-123-CD", payload["plateNumber"])
        assertEquals("ABC234", payload["invitationCode"])
        assertEquals(2, payload["offeredSeats"])
        assertEquals("pending", payload["status"])
        assertEquals(TS, payload["createdAt"])
        assertEquals(TS, payload["updatedAt"])

        val arrival = VehiclePayload.arrivalPayload(
            checkIn = VehicleCheckIn(
                campingId = "camp-1",
                vehicleId = "veh-1",
                scannedToken = vehicle.qrToken,
                checkedInByUid = "leader-1",
                checkedInAt = Date(5),
                expectedPassengerCount = 2,
                actualPassengerCount = 1,
                presentRegistrationIds = listOf("driver-reg"),
                missingRegistrationIds = listOf("p1"),
                plateNumberConfirmed = true,
            ),
            serverTimestamp = TS,
        )
        assertEquals("arrived", arrival["status"])
        assertFalse(arrival.containsKey("qrToken"))
        assertFalse(arrival.containsKey("passengerRegistrationIDs"))
    }

    @Test
    fun vehicleUpdatePayloadStaysWithinDriverManagerAllowlist() {
        val payload = VehiclePayload.updatePayload(
            vehicle = sampleVehicle().copy(
                brand = null,
                notes = "",
                status = VehicleStatus.Confirmed,
                offeredSeats = 1,
            ),
            serverTimestamp = TS,
            deleteField = DEL,
        )

        assertEquals("AB-123-CD", payload["plateNumber"])
        assertEquals("confirmed", payload["status"])
        assertEquals(1, payload["offeredSeats"])
        assertEquals(DEL, payload["brand"])
        assertEquals(DEL, payload["notes"])
        assertFalse(payload.containsKey("id"))
        assertFalse(payload.containsKey("campingID"))
        assertFalse(payload.containsKey("ownerUserID"))
        assertFalse(payload.containsKey("driverUserID"))
        assertFalse(payload.containsKey("driverRegistrationID"))
        assertFalse(payload.containsKey("qrToken"))
        assertFalse(payload.containsKey("invitationCode"))
        assertFalse(payload.containsKey("createdAt"))
    }

    @Test
    fun passengerPayloadStaysWithinSelfRemovalAllowlist() {
        val removed = VehicleMutation.removingPassenger(
            sampleVehicle(
                totalSeats = 5,
                occupiedSeats = 3,
                passengerRegistrationIds = listOf("p1", "p2"),
                passengerNames = listOf("Joao", "Maria"),
                offeredSeats = 1,
            ),
            "p1",
        )
        val payload = VehiclePayload.passengerPayload(removed, TS, DEL)

        assertEquals(2, payload["occupiedSeats"])
        assertEquals(2, payload["offeredSeats"])
        assertEquals(listOf("p2"), payload["passengerRegistrationIDs"])
        assertEquals(listOf("Maria"), payload["passengerNames"])
        assertFalse(payload.containsKey("plateNumber"))
        assertFalse(payload.containsKey("driverUserID"))
        assertFalse(payload.containsKey("qrToken"))
    }

    private fun sampleVehicle(
        id: String = "veh-1",
        plateNumber: String = "AB-123-CD",
        driverUserId: String = "driver-user",
        driverRegistrationId: String = "driver-reg",
        totalSeats: Int = 5,
        occupiedSeats: Int = 1,
        passengerRegistrationIds: List<String> = emptyList(),
        passengerNames: List<String> = emptyList(),
        pendingPassengerRegistrationIds: List<String> = emptyList(),
        pendingPassengerNames: List<String> = emptyList(),
        invitationCode: String? = "INV234",
        offeredSeats: Int? = null,
        hasAvailableSeats: Boolean = true,
        status: VehicleStatus = VehicleStatus.Pending,
    ) = CampingVehicle(
        id = id,
        campingId = "camp-1",
        ownerUserId = driverUserId,
        driverUserId = driverUserId,
        driverRegistrationId = driverRegistrationId,
        driverName = "Driver",
        driverPhotoUrl = "https://example.com/driver.jpg",
        plateNumber = plateNumber,
        brand = "Toyota",
        model = "Yaris",
        color = "Blue",
        totalSeats = totalSeats,
        occupiedSeats = occupiedSeats,
        hasAvailableSeats = hasAvailableSeats,
        offeredSeats = offeredSeats,
        passengerRegistrationIds = passengerRegistrationIds,
        passengerNames = passengerNames,
        pendingPassengerRegistrationIds = pendingPassengerRegistrationIds,
        pendingPassengerNames = pendingPassengerNames,
        qrToken = "a".repeat(64),
        invitationCode = invitationCode,
        status = status,
    )

    private companion object {
        val TS = Date(123)
        const val DEL = "__DELETE__"
    }
}
