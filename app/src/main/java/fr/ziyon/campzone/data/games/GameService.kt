package fr.ziyon.campzone.data.games

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.ziyon.campzone.data.model.Activity
import fr.ziyon.campzone.data.model.ActivityPayload
import fr.ziyon.campzone.data.model.Game
import fr.ziyon.campzone.data.model.GamePayload
import fr.ziyon.campzone.data.model.toActivityOrNull
import fr.ziyon.campzone.data.model.toGameOrNull
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import javax.inject.Inject

interface GameService {
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
    private val gamesByCampingId = games.groupBy { it.campingId }
        .mapValues { it.value.toMutableList() }.toMutableMap()
    private val activitiesByCampingId = activities.groupBy { it.campingId }
        .mapValues { it.value.toMutableList() }.toMutableMap()

    private fun check() { if (shouldFail) throw Exception("FakeGameService configured to fail.") }

    override suspend fun loadGames(campingId: String): List<Game> {
        check()
        return (gamesByCampingId[campingId] ?: emptyList()).sortedByDescending { it.createdAt }
    }

    override suspend fun saveGame(game: Game): Game {
        check()
        val now = Date()
        val updated = game.copy(updatedAt = now, createdAt = game.createdAt ?: now)
        val list = gamesByCampingId.getOrPut(game.campingId) { mutableListOf() }
        val idx = list.indexOfFirst { it.id == game.id }
        if (idx >= 0) list[idx] = updated else list.add(updated)
        return updated
    }

    override suspend fun deleteGame(id: String, campingId: String) {
        check()
        gamesByCampingId[campingId]?.removeAll { it.id == id }
    }

    override suspend fun loadActivities(campingId: String): List<Activity> {
        check()
        return (activitiesByCampingId[campingId] ?: emptyList()).sortedByDescending { it.createdAt }
    }

    override suspend fun loadActivitiesForGame(gameId: String, campingId: String): List<Activity> {
        check()
        return (activitiesByCampingId[campingId] ?: emptyList())
            .filter { it.gameId == gameId }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun recordActivity(activity: Activity): Activity {
        check()
        val list = activitiesByCampingId.getOrPut(activity.campingId) { mutableListOf() }
        list.add(0, activity)
        return activity
    }

    override suspend fun deleteActivities(ids: List<String>, campingId: String) {
        check()
        val idSet = ids.toSet()
        activitiesByCampingId[campingId]?.removeAll { it.id in idSet }
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
