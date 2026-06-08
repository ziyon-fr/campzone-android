package fr.ziyon.campzone.data.model

import fr.ziyon.campzone.data.auth.CampingAgeGroup
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampOperationsTest {

    // --- CheckIn ---

    @Test
    fun checkInFullSetWithOmitWhenNilOptionals() {
        val updatedAt = Date(11)
        val record = CheckInRecord(
            campingId = "camp-1",
            attendeeId = "att-1",
            userId = "u1",
            displayName = "Maria",
            method = CheckInMethod.Qr,
            checkedInBy = "scanner-1",
            ageGroup = CampingAgeGroup.Youth,
        )
        val payload = CheckInRecordPayload.checkInPayload(record, TS)
            .toMutableMap()
            .apply { put("updatedAt", updatedAt) }
        assertEquals("qr", payload["method"])
        assertEquals(TS, payload["checkedInAt"])
        assertEquals("youth", payload["ageGroup"])
        assertFalse(payload.containsKey("gender")) // omit-when-nil
        assertFalse(payload.containsKey("photoURL"))

        val decoded = payload.toCheckInRecordOrNull("att-1")!!
        assertEquals("att-1", decoded.attendeeId)
        assertEquals(CheckInMethod.Qr, decoded.method)
        assertEquals(updatedAt, decoded.updatedAt)

        val broken = payload.toMutableMap().apply { remove("userID") }
        assertNull(broken.toCheckInRecordOrNull("att-1"))
    }

    // --- TransportationBooking ---

    @Test
    fun transportationCreateUsesRbacLiteralsAndRawDates() {
        val from = Date(1_700_000_000_000L)
        val until = Date(1_700_086_400_000L)
        val booking = TransportationBooking(
            id = "b1",
            campingId = "camp-1",
            registrationId = "att-1",
            participantId = "att-1",
            participantKind = RegistrationParticipantKind.SelfParticipant,
            participantName = "Maria",
            userId = "u1",
            ticketToken = "tok-xyz",
            validFrom = from,
            validUntil = until,
            paymentStatus = TransportationPaymentStatus.Paid, // ignored on create
            boardingStatus = TransportationBoardingStatus.Boarded, // ignored on create
        )
        val payload = TransportationBookingPayload.createPayload(booking, TS)
        assertEquals("unpaid", payload["paymentStatus"]) // RBAC literal
        assertEquals("not_boarded", payload["boardingStatus"]) // RBAC literal
        assertEquals(from, payload["validFrom"]) // raw Date
        assertEquals(until, payload["validUntil"])
        assertFalse(payload.containsKey("guardianID")) // omit-when-nil

        val boarded = TransportationBookingPayload.markBoardedPayload(
            leg = TransportationLeg.Outbound,
            reviewerId = "scanner-1",
            reviewerName = null,
            location = null,
            now = Date(0),
            serverTimestamp = TS,
        )
        assertEquals("boarded", boarded["boardingStatus"])
        assertEquals("scanner-1", boarded["boardedBy"])
    }

    @Test
    fun canBoardLogic() {
        val base = TransportationBooking(
            id = "b1", campingId = "c", registrationId = "r", participantId = "p",
            participantKind = RegistrationParticipantKind.SelfParticipant, participantName = "M",
            userId = "u", ticketToken = "t", validFrom = Date(0), validUntil = Date(1),
        )
        assertFalse(base.canBoard) // unpaid
        assertTrue(base.copy(paymentStatus = TransportationPaymentStatus.Paid).canBoard)
        assertTrue(base.copy(paymentStatus = TransportationPaymentStatus.Waived).canBoard)
        assertFalse(
            base.copy(
                paymentStatus = TransportationPaymentStatus.Paid,
                boardingStatus = TransportationBoardingStatus.Boarded,
            ).canBoard,
        )
    }

    // --- Lodging ---

    @Test
    fun lodgingRoundTripsAndClampsCapacity() {
        val unit = LodgingUnit(
            id = "unit-1",
            campingId = "camp-1",
            name = "Cabin 3",
            kind = LodgingKind.Cabin,
            capacity = 0, // clamped to >= 1
            genderPolicy = LodgingGenderPolicy.Female,
            occupantIds = listOf("u1", "u2"),
        )
        val payload = LodgingPayload.unitPayload(unit, TS, includeCreatedAt = true)
        assertEquals(1, payload["capacity"])
        assertEquals("cabin", payload["kind"])
        assertEquals("female", payload["genderPolicy"])

        val decoded = payload.toLodgingUnitOrNull("unit-1")
        assertEquals(listOf("u1", "u2"), decoded.occupantIds)
        assertEquals(LodgingKind.Cabin, decoded.kind)
    }

    // --- VenueMap ---

    @Test
    fun venueMapImageDeleteWhenEmptyAndPinCoords() {
        val map = VenueMap(
            campingId = "camp-1",
            imageUrl = null, // delete-when-empty
            imagePublicId = null,
            points = listOf(
                VenuePoint(id = "pin-1", name = "Stage", category = VenueCategory.Stage, imageX = 0.5, imageY = 0.25),
            ),
        )
        val payload = VenueMapPayload.configPayload(map, TS, DEL)
        assertEquals(DEL, payload["imageURL"])
        assertEquals(DEL, payload["imagePublicID"])

        @Suppress("UNCHECKED_CAST")
        val pin = (payload["points"] as List<Map<String, Any?>>).first()
        assertEquals("stage", pin["category"])
        assertEquals(0.5, pin["imageX"])
        assertFalse(pin.containsKey("latitude")) // omit-when-nil

        val decoded = payload.toVenueMap("config")
        assertEquals(1, decoded.points.size)
        assertEquals(VenueCategory.Stage, decoded.points.first().category)
    }

    @Test
    fun venueMapCustomPinsWriteCustomCategoryAndIconOnlyForCustomCategory() {
        val map = VenueMap(
            campingId = "camp-1",
            points = listOf(
                VenuePoint(
                    id = "pin-custom",
                    name = "Clue Tree",
                    category = VenueCategory.Custom,
                    customCategoryName = "Clue",
                    customIconName = "leaf.fill",
                    latitude = 45.1,
                    longitude = 6.2,
                ),
                VenuePoint(
                    id = "pin-built-in",
                    name = "Stage",
                    category = VenueCategory.Stage,
                    customCategoryName = "Should not leak",
                    customIconName = "leaf.fill",
                ),
            ),
        )
        val payload = VenueMapPayload.configPayload(map, TS, DEL)

        @Suppress("UNCHECKED_CAST")
        val pins = payload["points"] as List<Map<String, Any?>>
        assertEquals("custom", pins[0]["category"])
        assertEquals("Clue", pins[0]["customCategoryName"])
        assertEquals("leaf.fill", pins[0]["customIconName"])
        assertEquals(45.1, pins[0]["latitude"])
        assertFalse(pins[1].containsKey("customCategoryName"))
        assertFalse(pins[1].containsKey("customIconName"))

        val decoded = payload.toVenueMap("config")
        assertEquals(VenueCategory.Custom, decoded.points[0].category)
        assertEquals("Clue", decoded.points[0].resolvedDisplayName)
        assertEquals("leaf.fill", decoded.points[0].resolvedIconName)
    }

    @Test
    fun venueMapCustomPinFallbacksAndIconCatalogStayCrossPlatformSized() {
        val point = VenuePoint(
            id = "pin-custom",
            name = "Custom",
            category = VenueCategory.Custom,
            customCategoryName = " ",
            customIconName = "",
        )

        assertEquals("custom", point.resolvedDisplayName)
        assertEquals(VenueIconCatalog.defaultIconName, point.resolvedIconName)
        assertTrue(VenueIconCatalog.allIconNames.size >= 40)
    }

    // --- CampFeedback ---

    @Test
    fun feedbackClampsRatingAndStampsServerTimestamp() {
        val feedback = CampFeedback(
            id = "u1",
            campingId = "camp-1",
            userId = "u1",
            displayName = "Maria",
            overallRating = 9, // clamped to 1..5
            wouldReturn = true,
            isAnonymous = true,
            programFeedback = listOf(ProgramFeedback(id = "prog-1", programTitle = "Games", rating = 4)),
        )
        val payload = CampFeedbackPayload.feedbackPayload(feedback, TS)
        assertEquals(5, payload["overallRating"])
        assertEquals(TS, payload["submittedAt"])

        val decoded = payload.toCampFeedbackOrNull("u1")!!
        assertEquals(5, decoded.overallRating)
        assertTrue(decoded.isAnonymous)
        assertEquals(1, decoded.programFeedback.size)
    }

    private companion object {
        const val TS = "serverTimestamp"
        const val DEL = "delete"
    }
}
