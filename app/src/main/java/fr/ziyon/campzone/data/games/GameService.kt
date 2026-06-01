package fr.ziyon.campzone.data.games

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.ziyon.campzone.data.model.Activity
import fr.ziyon.campzone.data.model.ActivityPayload
import fr.ziyon.campzone.data.model.Game
import fr.ziyon.campzone.data.model.GamePayload
import fr.ziyon.campzone.data.model.toActivityOrNull
import fr.ziyon.campzone.data.model.toGameOrNull
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import javax.inject.Inject

interface GameService {
    /** Live games for a camping, ordered `createdAt` desc (mirrors [loadGames]). */
    fun observeGames(campingId: String): Flow<List<Game>>

    /** Live point-award activities for a camping, ordered `createdAt` desc (mirrors [loadActivities]). */
    fun observeActivities(campingId: String): Flow<List<Activity>>

    suspend fun loadGames(campingId: String): List<Game>
    suspend fun saveGame(game: Game): Game
    suspend fun deleteGame(id: String, campingId: String)
    suspend fun loadActivities(campingId: String): List<Activity>
    suspend fun loadActivitiesForGame(gameId: String, campingId: String): List<Activity>
    suspend fun recordActivity(activity: Activity): Activity
    suspend fun deleteActivities(ids: List<String>, campingId: String)
}

