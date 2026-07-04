package fr.ziyon.campzone.ui.camping.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.CampingPermissionContext
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingRegistrationStatus
import fr.ziyon.campzone.data.model.CampingTemplateCloneForm
import fr.ziyon.campzone.data.model.CampingTemplateCloneOptions
import fr.ziyon.campzone.data.model.CampingTemplateCloneValidationError
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CampingTemplateCloneUiState {
    data object Loading : CampingTemplateCloneUiState
    data object Restricted : CampingTemplateCloneUiState
    data class Ready(val source: Camping) : CampingTemplateCloneUiState
    data class Error(val message: String) : CampingTemplateCloneUiState
}

data class CampingTemplateClonePermissions(
    val canCopySchedule: Boolean = false,
    val canCopyTeams: Boolean = false,
    val canCopySongbook: Boolean = false,
    val canCopyGuidelines: Boolean = false,
) {
    val canCopyAny: Boolean
        get() = canCopySchedule || canCopyTeams || canCopySongbook || canCopyGuidelines
}

@HiltViewModel
class CampingTemplateCloneViewModel @Inject constructor(
    private val campingService: CampingService,
    private val stringProvider: StringProvider,
) : ViewModel() {
    private val permissions = AppPermissionEvaluator()

    private val _uiState = MutableStateFlow<CampingTemplateCloneUiState>(CampingTemplateCloneUiState.Loading)
    val uiState: StateFlow<CampingTemplateCloneUiState> = _uiState.asStateFlow()

    private val _form = MutableStateFlow(CampingTemplateCloneForm())
    val form: StateFlow<CampingTemplateCloneForm> = _form.asStateFlow()

    private val _availableCopies = MutableStateFlow(CampingTemplateClonePermissions())
    val availableCopies: StateFlow<CampingTemplateClonePermissions> = _availableCopies.asStateFlow()

    private val _validationErrors = MutableStateFlow<List<CampingTemplateCloneValidationError>>(emptyList())
    val validationErrors: StateFlow<List<CampingTemplateCloneValidationError>> = _validationErrors.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private val _createdCampingId = MutableStateFlow<String?>(null)
    val createdCampingId: StateFlow<String?> = _createdCampingId.asStateFlow()

    private var loadedKey: Pair<String, String>? = null

    fun load(sourceCampingId: String, user: AuthenticatedUser) {
        val key = sourceCampingId to user.uid
        if (loadedKey == key && _uiState.value is CampingTemplateCloneUiState.Ready) return
        loadedKey = key
        _uiState.value = CampingTemplateCloneUiState.Loading
        _operationError.value = null
        viewModelScope.launch {
            runCatching { campingService.fetchCamping(sourceCampingId) }
                .onSuccess { source ->
                    val permissionUser = PermissionUser(
                        role = user.role,
                        userId = user.uid,
                        church = user.church,
                    )
                    val context = source.permissionContext()
                    val copyPermissions = CampingTemplateClonePermissions(
                        canCopySchedule = permissions.canManageSchedule(permissionUser, context),
                        canCopyTeams = permissions.canManageTeams(permissionUser, context),
                        canCopySongbook = permissions.canManageSongbook(permissionUser, context),
                        canCopyGuidelines = permissions.canEditGuidelines(permissionUser, context),
                    )
                    val canCreateTarget = permissions.canCreateCamping(permissionUser, context)
                    if (!canCreateTarget || !copyPermissions.canCopyAny) {
                        _availableCopies.value = copyPermissions
                        _uiState.value = CampingTemplateCloneUiState.Restricted
                        return@onSuccess
                    }
                    _availableCopies.value = copyPermissions
                    _form.value = CampingTemplateCloneForm.from(source).copy(
                        options = CampingTemplateCloneOptions(
                            includeSchedule = copyPermissions.canCopySchedule,
                            includeTeams = copyPermissions.canCopyTeams,
                            includeSongbook = copyPermissions.canCopySongbook,
                            includeGuidelines = copyPermissions.canCopyGuidelines,
                        ),
                    )
                    _validationErrors.value = emptyList()
                    _uiState.value = CampingTemplateCloneUiState.Ready(source)
                }
                .onFailure { error ->
                    loadedKey = null
                    _uiState.value = CampingTemplateCloneUiState.Error(
                        error.message ?: stringProvider.get(R.string.camping_template_load_failed),
                    )
                }
        }
    }

    fun retry(sourceCampingId: String, user: AuthenticatedUser) {
        loadedKey = null
        load(sourceCampingId, user)
    }

    fun updateTitle(value: String) = _form.update { it.copy(title = value) }

    fun updateStartDate(value: Date) {
        _form.update { current ->
            val duration = (current.endDate.time - current.startDate.time).coerceAtLeast(0L)
            current.copy(startDate = value, endDate = Date(value.time + duration))
        }
    }

    fun updateEndDate(value: Date) = _form.update { it.copy(endDate = value) }

    fun updateRegistrationStatus(value: CampingRegistrationStatus) =
        _form.update { it.copy(registrationStatus = value) }

    fun toggleSchedule(value: Boolean) = updateOptions { it.copy(includeSchedule = value) }
    fun toggleTeams(value: Boolean) = updateOptions { it.copy(includeTeams = value) }
    fun toggleSongbook(value: Boolean) = updateOptions { it.copy(includeSongbook = value) }
    fun toggleGuidelines(value: Boolean) = updateOptions { it.copy(includeGuidelines = value) }

    private fun updateOptions(update: (CampingTemplateCloneOptions) -> CampingTemplateCloneOptions) {
        val available = _availableCopies.value
        _form.update { current ->
            val requested = update(current.options)
            current.copy(
                options = requested.copy(
                    includeSchedule = requested.includeSchedule && available.canCopySchedule,
                    includeTeams = requested.includeTeams && available.canCopyTeams,
                    includeSongbook = requested.includeSongbook && available.canCopySongbook,
                    includeGuidelines = requested.includeGuidelines && available.canCopyGuidelines,
                ),
            )
        }
    }

    fun cloneTemplate(sourceCampingId: String) {
        val errors = _form.value.validationErrors()
        _validationErrors.value = errors
        if (errors.isNotEmpty() || _isSaving.value) return

        _isSaving.value = true
        _operationError.value = null
        viewModelScope.launch {
            runCatching { campingService.cloneCampingTemplate(_form.value.request(sourceCampingId)) }
                .onSuccess { cloned ->
                    _createdCampingId.value = cloned.id
                }
                .onFailure { error ->
                    _operationError.value = error.message ?: stringProvider.get(R.string.camping_template_create_failed)
                }
            _isSaving.value = false
        }
    }

    fun consumeCreatedCampingId() {
        _createdCampingId.value = null
    }

    fun consumeOperationError() {
        _operationError.value = null
    }
}

private fun Camping.permissionContext(): CampingPermissionContext =
    CampingPermissionContext(
        organizerLevelType = organizerLevel.type.wireValue,
        organizerLevelValue = organizerLevel.value,
        createdByUid = createdByUid,
    )
