package fr.ziyon.campzone.data.guardian

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.checkin.CheckInService
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CheckInRecord
import fr.ziyon.campzone.data.model.Program
import fr.ziyon.campzone.data.model.toCampingAttendeeOrNull
import fr.ziyon.campzone.data.schedule.ScheduleService
import fr.ziyon.campzone.data.teams.TeamService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Aggregates a guardian's read-only view of their children at camp from
 * collections they can already read: each child's check-in doc (fetched by id —
 * a blanket `checkIns` query is denied for a guardian by the rules) and their
 * registration doc, the camp's teams (signed-in readable), and the schedule
 * (public). It owns no query logic of its own beyond the per-doc reads — it
 * composes the existing CheckIn / Team / Schedule services. Mirrors the iOS
 * `GuardianUpdatesServicing`.
 */
interface GuardianUpdatesService {
    suspend fun loadUpdates(campingId: String, childAttendeeIds: List<String>): GuardianUpdatesData

    /** Live team scores (the most dynamic input) via the team listener; check-ins,
     *  registrations and the schedule are loaded once up front and re-merged on
     *  each team change. */
    fun observeUpdates(campingId: String, childAttendeeIds: List<String>): Flow<GuardianUpdatesData>
}

class FirestoreGuardianUpdatesService @Inject constructor(
    private val db: FirebaseFirestore,
    private val checkInService: CheckInService,
    private val teamService: TeamService,
    private val scheduleService: ScheduleService,
) : GuardianUpdatesService {

    override suspend fun loadUpdates(
        campingId: String,
        childAttendeeIds: List<String>,
    ): GuardianUpdatesData {
        val registrations = childRegistrations(campingId, childAttendeeIds)
        val checkIns = checkIns(campingId, childAttendeeIds)
        val teams = runCatching { teamService.loadTeams(campingId) }.getOrDefault(emptyList())
        val programs = programs(campingId)
        return GuardianUpdatesData(registrations, checkIns, teams, programs)
    }

    override fun observeUpdates(
        campingId: String,
        childAttendeeIds: List<String>,
    ): Flow<GuardianUpdatesData> = flow {
        val registrations = childRegistrations(campingId, childAttendeeIds)
        val checkIns = checkIns(campingId, childAttendeeIds)
        val programs = programs(campingId)
        emitAll(
            teamService.observeTeams(campingId).map { teams ->
                GuardianUpdatesData(registrations, checkIns, teams, programs)
            },
        )
    }

    private suspend fun childRegistrations(
        campingId: String,
        ids: List<String>,
    ): List<CampingAttendee> = ids.mapNotNull { id ->
        runCatching {
            db.collection("campings").document(campingId)
                .collection("registrations").document(id)
                .get().await()
        }.getOrNull()?.takeIf { it.exists() }?.data?.toCampingAttendeeOrNull(id)
    }

    private suspend fun checkIns(campingId: String, ids: List<String>): List<CheckInRecord> =
        ids.mapNotNull { id ->
            runCatching { checkInService.loadRecord(campingId, id) }.getOrNull()
        }

    private suspend fun programs(campingId: String): List<Program> =
        runCatching { scheduleService.loadSchedule(campingId).allPrograms }.getOrDefault(emptyList())
}

/** In-memory fake for previews and tests; mirrors the iOS `MockGuardianUpdatesService`. */
class FakeGuardianUpdatesService(
    private val data: GuardianUpdatesData = GuardianUpdatesData(),
    private var shouldFail: Boolean = false,
) : GuardianUpdatesService {

    override suspend fun loadUpdates(
        campingId: String,
        childAttendeeIds: List<String>,
    ): GuardianUpdatesData {
        if (shouldFail) throw IllegalStateException("The fake guardian-updates service was configured to fail.")
        return data
    }

    override fun observeUpdates(
        campingId: String,
        childAttendeeIds: List<String>,
    ): Flow<GuardianUpdatesData> = MutableStateFlow(data)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GuardianUpdatesBindings {
    @Binds
    @Singleton
    abstract fun bindGuardianUpdatesService(impl: FirestoreGuardianUpdatesService): GuardianUpdatesService
}