class FirestoreGameService @Inject constructor(
    private val db: FirebaseFirestore,
) : GameService {

    override fun observeGames(campingId: String): Flow<List<Game>> = callbackFlow {
        val registration = gamesCollection(campingId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val games = snapshot?.documents?.mapNotNull {
                    @Suppress("UNCHECKED_CAST")
                    (it.data as? Map<String, Any?>)?.toGameOrNull(it.id)
                }.orEmpty()
                trySend(games)
            }
        awaitClose { registration.remove() }
    }

    override fun observeActivities(campingId: String): Flow<List<Activity>> = callbackFlow {
        val registration = activitiesCollection(campingId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(500)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val activities = snapshot?.documents?.mapNotNull {
                    @Suppress("UNCHECKED_CAST")
                    (it.data as? Map<String, Any?>)?.toActivityOrNull(it.id)
                }.orEmpty()
                trySend(activities)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun loadGames(campingId: String): List<Game> {
        val snapshot = gamesCollection(campingId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()
        return snapshot.documents.mapNotNull {
            @Suppress("UNCHECKED_CAST")
            (it.data as? Map<String, Any?>)?.toGameOrNull(it.id)
        }
    }

    override suspend fun saveGame(game: Game): Game {
        val now = Date()
        val isNew = game.createdAt == null
        val payload = GamePayload.gamePayload(game, now, includeCreatedAt = isNew)
        gamesCollection(game.campingId).document(game.id).set(payload).await()
        return game.copy(updatedAt = now, createdAt = game.createdAt ?: now)
    }

    override suspend fun deleteGame(id: String, campingId: String) {
        gamesCollection(campingId).document(id).delete().await()
    }

    override suspend fun loadActivities(campingId: String): List<Activity> {
        val snapshot = activitiesCollection(campingId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(500)
            .get().await()
        return snapshot.documents.mapNotNull {
            @Suppress("UNCHECKED_CAST")
            (it.data as? Map<String, Any?>)?.toActivityOrNull(it.id)
        }
    }

    override suspend fun loadActivitiesForGame(gameId: String, campingId: String): List<Activity> {
        val snapshot = activitiesCollection(campingId)
            .whereEqualTo("gameID", gameId)
            .get().await()
        return snapshot.documents.mapNotNull {
            @Suppress("UNCHECKED_CAST")
            (it.data as? Map<String, Any?>)?.toActivityOrNull(it.id)
        }.sortedByDescending { it.createdAt }
    }

    override suspend fun recordActivity(activity: Activity): Activity {
        val payload = ActivityPayload.activityPayload(activity)
        activitiesCollection(activity.campingId).document(activity.id).set(payload).await()
        return activity
    }

    override suspend fun deleteActivities(ids: List<String>, campingId: String) {
        if (ids.isEmpty()) return
        for (chunk in ids.chunked(450)) {
            val batch = db.batch()
            chunk.forEach { id -> batch.delete(activitiesCollection(campingId).document(id)) }
            batch.commit().await()
        }
    }

    private fun gamesCollection(campingId: String) =
        db.collection("campings").document(campingId).collection("games")

    private fun activitiesCollection(campingId: String) =
        db.collection("campings").document(campingId).collection("activities")
}

class FakeGameService(
    games: List<Game> = emptyList(),
    activities: List<Activity> = emptyList(),
    var shouldFail: Boolean = false,
) : GameService {
    private val gamesByCampingId = MutableStateFlow(games.groupBy { it.campingId })
    private val activitiesByCampingId = MutableStateFlow(activities.groupBy { it.campingId })

    private fun check() { if (shouldFail) throw Exception("FakeGameService configured to fail.") }

    override fun observeGames(campingId: String): Flow<List<Game>> =
        if (shouldFail) {
            flow { throw Exception("FakeGameService configured to fail.") }
        } else {
            gamesByCampingId.map { map ->
                (map[campingId] ?: emptyList()).sortedByDescending { it.createdAt }
            }
        }

    override fun observeActivities(campingId: String): Flow<List<Activity>> =
        if (shouldFail) {
            flow { throw Exception("FakeGameService configured to fail.") }
        } else {
            activitiesByCampingId.map { map ->
                (map[campingId] ?: emptyList()).sortedByDescending { it.createdAt }
            }
        }

    override suspend fun loadGames(campingId: String): List<Game> {
        check()
        return (gamesByCampingId.value[campingId] ?: emptyList()).sortedByDescending { it.createdAt }
    }

    override suspend fun saveGame(game: Game): Game {
        check()
        val now = Date()
        val updated = game.copy(updatedAt = now, createdAt = game.createdAt ?: now)
        gamesByCampingId.update { map ->
            val list = (map[game.campingId] ?: emptyList()).filterNot { it.id == game.id } + updated
            map + (game.campingId to list)
        }
        return updated
    }

    override suspend fun deleteGame(id: String, campingId: String) {
        check()
        gamesByCampingId.update { map ->
            map + (campingId to (map[campingId] ?: emptyList()).filterNot { it.id == id })
        }
    }

    override suspend fun loadActivities(campingId: String): List<Activity> {
        check()
        return (activitiesByCampingId.value[campingId] ?: emptyList()).sortedByDescending { it.createdAt }
    }

    override suspend fun loadActivitiesForGame(gameId: String, campingId: String): List<Activity> {
        check()
        return (activitiesByCampingId.value[campingId] ?: emptyList())
            .filter { it.gameId == gameId }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun recordActivity(activity: Activity): Activity {
        check()
        activitiesByCampingId.update { map ->
            map + (activity.campingId to (listOf(activity) + (map[activity.campingId] ?: emptyList())))
        }
        return activity
    }

    override suspend fun deleteActivities(ids: List<String>, campingId: String) {
        check()
        val idSet = ids.toSet()
        activitiesByCampingId.update { map ->
            map + (campingId to (map[campingId] ?: emptyList()).filterNot { it.id in idSet })
        }
    }
}

fun previewGame(campingId: String = "preview-camp") = Game(
    id = "game-1",
    campingId = campingId,
    name = "Bible Knowledge",
    rules = "Teams answer questions from the Sabbath School quarterly.",
    pointRules = listOf(
        fr.ziyon.campzone.data.model.PointRule(
            id = UUID.randomUUID().toString(),
            name = "Correct answer",
            points = 10,
            reason = "Answered a Bible question correctly",
            appliesTo = fr.ziyon.campzone.data.model.PointRuleTarget.Team,
            visibility = fr.ziyon.campzone.data.model.PointRuleVisibility.Immediate,
        ),
        fr.ziyon.campzone.data.model.PointRule(
            id = UUID.randomUUID().toString(),
            name = "Bonus round",
            points = 25,
            reason = "Answered a bonus question",
            appliesTo = fr.ziyon.campzone.data.model.PointRuleTarget.Team,
            visibility = fr.ziyon.campzone.data.model.PointRuleVisibility.Immediate,
        ),
    ),
    createdBy = "preview-uid",
    createdAt = Date(),
    updatedAt = Date(),
)
