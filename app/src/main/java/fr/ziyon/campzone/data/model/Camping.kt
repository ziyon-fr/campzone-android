package fr.ziyon.campzone.data.model

import java.util.Date

/**
 * `campings/{campingId}` (`02-firestore-schema.md` §3). Doc ID is the
 * client-supplied [id]. The camping doc is written via a hand-built payload
 * ([CampingPayload]) — never an auto-encoder — and never carries `attendees`
 * (those live in the `registrations` subcollection). `guidelines` and
 * `winnerRevealPolicy` are written only by their dedicated paths.
 */
data class Camping(
    val id: String,
    val title: String,
    val description: String,
    val startDate: Date,
    val endDate: Date,
    val organizerLevel: OrganizerLevel,
    val location: String,
    val registrationStatus: CampingRegistrationStatus,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val participantCapacity: Int? = null,
    val winnerRevealPolicy: WinnerRevealPolicy? = null,
    val logoUrl: String? = null,
    val logoPublicId: String? = null,
    val guidelines: String = "",
    val registrationFeeCents: Int? = null,
    val feeCurrency: String? = null,
    val priceItems: List<CampingPriceItem> = emptyList(),
    val agePrices: List<CampingAgePrice> = emptyList(),
    val transportationOptions: List<CampingTransportationOption> = emptyList(),
    val createdByUid: String? = null,
    val createdByName: String? = null,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val isPaid: Boolean
        get() = (registrationFeeCents ?: 0) > 0 ||
            priceItems.any { it.amountCents > 0 } ||
            agePrices.any { it.amountCents > 0 }

    val acceptsRegistrations: Boolean
        get() = registrationStatus == CampingRegistrationStatus.Open

    /** Lowest matching age band, else null (caller falls back to [registrationFeeCents]). */
    fun agePriceFor(age: Int): CampingAgePrice? =
        agePrices
            .filter { age >= it.minAge && (it.maxAge == null || age <= it.maxAge) }
            .minByOrNull { it.minAge }
}

data class OrganizerLevel(
    val type: OrganizerType,
    val value: String,
)

data class WinnerRevealPolicy(
    val isRevealed: Boolean,
    val hideDate: Date? = null,
    val revealDate: Date? = null,
    val revealedBy: String? = null,
    val revealedByName: String? = null,
    val revealedAt: Date? = null,
)

data class CampingPriceItem(
    val id: String,
    val name: String,
    val details: String,
    val amountCents: Int,
    val currency: String,
    val paymentOptions: List<CampingPaymentOption> = emptyList(),
    val iban: String? = null,
    val ibanHolder: String? = null,
    val isMandatory: Boolean = false,
)

data class CampingAgePrice(
    val id: String,
    val label: String,
    val minAge: Int,
    val amountCents: Int,
    val maxAge: Int? = null,
)

data class CampingTransportationOption(
    val id: String,
    val name: String,
    val mode: TransportationMode,
    val details: String,
    val requiresTicket: Boolean = false,
    val capacity: Int? = null,
    val feeCents: Int? = null,
    val currency: String = "EUR",
)

// region decode

internal fun Map<String, Any?>.toCampingOrNull(documentId: String): Camping? {
    val title = stringValue("title") ?: return null
    val description = rawStringValue("description") ?: return null
    val startDate = dateValue("startDate") ?: return null
    val endDate = dateValue("endDate") ?: return null
    val organizerLevel = mapValue("organizerLevel")?.toOrganizerLevelOrNull() ?: return null
    val location = stringValue("location") ?: return null
    val registrationStatus = stringValue("registrationStatus") ?: return null

    val fee = intValue("registrationFeeCents")?.takeIf { it > 0 }
    return Camping(
        id = stringValue("id") ?: documentId,
        title = title,
        description = description,
        startDate = startDate,
        endDate = endDate,
        organizerLevel = organizerLevel,
        location = location,
        registrationStatus = CampingRegistrationStatus.fromWire(registrationStatus),
        locationLatitude = doubleValue("locationLatitude"),
        locationLongitude = doubleValue("locationLongitude"),
        participantCapacity = intValue("participantCapacity"),
        winnerRevealPolicy = mapValue("winnerRevealPolicy")?.toWinnerRevealPolicy(),
        logoUrl = stringValue("logoURL"),
        logoPublicId = stringValue("logoPublicID"),
        guidelines = rawStringValue("guidelines").orEmpty(),
        registrationFeeCents = fee,
        feeCurrency = stringValue("feeCurrency"),
        priceItems = mapListValue("priceItems").mapNotNull { it.toCampingPriceItemOrNull() },
        agePrices = mapListValue("agePrices").mapNotNull { it.toCampingAgePriceOrNull() },
        transportationOptions = mapListValue("transportationOptions")
            .mapNotNull { it.toCampingTransportationOptionOrNull() },
        createdByUid = stringValue("createdByUID"),
        createdByName = stringValue("createdByName"),
        createdAt = dateValue("createdAt"),
        updatedAt = dateValue("updatedAt"),
    )
}

internal fun Map<String, Any?>.toOrganizerLevelOrNull(): OrganizerLevel? {
    val type = stringValue("type") ?: return null
    val value = stringValue("value") ?: return null
    return OrganizerLevel(type = OrganizerType.fromWire(type), value = value)
}

internal fun Map<String, Any?>.toWinnerRevealPolicy(): WinnerRevealPolicy =
    WinnerRevealPolicy(
        isRevealed = boolValue("isRevealed") ?: false,
        hideDate = dateValue("hideDate"),
        revealDate = dateValue("revealDate"),
        revealedBy = stringValue("revealedBy"),
        revealedByName = stringValue("revealedByName"),
        revealedAt = dateValue("revealedAt"),
    )

internal fun Map<String, Any?>.toCampingPriceItemOrNull(): CampingPriceItem? {
    val id = stringValue("id") ?: return null
    return CampingPriceItem(
        id = id,
        name = rawStringValue("name").orEmpty(),
        details = rawStringValue("details").orEmpty(),
        amountCents = intValue("amountCents") ?: 0,
        currency = stringValue("currency") ?: "EUR",
        paymentOptions = rawStringListValue("paymentOptions").mapNotNull(CampingPaymentOption::fromWire),
        iban = stringValue("iban"),
        ibanHolder = stringValue("ibanHolder"),
        isMandatory = boolValue("isMandatory") ?: false,
    )
}

internal fun Map<String, Any?>.toCampingAgePriceOrNull(): CampingAgePrice? {
    val id = stringValue("id") ?: return null
    return CampingAgePrice(
        id = id,
        label = rawStringValue("label").orEmpty(),
        minAge = intValue("minAge") ?: 0,
        amountCents = intValue("amountCents") ?: 0,
        maxAge = intValue("maxAge"),
    )
}

internal fun Map<String, Any?>.toCampingTransportationOptionOrNull(): CampingTransportationOption? {
    val id = stringValue("id") ?: return null
    return CampingTransportationOption(
        id = id,
        name = rawStringValue("name").orEmpty(),
        mode = TransportationMode.fromWire(stringValue("mode")),
        details = rawStringValue("details").orEmpty(),
        requiresTicket = boolValue("requiresTicket") ?: false,
        capacity = intValue("capacity"),
        feeCents = intValue("feeCents"),
        currency = stringValue("currency") ?: "EUR",
    )
}

// endregion
