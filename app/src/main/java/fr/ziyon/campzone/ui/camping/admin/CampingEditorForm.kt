package fr.ziyon.campzone.ui.camping.admin

import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAgePrice
import fr.ziyon.campzone.data.model.CampingPriceItem
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CampingTransportationOption
import fr.ziyon.campzone.data.model.OrganizerLevel
import fr.ziyon.campzone.data.model.OrganizerType
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

data class CampingEditorForm(
    val title: String = "",
    val description: String = "",
    val startDate: Date = Date(),
    val endDate: Date = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, 2) }.time,
    val organizerType: OrganizerType = OrganizerType.Regional,
    val organizerName: String = "",
    val location: String = "",
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val registrationStatus: CampingRegistrationStatus = CampingRegistrationStatus.Open,
    val participantCapacityText: String = "",
    val logoUrl: String? = null,
    val logoPublicId: String? = null,
    val registrationFeeText: String = "",
    val feeCurrency: String = "EUR",
    val priceItems: List<CampingPriceItem> = emptyList(),
    val agePrices: List<CampingAgePrice> = emptyList(),
    val transportationOptions: List<CampingTransportationOption> = emptyList(),
    /** Optional registration deadline. `null` = no deadline (open until manually closed). */
    val registrationDueDate: Date? = null,
) {
    val organizerLevel: OrganizerLevel
        get() = OrganizerLevel(type = organizerType, value = organizerName.trim())

    companion object {
        fun from(camping: Camping) = CampingEditorForm(
            title = camping.title,
            description = camping.description,
            startDate = camping.startDate,
            endDate = camping.endDate,
            organizerType = camping.organizerLevel.type,
            organizerName = camping.organizerLevel.value,
            location = camping.location,
            locationLatitude = camping.locationLatitude,
            locationLongitude = camping.locationLongitude,
            registrationStatus = camping.registrationStatus,
            participantCapacityText = camping.participantCapacity?.toString() ?: "",
            logoUrl = camping.logoUrl,
            logoPublicId = camping.logoPublicId,
            registrationFeeText = camping.registrationFeeCents?.let { "%.2f".format(it / 100.0) } ?: "",
            feeCurrency = camping.feeCurrency?.trim()?.takeUnless { it.isBlank() }?.uppercase() ?: "EUR",
            priceItems = camping.priceItems,
            agePrices = camping.agePrices,
            transportationOptions = camping.transportationOptions,
            registrationDueDate = camping.registrationDeadline,
        )

        /** Parses a major-unit fee string (e.g. "25" or "25.50") → integer cents. */
        fun feeCents(text: String): Int? {
            val trimmed = text.trim().replace(",", ".")
            if (trimmed.isEmpty()) return null
            val amount = trimmed.toDoubleOrNull() ?: return null
            if (amount <= 0) return null
            return (amount * 100).roundToInt()
        }
    }
}
