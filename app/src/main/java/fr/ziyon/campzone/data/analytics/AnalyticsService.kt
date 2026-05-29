package fr.ziyon.campzone.data.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

data class AnalyticsEvent(
    val name: String,
    val parameters: Map<String, String> = emptyMap(),
)

interface AnalyticsService {
    fun viewCamping(id: String, title: String)
    fun registerForCamping(id: String)
    fun cancelCamping(id: String)
    fun viewSchedule(campingId: String)
    fun viewSongbook(campingId: String)
    fun viewTeams(campingId: String)
    fun playSong(id: String, title: String)
    fun favoriteSong(id: String, title: String)
    fun searchCampings(query: String)
    fun signIn(provider: String)
    fun signOut()
}

interface AnalyticsLogger {
    fun log(event: AnalyticsEvent)
}

class FirebaseAnalyticsLogger @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics,
) : AnalyticsLogger {
    override fun log(event: AnalyticsEvent) {
        val bundle = Bundle().apply {
            event.parameters.forEach { (key, value) -> putString(key, value) }
        }
        firebaseAnalytics.logEvent(event.name, bundle)
    }
}

class CampzoneAnalyticsService @Inject constructor(
    private val logger: AnalyticsLogger,
) : AnalyticsService {
    override fun viewCamping(id: String, title: String) {
        logger.log(
            AnalyticsEvent(
                name = AnalyticsEventNames.ViewItem,
                parameters = mapOf(
                    AnalyticsParams.ItemId to id,
                    AnalyticsParams.ItemName to title,
                    AnalyticsParams.ContentType to AnalyticsContentTypes.Camping,
                ),
            ),
        )
    }

    override fun registerForCamping(id: String) {
        logger.log(
            AnalyticsEvent(
                name = AnalyticsEventNames.RegisterCamping,
                parameters = mapOf(AnalyticsParams.ItemId to id),
            ),
        )
    }

    override fun cancelCamping(id: String) {
        logger.log(
            AnalyticsEvent(
                name = AnalyticsEventNames.CancelCamping,
                parameters = mapOf(AnalyticsParams.ItemId to id),
            ),
        )
    }

    override fun viewSchedule(campingId: String) {
        logger.log(campingScopedEvent(AnalyticsEventNames.ViewSchedule, campingId))
    }

    override fun viewSongbook(campingId: String) {
        logger.log(campingScopedEvent(AnalyticsEventNames.ViewSongbook, campingId))
    }

    override fun viewTeams(campingId: String) {
        logger.log(campingScopedEvent(AnalyticsEventNames.ViewTeams, campingId))
    }

    override fun playSong(id: String, title: String) {
        logger.log(songEvent(AnalyticsEventNames.PlaySong, id, title))
    }

    override fun favoriteSong(id: String, title: String) {
        logger.log(songEvent(AnalyticsEventNames.FavoriteSong, id, title))
    }

    override fun searchCampings(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        logger.log(
            AnalyticsEvent(
                name = AnalyticsEventNames.Search,
                parameters = mapOf(AnalyticsParams.SearchTerm to trimmed),
            ),
        )
    }

    override fun signIn(provider: String) {
        logger.log(
            AnalyticsEvent(
                name = AnalyticsEventNames.Login,
                parameters = mapOf(AnalyticsParams.Method to provider),
            ),
        )
    }

    override fun signOut() {
        logger.log(AnalyticsEvent(name = AnalyticsEventNames.SignOut))
    }

    private fun campingScopedEvent(name: String, campingId: String) = AnalyticsEvent(
        name = name,
        parameters = mapOf(AnalyticsParams.CampingId to campingId),
    )

    private fun songEvent(name: String, id: String, title: String) = AnalyticsEvent(
        name = name,
        parameters = mapOf(
            AnalyticsParams.ItemId to id,
            AnalyticsParams.ItemName to title,
        ),
    )
}

object NoOpAnalyticsService : AnalyticsService {
    override fun viewCamping(id: String, title: String) = Unit
    override fun registerForCamping(id: String) = Unit
    override fun cancelCamping(id: String) = Unit
    override fun viewSchedule(campingId: String) = Unit
    override fun viewSongbook(campingId: String) = Unit
    override fun viewTeams(campingId: String) = Unit
    override fun playSong(id: String, title: String) = Unit
    override fun favoriteSong(id: String, title: String) = Unit
    override fun searchCampings(query: String) = Unit
    override fun signIn(provider: String) = Unit
    override fun signOut() = Unit
}

object AnalyticsEventNames {
    const val ViewItem = "view_item"
    const val RegisterCamping = "register_camping"
    const val CancelCamping = "cancel_camping"
    const val ViewSchedule = "view_schedule"
    const val ViewSongbook = "view_songbook"
    const val ViewTeams = "view_teams"
    const val PlaySong = "play_song"
    const val FavoriteSong = "favorite_song"
    const val Search = "search"
    const val Login = "login"
    const val SignOut = "sign_out"
}

object AnalyticsParams {
    const val ItemId = "item_id"
    const val ItemName = "item_name"
    const val ContentType = "content_type"
    const val CampingId = "camping_id"
    const val SearchTerm = "search_term"
    const val Method = "method"
}

object AnalyticsContentTypes {
    const val Camping = "camping"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsBindings {
    @Binds
    @Singleton
    abstract fun bindAnalyticsLogger(
        logger: FirebaseAnalyticsLogger,
    ): AnalyticsLogger

    @Binds
    @Singleton
    abstract fun bindAnalyticsService(
        service: CampzoneAnalyticsService,
    ): AnalyticsService
}
