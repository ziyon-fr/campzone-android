package fr.ziyon.campzone.data.safety

import java.util.Date
import java.util.UUID

data class EmergencyContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val role: String = "",
    val phoneNumber: String = "",
    val note: String = "",
    val isPrimary: Boolean = false,
    val isEmergencyService: Boolean = false,
) {
    val dialablePhoneNumber: String
        get() = phoneNumber.filter { it.isDigit() || it == '+' }

    val subtitle: String
        get() = listOf(role.trim(), note.trim()).filter(String::isNotBlank).joinToString(" · ")

    fun normalized() = copy(
        id = id.ifBlank { UUID.randomUUID().toString() },
        name = name.trim(),
        role = role.trim(),
        phoneNumber = phoneNumber.trim(),
        note = note.trim(),
    )
}

data class EmergencySafetyHub(
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val emergencyInstructions: String = "",
    val firstAidInfo: String = "",
    val updatedAt: Date? = null,
) {
    fun normalized(): EmergencySafetyHub {
        val contacts = emergencyContacts.map(EmergencyContact::normalized)
            .filter { it.name.isNotBlank() || it.phoneNumber.isNotBlank() || it.role.isNotBlank() }
            .toMutableList()
        if (contacts.none { it.isPrimary } && contacts.isNotEmpty()) {
            contacts[0] = contacts[0].copy(isPrimary = true)
        }
        return copy(
            emergencyContacts = contacts,
            emergencyInstructions = emergencyInstructions.trim(),
            firstAidInfo = firstAidInfo.trim(),
        )
    }

    companion object {
        fun fallback(campingLocation: String) = EmergencySafetyHub(
            emergencyContacts = listOf(
                EmergencyContact(
                    name = "Emergency services",
                    role = "Local SOS",
                    phoneNumber = "112",
                    note = "Use for life-threatening emergencies",
                    isPrimary = true,
                    isEmergencyService = true,
                ),
            ),
            emergencyInstructions = buildString {
                append("Call emergency services first for immediate danger, serious injury, fire, or a missing participant. ")
                append("Keep the affected person supervised and notify camp leadership. ")
                append(if (campingLocation.isBlank()) "Send someone to meet responders at the main entrance." else "Send someone to meet responders at $campingLocation.")
            },
            firstAidInfo = "Go to the first-aid point or contact a leader for injuries, illness, medication concerns, or anything that feels unsafe. For urgent symptoms, call emergency services.",
        )
    }
}
