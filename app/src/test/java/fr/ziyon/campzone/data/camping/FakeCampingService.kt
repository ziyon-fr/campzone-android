package fr.ziyon.campzone.data.camping

import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** In-memory [CampingService] for ViewModel tests and previews. */
class FakeCampingService(
    initial: List<Camping> = emptyList(),
    private val attendeesByCamping: Map<String, List<CampingAttendee>> = emptyMap(),
    var shouldFail: Boolean = false,
    private val attendeesFail: Boolean = false,
) : CampingService {

    private val campings = MutableStateFlow(initial)
    val saved = mutableListOf<Camping>()
    val deleted = mutableListOf<String>()

    override fun observeCampings(): Flow<List<Camping>> =
        if (shouldFail) {
            flow { throw RuntimeException("Stream failed") }
        } else {
            campings.map { list -> list.map(::withAttendees) }
        }

    override suspend fun fetchCamping(id: String): Camping =
        campings.value.firstOrNull { it.id == id }?.let(::withAttendees) ?: error("Camping not found")

    override suspend fun loadAttendees(campingId: String): List<CampingAttendee> {
        if (attendeesFail) throw RuntimeException("Attendees denied")
        return attendeesByCamping[campingId].orEmpty()
    }

    override suspend fun saveCamping(camping: Camping): Camping {
        saved += camping
        campings.value = campings.value.filterNot { it.id == camping.id } + camping
        return camping
    }

    override suspend fun cancelCamping(id: String): Camping {
        val updated = fetchCamping(id).copy(registrationStatus = CampingRegistrationStatus.Cancelled)
        campings.value = campings.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun deleteCamping(id: String) {
        deleted += id
        campings.value = campings.value.filterNot { it.id == id }
    }

    override suspend fun updateGuidelines(campingId: String, body: String): Camping {
        val updated = fetchCamping(campingId).copy(guidelines = body)
        campings.value = campings.value.map { if (it.id == campingId) updated else it }
        return updated
    }

    private fun withAttendees(camping: Camping): Camping =
        camping.copy(attendees = attendeesByCamping[camping.id] ?: camping.attendees)
}
