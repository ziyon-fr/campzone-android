package fr.ziyon.campzone.ui.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

class QuickActionPinStore internal constructor(
    private val preferences: SharedPreferences,
) {
    private val pinnedState = mutableStateListOf<QuickActionKind>()

    val pinned: List<QuickActionKind>
        get() = pinnedState.toList()

    init {
        pinnedState.addAll(
            QuickActionPinPolicy.normalized(
                preferences.getString(StorageKey, null)
                    .orEmpty()
                    .split(StorageSeparator)
                    .mapNotNull { raw -> QuickActionKind.entries.firstOrNull { it.storageKey == raw } },
            ),
        )
    }

    fun isPinned(kind: QuickActionKind): Boolean = kind in pinnedState

    fun canPin(kind: QuickActionKind): Boolean =
        isPinned(kind) || pinnedState.size < MaximumPinnedCount

    fun pin(kind: QuickActionKind): Boolean {
        if (isPinned(kind)) return true
        if (!canPin(kind)) return false
        pinnedState += kind
        persist()
        return true
    }

    fun unpin(kind: QuickActionKind) {
        if (pinnedState.remove(kind)) persist()
    }

    fun toggle(kind: QuickActionKind): Boolean {
        if (isPinned(kind)) {
            unpin(kind)
            return true
        }
        return pin(kind)
    }

    fun clear() {
        pinnedState.clear()
        persist()
    }

    private fun persist() {
        preferences.edit {
            if (pinnedState.isEmpty()) {
                remove(StorageKey)
            } else {
                putString(StorageKey, pinnedState.joinToString(StorageSeparator.toString()) { it.storageKey })
            }
        }
    }

    companion object {
        const val MaximumPinnedCount = 2
        private const val PreferencesName = "cz.home.quickActionPins.v2"
        private const val StorageKey = "orderedPins"
        private const val StorageSeparator = ','

        @Volatile
        private var shared: QuickActionPinStore? = null

        fun shared(context: Context): QuickActionPinStore = shared ?: synchronized(this) {
            shared ?: QuickActionPinStore(
                context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE),
            ).also { shared = it }
        }
    }
}

internal object QuickActionPinPolicy {
    fun normalized(kinds: List<QuickActionKind>): List<QuickActionKind> =
        kinds.distinct().take(QuickActionPinStore.MaximumPinnedCount)
}

@Composable
fun rememberQuickActionPinStore(): QuickActionPinStore {
    val context = LocalContext.current
    return remember(context.applicationContext) { QuickActionPinStore.shared(context) }
}
