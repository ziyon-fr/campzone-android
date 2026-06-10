package fr.ziyon.campzone.data.dashboard

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.toCampingOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

interface MainDashboardRepository {
    fun observeFeaturedCamping(registeredCampingIds: Set<String>): Flow<Camping?>
    suspend fun loadFeaturedCamping(registeredCampingIds: Set<String>): Camping?
}

@Singleton
class FirebaseMainDashboardRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : MainDashboardRepository {

    override fun observeFeaturedCamping(registeredCampingIds: Set<String>): Flow<Camping?> = callbackFlow {
        val registration = featuredQuery(Date())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val campings = snapshot?.documents
                    ?.mapNotNull { document -> document.data?.toCampingOrNull(document.id) }
                    .orEmpty()
                trySend(selectFeaturedCamping(campings, registeredCampingIds))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun loadFeaturedCamping(registeredCampingIds: Set<String>): Camping? {
        val snapshot = featuredQuery(Date()).get().await()
        val campings = snapshot.documents
            .mapNotNull { document -> document.data?.toCampingOrNull(document.id) }
        return selectFeaturedCamping(campings, registeredCampingIds)
    }

    private fun featuredQuery(now: Date) = firestore
        .collection(Campings)
        .whereGreaterThanOrEqualTo(EndDate, now)
        .orderBy(EndDate)
        .limit(HomeCandidateLimit)

    private companion object {
        const val Campings = "campings"
        const val EndDate = "endDate"
        const val HomeCandidateLimit = 12L
    }
}

internal fun selectFeaturedCamping(
    campingsInEndDateOrder: List<Camping>,
    registeredCampingIds: Set<String>,
    now: Date = Date(),
): Camping? {
    var activeForUser: Camping? = null
    var adminPinned: Camping? = null
    var openSoonest: Camping? = null
    var dateFallback: Camping? = null

    for (camping in campingsInEndDateOrder) {
        if (camping.endDate.before(now) || camping.registrationStatus == CampingRegistrationStatus.Cancelled) {
            continue
        }

        if (activeForUser == null &&
            !camping.startDate.after(now) &&
            !camping.endDate.before(now) &&
            registeredCampingIds.contains(camping.id)
        ) {
            activeForUser = camping
        }

        if (adminPinned == null && camping.isFeatured) {
            adminPinned = camping
        }

        if (camping.effectiveRegistrationStatus == CampingRegistrationStatus.Open) {
            val existing = openSoonest
            if (existing == null || camping.startDate.before(existing.startDate)) {
                openSoonest = camping
            }
        }

        if (dateFallback == null) {
            dateFallback = camping
        }
    }

    return activeForUser ?: adminPinned ?: openSoonest ?: dateFallback
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MainDashboardBindings {
    @Binds
    abstract fun bindMainDashboardRepository(
        repository: FirebaseMainDashboardRepository,
    ): MainDashboardRepository
}

class PreviewMainDashboardRepository(
    private val featuredCamping: Camping? = null,
) : MainDashboardRepository {
    override fun observeFeaturedCamping(registeredCampingIds: Set<String>): Flow<Camping?> =
        flowOf(featuredCamping)

    override suspend fun loadFeaturedCamping(registeredCampingIds: Set<String>): Camping? =
        featuredCamping
}
