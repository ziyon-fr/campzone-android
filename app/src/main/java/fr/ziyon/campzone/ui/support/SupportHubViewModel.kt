package fr.ziyon.campzone.ui.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.AppPermission
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.core.permissions.UserRole
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.support.SupportHub
import fr.ziyon.campzone.data.support.SupportHubService
import fr.ziyon.campzone.data.support.SupportUrlValidator
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SupportHubMode { App, Camp }
data class SupportHubUiState(
    val loading: Boolean = true,
    val mode: SupportHubMode = SupportHubMode.App,
    val campingId: String? = null,
    val title: String = "Campzone",
    val hub: SupportHub? = null,
    val canManage: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SupportHubViewModel @Inject constructor(
    private val service: SupportHubService,
    private val campingService: CampingService,
    private val strings: StringProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SupportHubUiState())
    val uiState: StateFlow<SupportHubUiState> = _uiState.asStateFlow()
    private val permissions = AppPermissionEvaluator()

    fun loadApp(user: AuthenticatedUser) = load(SupportHubMode.App, user, null)
    fun loadCamp(campingId: String, user: AuthenticatedUser) = load(SupportHubMode.Camp, user, campingId)

    private fun load(mode: SupportHubMode, user: AuthenticatedUser, campingId: String?) {
        viewModelScope.launch {
            _uiState.value = SupportHubUiState(loading = true, mode = mode, campingId = campingId)
            runCatching {
                if (mode == SupportHubMode.App) {
                    SupportHubUiState(false, mode, null, "Campzone", service.loadApp(), user.role == UserRole.Admin)
                } else {
                    val camping = campingService.fetchCamping(requireNotNull(campingId))
                    val permissionUser = PermissionUser(user.role, user.uid, user.church)
                    val context = CampingPermissionContext(camping.organizerLevel.type.wireValue, camping.organizerLevel.value, camping.createdByUid)
                    SupportHubUiState(
                        loading = false,
                        mode = mode,
                        campingId = campingId,
                        title = camping.title,
                        hub = service.loadCamp(camping.id, camping.title),
                        canManage = permissions.canManageAnnouncements(permissionUser, context) ||
                            permissions.canManageSchedule(permissionUser, context) || permissions.canEditCamping(permissionUser, context),
                    )
                }
            }.onSuccess { _uiState.value = it }
                .onFailure { _uiState.update { it.copy(loading = false, error = it.message ?: strings.get(R.string.support_error_load)) } }
        }
    }

    fun save(hub: SupportHub) {
        val validation = validate(hub)
        if (validation != null) return _uiState.update { it.copy(error = validation) }
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            runCatching {
                if (state.mode == SupportHubMode.App) service.saveApp(hub)
                else service.saveCamp(requireNotNull(state.campingId), hub)
            }.onSuccess { saved -> _uiState.update { it.copy(hub = saved, saving = false, message = strings.get(R.string.support_feedback_saved)) } }
                .onFailure { error -> _uiState.update { it.copy(saving = false, error = error.message ?: strings.get(R.string.support_error_update)) } }
        }
    }

    private fun validate(hub: SupportHub): String? = when {
        hub.intro.isBlank() -> strings.get(R.string.support_error_intro)
        hub.links.any { (it.title.isNotBlank() || it.urlString.isNotBlank() || it.subtitle.isNotBlank()) && it.title.isBlank() } -> strings.get(R.string.support_error_link_title)
        hub.links.any { it.urlString.isNotBlank() && SupportUrlValidator.url(it.urlString) == null } -> strings.get(R.string.support_error_link_url)
        hub.sponsors.any { (it.name.isNotBlank() || it.note.isNotBlank() || it.urlString.isNotBlank()) && it.name.isBlank() } -> strings.get(R.string.support_error_sponsor_name)
        hub.sponsors.any { it.urlString.isNotBlank() && SupportUrlValidator.url(it.urlString) == null } -> strings.get(R.string.support_error_sponsor_url)
        else -> null
    }
}
