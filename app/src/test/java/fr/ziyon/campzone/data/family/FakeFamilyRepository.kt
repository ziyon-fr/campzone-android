package fr.ziyon.campzone.data.family

import fr.ziyon.campzone.data.auth.UserGender
import java.util.Date

/** In-memory [FamilyRepository] for ViewModel tests. */
class FakeFamilyRepository(
    childrenByGuardian: Map<String, List<ChildParticipant>> = emptyMap(),
    var shouldFail: Boolean = false,
    var crossGuardianMatch: FamilyParticipantDuplicateMatch? = null,
) : FamilyRepository {
    val store: MutableMap<String, MutableList<ChildParticipant>> =
        childrenByGuardian.mapValues { it.value.toMutableList() }.toMutableMap()

    override suspend fun loadChildren(userId: String): List<ChildParticipant> {
        throwIfNeeded()
        return store[userId].orEmpty().sortedBy { it.displayName.lowercase() }
    }

    override suspend fun saveChild(child: ChildParticipant, userId: String): ChildParticipant {
        throwIfNeeded()
        val saved = child.copy(guardianId = userId)
        val list = store.getOrPut(userId) { mutableListOf() }
        val index = list.indexOfFirst { it.id == saved.id }
        if (index >= 0) list[index] = saved else list.add(saved)
        return saved
    }

    override suspend fun deleteChild(id: String, userId: String) {
        throwIfNeeded()
        store[userId]?.removeAll { it.id == id }
    }

    override suspend fun findSimilarParticipant(
        displayName: String,
        age: Int,
        excludingGuardianId: String,
    ): FamilyParticipantDuplicateMatch? {
        throwIfNeeded()
        return crossGuardianMatch
    }

    private fun throwIfNeeded() {
        if (shouldFail) error("The fake family repository was configured to fail.")
    }
}

fun sampleChild(
    id: String = "child-1",
    guardianId: String = "guardian-1",
    displayName: String = "Ana Santos",
    age: Int = 10,
): ChildParticipant = ChildParticipant(
    id = id,
    guardianId = guardianId,
    displayName = displayName,
    age = age,
    gender = UserGender.Female,
    church = "Paris Central SDA",
    preferredLanguage = "fr",
    emergencyContactName = "Maria Santos",
    emergencyContactPhone = "+33 1 00 00 00 00",
    relationship = FamilyRelationship.Parent,
    guardianConsentAt = Date(),
)
