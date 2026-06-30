package fr.ziyon.campzone.ui.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

enum class QuickActionKind(val storageKey: String) {
    Schedule("schedule"),
    VenueMap("venueMap"),
    Songbook("songbook"),
    Teams("teams"),
    Games("games"),
    FoodMenu("foodMenu"),
    Guidelines("guidelines"),
    Packing("packing"),
    QrPass("qrPass"),
    Chat("chat"),
    Polls("polls"),
    Album("album"),
    Pricing("pricing"),
    Emergency("emergency"),
    Support("support"),
    Transportation("transportation"),
    Vehicles("vehicles"),
    CheckInScanner("checkInScanner");

    companion object {
        fun candidates(isLive: Boolean): List<QuickActionKind> =
            if (isLive) {
                listOf(QrPass, Schedule, VenueMap, Packing, FoodMenu, Teams, Songbook, Chat, Polls, Album, Games)
            } else {
                listOf(Schedule, VenueMap, Packing, Guidelines, Songbook, Teams, FoodMenu)
            }

        fun fromResourceId(resourceId: String): QuickActionKind? = when (resourceId) {
            "songbook" -> Songbook
            "emergency-safety" -> Emergency
            "support-sponsors" -> Support
            "chat" -> Chat
            "polls" -> Polls
            "album" -> Album
            "food-menu" -> FoodMenu
            "packing" -> Packing
            "pricing" -> Pricing
            "check-in-scanner" -> CheckInScanner
            "transportation" -> Transportation
            "vehicles" -> Vehicles
            else -> null
        }
    }
}

class QuickActionUsageStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val counts = mutableStateMapOf<QuickActionKind, Int>()

    init {
        QuickActionKind.entries.forEach { kind ->
            val storedCount = preferences.getInt(kind.storageKey, 0)
            if (storedCount > 0) counts[kind] = storedCount
        }
    }

    fun count(forKind: QuickActionKind): Int = counts[forKind] ?: 0

    fun record(kind: QuickActionKind) {
        val nextCount = count(kind) + 1
        counts[kind] = nextCount
        preferences.edit {
            putInt(kind.storageKey, nextCount)
        }
    }

    fun ranked(candidates: List<QuickActionKind>): List<QuickActionKind> =
        candidates
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<QuickActionKind>> { count(it.value) }
                    .thenBy { it.index },
            )
            .map { it.value }

    companion object {
        private const val PREFERENCES_NAME = "cz.home.quickActionTapCounts.v1"

        @Volatile
        private var shared: QuickActionUsageStore? = null

        fun shared(context: Context): QuickActionUsageStore =
            shared ?: synchronized(this) {
                shared ?: QuickActionUsageStore(context).also { shared = it }
            }
    }
}

@Composable
fun rememberQuickActionUsageStore(): QuickActionUsageStore {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        QuickActionUsageStore.shared(context)
    }
}
