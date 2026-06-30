package fr.ziyon.campzone.data.model

import java.util.Date

/**
 * `campings/{campingId}` (`02-firestore-schema.md` §3). Doc ID is the
 * client-supplied [id]. The camping doc is written via a hand-built payload
 * ([CampingPayload]) - never an auto-encoder - and never carries `attendees`
 * (those live in the `registrations` subcollection). `guidelines` and
 * `winnerRevealPolicy` are written only by their dedicated paths; `isFeatured`
 * is likewise written only by the dedicated Home pin path.
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
    val publicationStatus: CampingPublicationStatus = CampingPublicationStatus.Published,
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
    /**
     * Optional registration deadline. When set, registration closes
     * automatically once this moment passes; an admin or the camp creator
     * "extends" the window by editing this date (or clearing it). `null` means
     * no deadline (open until manually closed). Stored as a `delete-when-nil`
     * timestamp on the wire (`02-firestore-schema.md` §3).
     */
    val registrationDeadline: Date? = null,
    /**
     * Admin-selected Home pin. Missing legacy docs decode as false and regular
     * camping saves never write it, so edits cannot accidentally clear the pin.
     */
    val isFeatured: Boolean = false,
    val attendees: List<CampingAttendee> = emptyList(),
) {
    val isPaid: Boolean
        get() = (registrationFeeCents ?: 0) > 0 ||
            priceItems.any { it.amountCents > 0 } ||
            agePrices.any { it.amountCents > 0 }

    val usesTransportationOptions: Boolean
        get() = transportationOptions.isNotEmpty()

    val hasRegistrationDeadline: Boolean
        get() = registrationDeadline != null

    /** True once a set deadline has elapsed (`now >= deadline`). */
    val isRegistrationDeadlinePassed: Boolean
        get() = registrationDeadline?.let { !Date().before(it) } ?: false

    /**
     * Registration status as it should be presented and gated. An `Open` camp
     * whose deadline has passed reads as `Closed` (auto-close) without mutating
     * the stored field; `Closed`/`Cancelled` pass through unchanged.
     */
    val effectiveRegistrationStatus: CampingRegistrationStatus
        get() = if (registrationStatus == CampingRegistrationStatus.Open && isRegistrationDeadlinePassed) {
            CampingRegistrationStatus.Closed
        } else {
            registrationStatus
        }

    val acceptsRegistrations: Boolean
        get() = isPublished && effectiveRegistrationStatus == CampingRegistrationStatus.Open

    val isDraft: Boolean
        get() = publicationStatus == CampingPublicationStatus.Draft

    val isPublished: Boolean
        get() = publicationStatus == CampingPublicationStatus.Published

    val isArchived: Boolean
        get() = publicationStatus == CampingPublicationStatus.Archived

    val isPubliclyVisible: Boolean
        get() = isPublished

    val approvedAttendees: List<CampingAttendee>
        get() = attendees.filter { it.registrationStatus == RegistrationApprovalStatus.Approved }

    val pendingAttendees: List<CampingAttendee>
        get() = attendees.filter { it.registrationStatus == RegistrationApprovalStatus.Pending }

    val waitlistedAttendees: List<CampingAttendee>
        get() = attendees.filter { it.registrationStatus == RegistrationApprovalStatus.Waitlisted }

    val isAtCapacity: Boolean
        get() = participantCapacity?.let { approvedAttendees.size >= it } ?: false

    val participantCount: Int
        get() = approvedAttendees.size

    /** Lowest matching age band, else null (caller falls back to [registrationFeeCents]). */
    fun agePriceFor(age: Int): CampingAgePrice? =
        agePrices
            .filter { age >= it.minAge && (it.maxAge == null || age <= it.maxAge) }
            .minByOrNull { it.minAge }

    fun resolvedRegistrationFeeCents(age: Int?): Int =
        (age?.let { agePriceFor(it)?.amountCents } ?: registrationFeeCents ?: 0).coerceAtLeast(0)

    fun requiresRegistrationPayment(participant: RegistrationParticipant): Boolean =
        resolvedRegistrationFeeCents(participant.age) > 0

    fun requiresRegistrationPayment(participants: List<RegistrationParticipant>): Boolean =
        participants.any(::requiresRegistrationPayment)

    fun transportationOption(id: String?): CampingTransportationOption? =
        id?.let { optionId -> transportationOptions.firstOrNull { it.id == optionId } }

    fun registrationsForAuthenticatedUser(userId: String?): List<CampingAttendee> {
        val uid = userId?.takeUnless { it.isBlank() } ?: return emptyList()
        return attendees.filter { attendee ->
            attendee.userId == uid ||
                attendee.guardianId == uid ||
                (attendee.participantKind == RegistrationParticipantKind.SelfParticipant && attendee.id == uid)
        }
    }

    /**
     * The registration document Security Rules can join directly at
     * `/registrations/{auth.uid}`. A guardian-only child registration does not
     * authorize broad activity or notification collection queries.
     */
    fun directRegistrationForAuthenticatedUser(userId: String?): CampingAttendee? {
        val uid = userId?.takeUnless { it.isBlank() } ?: return null
        return attendees.firstOrNull {
            it.userId == uid && it.participantKind == RegistrationParticipantKind.SelfParticipant
        } ?: attendees.firstOrNull { it.id == uid }
    }

    fun hasApprovedRegistrationForUser(userId: String?): Boolean =
        registrationsForAuthenticatedUser(userId)
            .any { it.registrationStatus == RegistrationApprovalStatus.Approved }
}

data class OrganizerLevel(
    val type: OrganizerType,
    val value: String,
)

data class WinnerRevealPolicy(
    val isRevealed: Boolean = false,
    val hideDate: Date? = null,
    val revealDate: Date? = null,
    val revealedBy: String? = null,
    val revealedByName: String? = null,
    val revealedAt: Date? = null,
) {
    /** Effective hide moment, falling back to `campingEnd - 24h` when not set. */
    fun effectiveHideDate(campingEnd: Date): Date =
        hideDate ?: Date(campingEnd.time - 24L * 60 * 60 * 1000)

    /** True once the final reveal has fired, manually or via a scheduled date. */
    fun hasRevealFired(now: Date = Date()): Boolean =
        isRevealed || (revealDate?.let { now >= it } ?: false)

    /**
     * True when scores should be hidden from non-managing participants. Mirrors
     * iOS `WinnerRevealPolicy.areScoresHidden`: the default policy still hides
     * scores in the 24h before the camp ends even when none was saved.
     */
    fun areScoresHidden(campingEnd: Date, now: Date = Date()): Boolean =
        if (hasRevealFired(now)) false else now >= effectiveHideDate(campingEnd)
}

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
) {
    val resolvedName: String
        get() = name.trim().takeUnless { it.isBlank() } ?: mode.displayName

    val issuesTicket: Boolean
        get() = requiresTicket || mode.defaultRequiresTicket

    /** A seat carries an in-app fare when a positive `feeCents` is set. */
    val hasFee: Boolean
        get() = (feeCents ?: 0) > 0
}

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
        publicationStatus = CampingPublicationStatus.fromWire(stringValue("publicationStatus")),
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
        registrationDeadline = dateValue("registrationDeadline"),
        isFeatured = boolValue("isFeatured") ?: false,
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
