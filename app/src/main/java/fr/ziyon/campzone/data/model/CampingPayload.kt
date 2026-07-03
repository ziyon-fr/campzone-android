package fr.ziyon.campzone.data.model

/**
 * Hand-built Firestore payloads for `campings/{id}` (`02-firestore-schema.md`
 * §3, §6 data-contract rules). `serverTimestamp`/`deleteField` are opaque tokens
 * so the shape is unit-testable without Firebase. The main [campingPayload]
 * never writes `guidelines`, `winnerRevealPolicy`, or `isFeatured` - those have
 * dedicated paths ([guidelinesPayload], [winnerRevealPolicyPayload],
 * [featuredPayload]).
 */
internal object CampingPayload {

    fun campingPayload(
        camping: Camping,
        serverTimestamp: Any,
        deleteField: Any,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "id" to camping.id,
            "title" to camping.title.trim(),
            "description" to camping.description.trim(),
            "startDate" to camping.startDate,
            "endDate" to camping.endDate,
            "organizerLevel" to organizerLevelMap(camping.organizerLevel),
            "location" to camping.location.trim(),
            "registrationStatus" to camping.registrationStatus.wireValue,
            "publicationStatus" to camping.publicationStatus.wireValue,
            "priceItems" to camping.priceItems.map(::priceItemMap),
            "agePrices" to camping.agePrices.map(::agePriceMap),
            "transportationOptions" to camping.transportationOptions.map(::transportationOptionMap),
            "updatedAt" to serverTimestamp,
        )

        payload["locationLatitude"] = camping.locationLatitude ?: deleteField
        payload["locationLongitude"] = camping.locationLongitude ?: deleteField
        payload["participantCapacity"] = camping.participantCapacity ?: deleteField

        val fee = camping.registrationFeeCents?.takeIf { it > 0 }
        payload["registrationFeeCents"] = fee ?: deleteField
        payload["feeCurrency"] = camping.feeCurrency?.trim()?.takeUnless { it.isBlank() }?.lowercase() ?: deleteField

        payload["logoURL"] = camping.logoUrl?.trim()?.takeUnless { it.isBlank() } ?: deleteField
        payload["logoPublicID"] = camping.logoPublicId?.trim()?.takeUnless { it.isBlank() } ?: deleteField

        // Optional registration deadline (delete-when-nil): clearing the toggle
        // removes the field so the camp reverts to its manual `registrationStatus`.
        payload["registrationDeadline"] = camping.registrationDeadline ?: deleteField

        if (includeCreatedAt) {
            payload["createdAt"] = serverTimestamp
            camping.createdByUid?.trim()?.takeUnless { it.isBlank() }?.let { payload["createdByUID"] = it }
            camping.createdByName?.trim()?.takeUnless { it.isBlank() }?.let { payload["createdByName"] = it }
        }

        return payload
    }

    fun organizerLevelMap(level: OrganizerLevel): Map<String, Any?> =
        linkedMapOf(
            "type" to level.type.wireValue,
            "value" to level.normalizedValue,
        )

    fun priceItemMap(item: CampingPriceItem): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "id" to item.id,
            "name" to item.name.trim(),
            "details" to item.details,
            "amountCents" to item.amountCents.coerceAtLeast(0),
            "currency" to item.currency.trim().uppercase(),
            "paymentOptions" to item.paymentOptions.map { it.wireValue },
            "isMandatory" to item.isMandatory,
        )
        item.iban?.trim()?.takeUnless { it.isBlank() }?.let { map["iban"] = it }
        item.ibanHolder?.trim()?.takeUnless { it.isBlank() }?.let { map["ibanHolder"] = it }
        return map
    }

    fun agePriceMap(price: CampingAgePrice): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "id" to price.id,
            "label" to price.label,
            "minAge" to price.minAge.coerceAtLeast(0),
            "amountCents" to price.amountCents,
        )
        price.maxAge?.let { map["maxAge"] = it }
        return map
    }

    fun transportationOptionMap(option: CampingTransportationOption): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "id" to option.id,
            "name" to option.name.trim(),
            "mode" to option.mode.wireValue,
            "details" to option.details,
            "requiresTicket" to option.requiresTicket,
            "currency" to option.currency.trim().uppercase().ifBlank { "EUR" },
        )
        option.capacity?.let { map["capacity"] = it }
        option.feeCents?.let { map["feeCents"] = it }
        return map
    }

    /** Guidelines update path (`updateData(["guidelines": ...])`) - gate `canEditGuidelines`. */
    fun guidelinesPayload(
        guidelines: String,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "guidelines" to guidelines,
            "updatedAt" to serverTimestamp,
        )

    /** Cancel path - writes only `{ registrationStatus: "cancelled", updatedAt }`. */
    fun cancelPayload(serverTimestamp: Any): Map<String, Any?> =
        linkedMapOf(
            "registrationStatus" to CampingRegistrationStatus.Cancelled.wireValue,
            "updatedAt" to serverTimestamp,
        )

    /** Home pin path - writes only `{ isFeatured, updatedAt }` as a merge. */
    fun featuredPayload(
        isFeatured: Boolean,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "isFeatured" to isFeatured,
            "updatedAt" to serverTimestamp,
        )

    /** Publication path - writes only `{ publicationStatus, updatedAt }`. */
    fun publicationPayload(
        status: CampingPublicationStatus,
        serverTimestamp: Any,
    ): Map<String, Any?> =
        linkedMapOf(
            "publicationStatus" to status.wireValue,
            "updatedAt" to serverTimestamp,
        )

    /** Winner-reveal path - gated by `canRevealWinners`; forbidden in normal edit. */
    fun winnerRevealPolicyPayload(
        policy: WinnerRevealPolicy,
        serverTimestamp: Any,
    ): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>("isRevealed" to policy.isRevealed)
        policy.hideDate?.let { map["hideDate"] = it }
        policy.revealDate?.let { map["revealDate"] = it }
        policy.revealedBy?.trim()?.takeUnless { it.isBlank() }?.let { map["revealedBy"] = it }
        policy.revealedByName?.trim()?.takeUnless { it.isBlank() }?.let { map["revealedByName"] = it }
        policy.revealedAt?.let { map["revealedAt"] = it }
        return linkedMapOf(
            "winnerRevealPolicy" to map,
            "updatedAt" to serverTimestamp,
        )
    }
}
