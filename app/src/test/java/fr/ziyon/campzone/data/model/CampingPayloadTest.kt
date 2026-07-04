package fr.ziyon.campzone.data.model

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampingPayloadTest {

    @Test
    fun campingPayloadWritesContractShape() {
        val payload = CampingPayload.campingPayload(
            camping = fullCamping(),
            serverTimestamp = TS,
            deleteField = DEL,
            includeCreatedAt = true,
        )

        assertEquals("summer-2026", payload["id"])
        assertEquals("Summer Camp", payload["title"])
        assertEquals("open", payload["registrationStatus"])
        assertEquals("published", payload["publicationStatus"])
        // organizerLevel is a {type,value} map, not a string
        assertEquals(
            mapOf("type" to "church", "value" to "Paris Central SDA"),
            payload["organizerLevel"],
        )
        assertEquals("Pine Valley", payload["location"])
        // feeCurrency stored lowercase
        assertEquals("eur", payload["feeCurrency"])
        assertEquals(12000, payload["registrationFeeCents"])
        assertEquals(80, payload["participantCapacity"])
        assertEquals(TS, payload["updatedAt"])
        assertEquals(TS, payload["createdAt"])
        assertEquals("admin-1", payload["createdByUID"])
        assertEquals("Pastor Joao", payload["createdByName"])
        // always-written arrays present
        assertTrue(payload.containsKey("priceItems"))
        assertTrue(payload.containsKey("agePrices"))
        assertTrue(payload.containsKey("transportationOptions"))
        // never write guidelines or winnerRevealPolicy on the normal path
        assertFalse(payload.containsKey("guidelines"))
        assertFalse(payload.containsKey("winnerRevealPolicy"))
        assertFalse(payload.containsKey("isFeatured"))
        assertFalse(payload.containsKey("attendees"))
    }

    @Test
    fun priceItemAgePriceTransportEncodings() {
        val payload = CampingPayload.campingPayload(fullCamping(), TS, DEL, includeCreatedAt = false)

        @Suppress("UNCHECKED_CAST")
        val priceItem = (payload["priceItems"] as List<Map<String, Any?>>).first()
        assertEquals("EUR", priceItem["currency"]) // uppercase on the wire
        assertEquals(5000, priceItem["amountCents"])
        assertEquals(listOf("cardOneTime", "bankTransfer"), priceItem["paymentOptions"])
        assertFalse(priceItem.containsKey("iban")) // omit-when-nil

        @Suppress("UNCHECKED_CAST")
        val agePrice = (payload["agePrices"] as List<Map<String, Any?>>).first()
        assertFalse(agePrice.containsKey("maxAge")) // omit-when-nil (no upper bound)

        @Suppress("UNCHECKED_CAST")
        val transport = (payload["transportationOptions"] as List<Map<String, Any?>>).first()
        assertEquals("bus", transport["mode"])
        assertEquals(true, transport["requiresTicket"])
        assertFalse(transport.containsKey("capacity")) // omit-when-nil
    }

    @Test
    fun optionalScalarsUseDeleteWhenNil() {
        val bare = fullCamping().copy(
            locationLatitude = null,
            locationLongitude = null,
            participantCapacity = null,
            logoUrl = null,
            logoPublicId = null,
            registrationFeeCents = 0, // delete-when <= 0
            feeCurrency = "",
        )
        val payload = CampingPayload.campingPayload(bare, TS, DEL, includeCreatedAt = false)

        assertEquals(DEL, payload["locationLatitude"])
        assertEquals(DEL, payload["locationLongitude"])
        assertEquals(DEL, payload["participantCapacity"])
        assertEquals(DEL, payload["logoURL"])
        assertEquals(DEL, payload["logoPublicID"])
        assertEquals(DEL, payload["registrationFeeCents"])
        assertEquals(DEL, payload["feeCurrency"])
        assertFalse(payload.containsKey("createdByUID")) // not stamped on update
    }

    @Test
    fun organizerLevelValueIsTrimmedOnEncodeAndDecode() {
        val payload = CampingPayload.campingPayload(
            camping = fullCamping().copy(
                organizerLevel = OrganizerLevel(OrganizerType.Regional, "  South Region  "),
            ),
            serverTimestamp = TS,
            deleteField = DEL,
            includeCreatedAt = false,
        )

        assertEquals(
            mapOf("type" to "regional", "value" to "South Region"),
            payload["organizerLevel"],
        )

        val decoded = payload.toCampingOrNull("summer-2026")!!
        assertEquals(OrganizerLevel(OrganizerType.Regional, "South Region"), decoded.organizerLevel)
        assertTrue(decoded.organizerLevel.hasOrganizationName)
    }

    @Test
    fun registrationDeadlineWritesWhenSetAndDeletesWhenNil() {
        val deadline = Date(5_000_000)
        val withDeadline = CampingPayload.campingPayload(
            fullCamping().copy(registrationDeadline = deadline), TS, DEL, includeCreatedAt = false,
        )
        assertEquals(deadline, withDeadline["registrationDeadline"])

        val withoutDeadline = CampingPayload.campingPayload(
            fullCamping().copy(registrationDeadline = null), TS, DEL, includeCreatedAt = false,
        )
        assertEquals(DEL, withoutDeadline["registrationDeadline"]) // delete-when-nil

        // Round-trips back through the decoder.
        val realPayload = CampingPayload.campingPayload(
            fullCamping().copy(registrationDeadline = deadline), Date(1), DEL, includeCreatedAt = false,
        )
        assertEquals(deadline, realPayload.toCampingOrNull("summer-2026")!!.registrationDeadline)
    }

    @Test
    fun guidelinesCancelAndRevealAreSeparatePaths() {
        val guidelines = CampingPayload.guidelinesPayload("# Rules", TS)
        assertEquals("# Rules", guidelines["guidelines"])
        assertEquals(TS, guidelines["updatedAt"])

        val cancel = CampingPayload.cancelPayload(TS)
        assertEquals("cancelled", cancel["registrationStatus"])
        assertEquals(setOf("registrationStatus", "updatedAt"), cancel.keys)

        val featured = CampingPayload.featuredPayload(isFeatured = true, serverTimestamp = TS)
        assertEquals(true, featured["isFeatured"])
        assertEquals(TS, featured["updatedAt"])
        assertEquals(setOf("isFeatured", "updatedAt"), featured.keys)

        val publication = CampingPayload.publicationPayload(CampingPublicationStatus.Archived, TS)
        assertEquals("archived", publication["publicationStatus"])
        assertEquals(TS, publication["updatedAt"])
        assertEquals(setOf("publicationStatus", "updatedAt"), publication.keys)

        val reveal = CampingPayload.winnerRevealPolicyPayload(
            WinnerRevealPolicy(isRevealed = true, revealedBy = "admin-1"),
            TS,
        )
        @Suppress("UNCHECKED_CAST")
        val policy = reveal["winnerRevealPolicy"] as Map<String, Any?>
        assertEquals(true, policy["isRevealed"])
        assertEquals("admin-1", policy["revealedBy"])
    }

    @Test
    fun roundTripsThroughDecoder() {
        val original = fullCamping()
        val realTs = Date(9_000_000)
        val payload = CampingPayload.campingPayload(original, realTs, DEL, includeCreatedAt = true)
        val decoded = payload.toCampingOrNull(documentId = "summer-2026")!!

        assertEquals(original.id, decoded.id)
        assertEquals(original.title, decoded.title)
        assertEquals(original.startDate, decoded.startDate)
        assertEquals(original.endDate, decoded.endDate)
        assertEquals(original.organizerLevel, decoded.organizerLevel)
        assertEquals(CampingRegistrationStatus.Open, decoded.registrationStatus)
        assertEquals(CampingPublicationStatus.Published, decoded.publicationStatus)
        assertEquals(original.participantCapacity, decoded.participantCapacity)
        assertEquals("eur", decoded.feeCurrency)
        assertEquals(1, decoded.priceItems.size)
        assertEquals(CampingPaymentOption.CardOneTime, decoded.priceItems.first().paymentOptions.first())
        assertEquals(1, decoded.agePrices.size)
        assertEquals(TransportationMode.Bus, decoded.transportationOptions.first().mode)
        assertFalse(decoded.isFeatured)
        assertTrue(decoded.isPaid)
    }

    @Test
    fun decoderReadsFeaturedFlagWhenPresent() {
        val payload = CampingPayload.campingPayload(fullCamping(), Date(1), DEL, includeCreatedAt = false)
            .toMutableMap()
            .apply { put("isFeatured", true) }

        assertTrue(payload.toCampingOrNull("summer-2026")!!.isFeatured)
    }

    @Test
    fun decoderDefaultsMissingPublicationStatusToPublished() {
        val payload = CampingPayload.campingPayload(fullCamping(), Date(1), DEL, includeCreatedAt = false)
            .toMutableMap()
            .apply { remove("publicationStatus") }

        val decoded = payload.toCampingOrNull("summer-2026")!!
        assertEquals(CampingPublicationStatus.Published, decoded.publicationStatus)
        assertTrue(decoded.isPublished)
    }

    @Test
    fun draftCampingDoesNotAcceptRegistrationsEvenWhenOpen() {
        val draft = fullCamping().copy(publicationStatus = CampingPublicationStatus.Draft)

        assertFalse(draft.acceptsRegistrations)
    }

    @Test
    fun decoderDropsCampingMissingRequiredField() {
        val payload = CampingPayload.campingPayload(fullCamping(), Date(1), DEL, includeCreatedAt = false)
            .toMutableMap().apply { remove("organizerLevel") }
        assertEquals(null, payload.toCampingOrNull("summer-2026"))
    }

    private companion object {
        const val TS = "serverTimestamp"
        const val DEL = "delete"

        fun fullCamping() = Camping(
            id = "summer-2026",
            title = "Summer Camp",
            description = "A week of fun",
            startDate = Date(1_000_000),
            endDate = Date(2_000_000),
            organizerLevel = OrganizerLevel(OrganizerType.Church, "Paris Central SDA"),
            location = "Pine Valley",
            registrationStatus = CampingRegistrationStatus.Open,
            locationLatitude = 48.85,
            locationLongitude = 2.35,
            participantCapacity = 80,
            logoUrl = "https://cdn/logo.png",
            logoPublicId = "campzone/campings/summer-2026",
            registrationFeeCents = 12000,
            feeCurrency = "EUR",
            priceItems = listOf(
                CampingPriceItem(
                    id = "p1",
                    name = "Tuition",
                    details = "",
                    amountCents = 5000,
                    currency = "eur",
                    paymentOptions = listOf(CampingPaymentOption.CardOneTime, CampingPaymentOption.BankTransfer),
                    isMandatory = true,
                ),
            ),
            agePrices = listOf(CampingAgePrice(id = "a1", label = "Kids", minAge = 0, amountCents = 2500)),
            transportationOptions = listOf(
                CampingTransportationOption(
                    id = "t1",
                    name = "Bus A",
                    mode = TransportationMode.Bus,
                    details = "",
                    requiresTicket = true,
                ),
            ),
            createdByUid = "admin-1",
            createdByName = "Pastor Joao",
        )
    }
}
