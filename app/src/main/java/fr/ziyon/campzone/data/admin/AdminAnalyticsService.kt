package fr.ziyon.campzone.data.admin

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

data class AdminCampingAnalytics(
    val id: String,
    val title: String,
    val registrations: Int,
    val pending: Int,
    val approved: Int,
    val waitlisted: Int,
    val rejected: Int,
    val engagement: Int,
)

data class AdminAnalyticsTrendPoint(val label: String, val count: Int)

data class AdminAnalyticsDashboard(
    val generatedAt: Date,
    val totalUsers: Int,
    val completedProfiles: Int,
    val activeCampings: Int,
    val openCampings: Int,
    val campings: List<AdminCampingAnalytics>,
    val weeklyRegistrations: List<AdminAnalyticsTrendPoint>,
) {
    val totalRegistrations: Int get() = campings.sumOf { it.registrations }
    val totalEngagement: Int get() = campings.sumOf { it.engagement }
}

interface AdminAnalyticsService {
    suspend fun load(): AdminAnalyticsDashboard
}

@Singleton
class FirestoreAdminAnalyticsService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val campingService: CampingService,
) : AdminAnalyticsService {
    override suspend fun load(): AdminAnalyticsDashboard = coroutineScope {
        val now = Date()
        val usersTask = async { firestore.collection("users").get().await() }
        val campings = campingService.observeCampings().first()
        val eventDates = mutableListOf<Date>()
        val metrics = campings.map { camping ->
            async {
                val registrations = campingRef(camping).collection("registrations").get().await()
                val statuses = registrations.documents.map {
                    RegistrationApprovalStatus.fromWire(it.getString("registrationStatus"))
                }
                synchronized(eventDates) {
                    registrations.documents.mapNotNullTo(eventDates) { document ->
                        (document.get("createdAt") as? Timestamp)?.toDate() ?: document.getDate("createdAt")
                    }
                }
                val polls = campingRef(camping).collection("polls").get().await()
                val pollVotes = polls.documents.sumOf { document ->
                    @Suppress("UNCHECKED_CAST")
                    (document.get("options") as? List<Map<String, Any?>>).orEmpty()
                        .sumOf { (it["voteCount"] as? Number)?.toInt() ?: 0 }
                }
                val engagement = listOf("chat", "media", "checkIns").map { collection ->
                    async { campingRef(camping).collection(collection).get().await().size() }
                }.awaitAll().sum() + pollVotes + firestore.collection("announcements")
                    .whereEqualTo("campingID", camping.id).get().await().size()
                AdminCampingAnalytics(
                    id = camping.id,
                    title = camping.title,
                    registrations = statuses.size,
                    pending = statuses.count { it == RegistrationApprovalStatus.Pending },
                    approved = statuses.count { it == RegistrationApprovalStatus.Approved },
                    waitlisted = statuses.count { it == RegistrationApprovalStatus.Waitlisted },
                    rejected = statuses.count { it == RegistrationApprovalStatus.Rejected },
                    engagement = engagement,
                )
            }
        }.awaitAll()
        val users = usersTask.await()
        AdminAnalyticsDashboard(
            generatedAt = now,
            totalUsers = users.size(),
            completedProfiles = users.documents.count { it.getBoolean("onboardingCompleted") == true },
            activeCampings = campings.count { !it.endDate.before(now) },
            openCampings = campings.count { it.acceptsRegistrations },
            campings = metrics.sortedByDescending { it.registrations },
            weeklyRegistrations = weeklyTrend(eventDates, now),
        )
    }

    private fun campingRef(camping: Camping) = firestore.collection("campings").document(camping.id)

    private fun weeklyTrend(events: List<Date>, now: Date): List<AdminAnalyticsTrendPoint> {
        val calendar = Calendar.getInstance().apply {
            time = now
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentStart = calendar.time
        val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
        return (7 downTo 0).map { offset ->
            val start = Calendar.getInstance().apply { time = currentStart; add(Calendar.WEEK_OF_YEAR, -offset) }.time
            val end = Calendar.getInstance().apply { time = start; add(Calendar.WEEK_OF_YEAR, 1) }.time
            AdminAnalyticsTrendPoint(formatter.format(start), events.count { !it.before(start) && it.before(end) })
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdminAnalyticsBindings {
    @Binds abstract fun bindAdminAnalyticsService(implementation: FirestoreAdminAnalyticsService): AdminAnalyticsService
}
