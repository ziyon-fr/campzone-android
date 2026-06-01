package fr.ziyon.campzone.ui.admin.onboarding

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stable ids for the admin setup checklist (mirror the iOS `@AppStorage` keys
 * so progress reads the same on a shared device profile model).
 */
enum class AdminOnboardingStepId(val storageKey: String) {
    Camping("admin_onboarding_camping"),
    Announcement("admin_onboarding_announcement"),
    Roles("admin_onboarding_roles"),
    Rules("admin_onboarding_rules"),
    Notifications("admin_onboarding_notifications");

    companion object {
        fun fromStorageKey(key: String): AdminOnboardingStepId? =
            entries.firstOrNull { it.storageKey == key }
    }
}

/**
 * Persists which admin setup steps have been completed. Local UI state only —
 * no Firestore — backed by [SharedPreferences] (the project's pattern for
 * device-local flags). Mirrors iOS `AdminOnboardingView` progress tracking.
 */
@HiltViewModel
class AdminOnboardingViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
) : ViewModel() {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    private val _completed = MutableStateFlow(readCompleted())
    val completed: StateFlow<Set<AdminOnboardingStepId>> = _completed.asStateFlow()

    fun markComplete(step: AdminOnboardingStepId) {
        if (step in _completed.value) return
        persist(_completed.value + step)
    }

    fun toggle(step: AdminOnboardingStepId) {
        val current = _completed.value
        persist(if (step in current) current - step else current + step)
    }

    fun reset() {
        prefs.edit().apply {
            AdminOnboardingStepId.entries.forEach { remove(it.storageKey) }
        }.apply()
        _completed.value = emptySet()
    }

    private fun persist(updated: Set<AdminOnboardingStepId>) {
        prefs.edit().apply {
            AdminOnboardingStepId.entries.forEach { step ->
                putBoolean(step.storageKey, step in updated)
            }
        }.apply()
        _completed.value = updated
    }

    private fun readCompleted(): Set<AdminOnboardingStepId> =
        AdminOnboardingStepId.entries
            .filter { prefs.getBoolean(it.storageKey, false) }
            .toSet()

    private companion object {
        const val PrefsName = "admin_onboarding"
    }
}
