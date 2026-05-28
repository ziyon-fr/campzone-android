package fr.ziyon.campzone.data.polls

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.ziyon.campzone.data.model.Poll
import fr.ziyon.campzone.data.model.PollPayload
import fr.ziyon.campzone.data.model.PollVote
import fr.ziyon.campzone.data.model.toPoll
import fr.ziyon.campzone.data.model.toPollVote
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlin.math.max

interface PollService {
    suspend fun loadPolls(campingId: String): List<Poll>
    suspend fun loadPoll(pollId: String, campingId: String): Poll?
    fun observePoll(pollId: String, campingId: String): Flow<Poll?>
    suspend fun loadVote(pollId: String, campingId: String, voterId: String): PollVote?
    /** Create or update a poll (`setData` merge). [includeCreatedAt] only on first create. */
    suspend fun savePoll(poll: Poll, includeCreatedAt: Boolean)
    suspend fun setOpen(pollId: String, campingId: String, isOpen: Boolean)
    suspend fun castVote(
        campingId: String,
        pollId: String,
        voterId: String,
        selectedOptionIds: List<String>,
    )
    suspend fun deletePoll(pollId: String, campingId: String)
}

@Singleton
class FirestorePollService @Inject constructor(
    private val db: FirebaseFirestore,
) : PollService {

    override suspend fun loadPolls(campingId: String): List<Poll> {
        val snapshot = pollsCollection(campingId)
            .orderBy(Field.CreatedAt, Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.mapNotNull { document ->
            @Suppress("UNCHECKED_CAST")
            (document.data as? Map<String, Any?>)?.toPoll(document.id)?.copy(campingId = campingId)
        }
    }

    override suspend fun loadPoll(pollId: String, campingId: String): Poll? {
        val snapshot = pollDocument(campingId, pollId).get().await()
        @Suppress("UNCHECKED_CAST")
        return (snapshot.data as? Map<String, Any?>)?.toPoll(pollId)?.copy(campingId = campingId)
    }

    override fun observePoll(pollId: String, campingId: String): Flow<Poll?> = callbackFlow {
        val listener = pollDocument(campingId, pollId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val poll = (snapshot?.data as? Map<String, Any?>)?.toPoll(pollId)?.copy(campingId = campingId)
            trySend(poll)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun loadVote(pollId: String, campingId: String, voterId: String): PollVote? {
        if (voterId.isBlank()) return null
        val snapshot = voteDocument(campingId, pollId, voterId).get().await()
        @Suppress("UNCHECKED_CAST")
        val data = snapshot.data as? Map<String, Any?> ?: return null
        return data.toPollVote(snapshot.id)
    }

    override suspend fun savePoll(poll: Poll, includeCreatedAt: Boolean) {
        require(poll.campingId.isNotBlank()) { "Camping is required." }
        val payload = PollPayload.pollPayload(poll, now = Date(), includeCreatedAt = includeCreatedAt)
        pollsCollection(poll.campingId)
            .document(poll.id)
            .set(payload, SetOptions.merge())
            .await()
    }

    override suspend fun setOpen(pollId: String, campingId: String, isOpen: Boolean) {
        pollDocument(campingId, pollId).update(Field.IsOpen, isOpen).await()
    }

    override suspend fun castVote(
        campingId: String,
        pollId: String,
        voterId: String,
        selectedOptionIds: List<String>,
    ) {
        require(voterId.isNotBlank()) { "You must be signed in to vote." }
        val pollDoc = pollDocument(campingId, pollId)
        val voteDoc = voteDocument(campingId, pollId, voterId)

        db.runTransaction { transaction ->
            val pollSnapshot = transaction.get(pollDoc)
            @Suppress("UNCHECKED_CAST")
            val rawOptions = (pollSnapshot.get(Field.Options) as? List<Map<String, Any?>>)
                ?.map { it.toMutableMap() }
                ?.toMutableList()
                ?: throw IllegalArgumentException("Poll options not found or malformed.")

            // All reads must precede writes in a Firestore transaction.
            val voteSnapshot = transaction.get(voteDoc)
            @Suppress("UNCHECKED_CAST")
            val previous = (voteSnapshot.get(Field.SelectedOptionIds) as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()

            previous.forEach { optionId ->
                rawOptions.firstOrNull { it[Field.OptionId] == optionId }?.let { option ->
                    option[Field.OptionVoteCount] = max(0, option.voteCountValue() - 1)
                }
            }
            selectedOptionIds.forEach { optionId ->
                rawOptions.firstOrNull { it[Field.OptionId] == optionId }?.let { option ->
                    option[Field.OptionVoteCount] = option.voteCountValue() + 1
                }
            }

            transaction.update(pollDoc, Field.Options, rawOptions)
            transaction.set(
                voteDoc,
                PollPayload.votePayload(voterId, selectedOptionIds, FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            null
        }.await()
    }

    override suspend fun deletePoll(pollId: String, campingId: String) {
        pollDocument(campingId, pollId).delete().await()
    }

    private fun pollsCollection(campingId: String) =
        db.collection(Collection.Campings).document(campingId).collection(Collection.Polls)

    private fun pollDocument(campingId: String, pollId: String) =
        pollsCollection(campingId).document(pollId)

    private fun voteDocument(campingId: String, pollId: String, voterId: String) =
        pollDocument(campingId, pollId).collection(Collection.Votes).document(voterId)

    private fun Map<String, Any?>.voteCountValue(): Int =
        when (val value = this[Field.OptionVoteCount]) {
            is Long -> value.toInt()
            is Int -> value
            is Double -> value.toInt()
            else -> 0
        }

    private object Collection {
        const val Campings = "campings"
        const val Polls = "polls"
        const val Votes = "votes"
    }

    private object Field {
        const val CreatedAt = "createdAt"
        const val IsOpen = "isOpen"
        const val Options = "options"
        const val OptionId = "id"
        const val OptionVoteCount = "voteCount"
        const val SelectedOptionIds = "selectedOptionIDs"
    }
}

class FakePollService(
    polls: List<Poll> = emptyList(),
    votes: List<Pair<String, PollVote>> = emptyList(), // pollId to vote
    var shouldFail: Boolean = false,
) : PollService {
    private val pollsByCamping = polls.groupBy { it.campingId }
        .mapValues { it.value.toMutableList() }.toMutableMap()
    private val votesByPoll = votes.groupBy({ it.first }, { it.second })
        .mapValues { it.value.toMutableList() }.toMutableMap()

    private fun check() { if (shouldFail) throw IllegalStateException("FakePollService configured to fail.") }

    override suspend fun loadPolls(campingId: String): List<Poll> {
        check()
        return (pollsByCamping[campingId] ?: emptyList()).sortedByDescending { it.createdAt }
    }

    override suspend fun loadPoll(pollId: String, campingId: String): Poll? {
        check()
        return pollsByCamping[campingId]?.firstOrNull { it.id == pollId }
    }

    override fun observePoll(pollId: String, campingId: String): Flow<Poll?> = flow {
        check()
        emit(pollsByCamping[campingId]?.firstOrNull { it.id == pollId })
    }

    override suspend fun loadVote(pollId: String, campingId: String, voterId: String): PollVote? {
        check()
        return votesByPoll[pollId]?.firstOrNull { it.voterId == voterId }
    }

    override suspend fun savePoll(poll: Poll, includeCreatedAt: Boolean) {
        check()
        val list = pollsByCamping.getOrPut(poll.campingId) { mutableListOf() }
        list.removeAll { it.id == poll.id }
        list.add(poll)
    }

    override suspend fun setOpen(pollId: String, campingId: String, isOpen: Boolean) {
        check()
        pollsByCamping[campingId]?.replaceAll { if (it.id == pollId) it.copy(isOpen = isOpen) else it }
    }

    override suspend fun castVote(
        campingId: String,
        pollId: String,
        voterId: String,
        selectedOptionIds: List<String>,
    ) {
        check()
        val votes = votesByPoll.getOrPut(pollId) { mutableListOf() }
        val previous = votes.firstOrNull { it.voterId == voterId }
        votes.removeAll { it.voterId == voterId }
        votes.add(PollVote(voterId, selectedOptionIds, Date()))

        val list = pollsByCamping[campingId] ?: return
        val index = list.indexOfFirst { it.id == pollId }
        if (index < 0) return
        val options = list[index].options.map { option ->
            var count = option.voteCount
            if (previous?.selectedOptionIds?.contains(option.id) == true) count = max(0, count - 1)
            if (selectedOptionIds.contains(option.id)) count += 1
            option.copy(voteCount = count)
        }
        list[index] = list[index].copy(options = options)
    }

    override suspend fun deletePoll(pollId: String, campingId: String) {
        check()
        pollsByCamping[campingId]?.removeAll { it.id == pollId }
        votesByPoll.remove(pollId)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PollBindings {
    @Binds
    @Singleton
    abstract fun bindPollService(impl: FirestorePollService): PollService
}
