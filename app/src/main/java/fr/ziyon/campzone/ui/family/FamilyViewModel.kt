package fr.ziyon.campzone.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermission
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.auth.PreferredLanguage
import fr.ziyon.campzone.data.auth.UserGender
import fr.ziyon.campzone.data.church.ChurchDirectory
import fr.ziyon.campzone.data.church.ChurchGroup
import fr.ziyon.campzone.data.church.SDAChurch
import fr.ziyon.campzone.data.church.groupedByCountry
import fr.ziyon.campzone.data.family.ChildParticipant
import fr.ziyon.campzone.data.family.FamilyParticipantDuplicateMatch
import fr.ziyon.campzone.data.family.FamilyRelationship
import fr.ziyon.campzone.data.family.FamilyRepository
import fr.ziyon.campzone.data.media.ImageUploader
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FamilyListState {
    data object Loading : FamilyListState
    data class Loaded(val children: List<ChildParticipant>) : FamilyListState
    data object Empty : FamilyListState
    data class Error(val message: String) : FamilyListState
}

sealed interface FamilyFeedback {
    data object Saved : FamilyFeedback
    data object Removed : FamilyFeedback
    data object PermissionDenied : FamilyFeedback
    data class Failure(val message: String) : FamilyFeedback
}

enum class ChildValidationError {
    DisplayNameRequired,
    AgeRequired,
    AgeOutOfRange,
    ChurchRequired,
    EmergencyContactRequired,
    EmergencyPhoneRequired,
    RelationshipLabelRequired,
    GuardianConsentRequired,
}

data class ChildFormState(
    val displayName: String = "",
    val ageText: String = "",
    val gender: UserGender = UserGender.PreferNotToSay,
    val church: String = "",
    val preferredLanguage: PreferredLanguage = PreferredLanguage.French,
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val medicalNotes: String = "",
    val relationship: FamilyRelationship = FamilyRelationship.Parent,
    val customRelationshipLabel: String = "",
    val hasGuardianConsent: Boolean = false,
    val photoUrl: String? = null,
    val photoPublicId: String? = null,
) {
    val ageOrNull: Int?
        get() = ageText.trim().toIntOrNull()
}

data class ChildEditorState(
    val existingChildId: String?,
    val documentId: String,
    val form: ChildFormState = ChildFormState(),
    val validationErrors: List<ChildValidationError> = emptyList(),
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val photoError: String? = null,
    val pendingDuplicate: FamilyParticipantDuplicateMatch? = null,
    val errorMessage: String? = null,
) {
    val isEditing: Boolean get() = existingChildId != null
    val hasPhoto: Boolean get() = !form.photoUrl.isNullOrBlank()
}

data class FamilyUiState(
    val listState: FamilyListState = FamilyListState.Loading,
    val children: List<ChildParticipant> = emptyList(),
    val canManageFamily: Boolean = false,
    val isDeleting: Boolean = false,
    val feedback: FamilyFeedback? = null,
    val editor: ChildEditorState? = null,
    val churchQuery: String = "",
    val churches: List<SDAChurch> = emptyList(),
    val isLoadingChurches: Boolean = false,
    val churchError: String? = null,
) {
    val filteredChurchGroups: List<ChurchGroup>
        get() = churches.groupedByCountry(churchQuery)
}

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val repository: FamilyRepository,
    private val imageUploader: ImageUploader,
    private val churchDirectory: ChurchDirectory,
) : ViewModel() {
    private val permissions = AppPermissionEvaluator()
    private val _uiState = MutableStateFlow(FamilyUiState())
    val uiState: StateFlow<FamilyUiState> = _uiState.asStateFlow()

    private var loadedUserId: String? = null

    fun load(user: AuthenticatedUser) {
        val canManage = canManageFamily(user)
        if (!canManage) {
            _uiState.value = _uiState.value.copy(
                canManageFamily = false,
                listState = FamilyListState.Empty,
            )
            return
        }
        if (loadedUserId == user.uid && _uiState.value.listState !is FamilyListState.Loading) {
            _uiState.value = _uiState.value.copy(canManageFamily = true)
            return
        }
        reload(user)
    }

    fun reload(user: AuthenticatedUser) {
        if (!canManageFamily(user)) return
        loadedUserId = user.uid
        _uiState.value = _uiState.value.copy(
            canManageFamily = true,
            listState = FamilyListState.Loading,
        )
        viewModelScope.launch {
            runCatching { repository.loadChildren(user.uid) }
                .onSuccess { children -> publishChildren(children) }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        listState = FamilyListState.Error(error.friendlyMessage()),
                    )
                }
        }
    }

    // region Editor

    fun openEditor(childId: String?, user: AuthenticatedUser) {
        val existing = childId?.let { id -> _uiState.value.children.firstOrNull { it.id == id } }
        val form = existing?.let(::ChildFormState) ?: ChildFormState(
            church = user.church,
            preferredLanguage = PreferredLanguage.fromWire(user.preferredLanguage)
                ?: PreferredLanguage.French,
        )
        _uiState.value = _uiState.value.copy(
            editor = ChildEditorState(
                existingChildId = existing?.id,
                documentId = existing?.id ?: UUID.randomUUID().toString(),
                form = form,
            ),
            feedback = null,
        )
    }

    fun closeEditor() {
        _uiState.value = _uiState.value.copy(editor = null)
    }

    fun loadChurches() {
        val state = _uiState.value
        if (state.churches.isNotEmpty() || state.isLoadingChurches) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingChurches = true, churchError = null)
            runCatching { churchDirectory.loadChurches() }
                .onSuccess { churches ->
                    _uiState.value = _uiState.value.copy(isLoadingChurches = false, churches = churches)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingChurches = false,
                        churchError = error.friendlyMessage(),
                    )
                }
        }
    }

    fun updateChurchQuery(query: String) {
        _uiState.value = _uiState.value.copy(churchQuery = query)
    }

    fun selectChurch(name: String) {
        updateForm { copy(church = name) }
        _uiState.value = _uiState.value.copy(churchQuery = "")
    }

    fun updateForm(update: ChildFormState.() -> ChildFormState) {
        val editor = _uiState.value.editor ?: return
        _uiState.value = _uiState.value.copy(
            editor = editor.copy(
                form = editor.form.update(),
                validationErrors = emptyList(),
                errorMessage = null,
            ),
        )
    }

    fun uploadPhoto(bytes: ByteArray, mimeType: String, fileExtension: String) {
        val editor = _uiState.value.editor ?: return
        viewModelScope.launch {
            updateEditor { copy(isUploadingPhoto = true, photoError = null) }
            runCatching {
                imageUploader.uploadImage(
                    assetIdPrefix = editor.documentId,
                    folder = "campzone/participants",
                    tags = listOf("campzone", "participants", "participant_${editor.documentId}"),
                    bytes = bytes,
                    mimeType = mimeType,
                    fileExtension = fileExtension,
                )
            }.onSuccess { result ->
                updateEditor {
                    copy(
                        isUploadingPhoto = false,
                        form = form.copy(photoUrl = result.secureUrl, photoPublicId = result.publicId),
                    )
                }
            }.onFailure { error ->
                updateEditor { copy(isUploadingPhoto = false, photoError = error.friendlyMessage()) }
            }
        }
    }

    fun reportPhotoError(message: String) {
        updateEditor { copy(photoError = message) }
    }

    fun removePhoto() {
        updateEditor { copy(form = form.copy(photoUrl = null, photoPublicId = null), photoError = null) }
    }

    fun save(user: AuthenticatedUser, onSaved: () -> Unit) {
        submit(user = user, forceOverride = false, onSaved = onSaved)
    }

    fun confirmDuplicateSave(user: AuthenticatedUser, onSaved: () -> Unit) {
        submit(user = user, forceOverride = true, onSaved = onSaved)
    }

    fun cancelDuplicate() {
        updateEditor { copy(pendingDuplicate = null) }
    }

    private fun submit(user: AuthenticatedUser, forceOverride: Boolean, onSaved: () -> Unit) {
        val editor = _uiState.value.editor ?: return
        if (!canManageFamily(user)) {
            _uiState.value = _uiState.value.copy(feedback = FamilyFeedback.PermissionDenied)
            return
        }

        val errors = editor.form.validationErrors()
        if (errors.isNotEmpty()) {
            updateEditor { copy(validationErrors = errors, pendingDuplicate = null) }
            return
        }

        viewModelScope.launch {
            if (!forceOverride) {
                val duplicate = findLocalDuplicate(editor) ?: findCrossGuardianDuplicate(editor, user.uid)
                if (duplicate != null) {
                    updateEditor { copy(pendingDuplicate = duplicate) }
                    return@launch
                }
            }

            updateEditor { copy(isSaving = true, pendingDuplicate = null, errorMessage = null) }
            val child = editor.form.toChild(id = editor.documentId, guardianId = user.uid)
            runCatching { repository.saveChild(child, user.uid) }
                .onSuccess { saved ->
                    val nextChildren = _uiState.value.children
                        .filterNot { it.id == saved.id }
                        .plus(saved)
                        .sortedBy { it.displayName.lowercase() }
                    _uiState.value = _uiState.value.copy(
                        children = nextChildren,
                        listState = listStateFor(nextChildren),
                        editor = null,
                        feedback = FamilyFeedback.Saved,
                    )
                    onSaved()
                }
                .onFailure { error ->
                    updateEditor { copy(isSaving = false, errorMessage = error.friendlyMessage()) }
                }
        }
    }

    // endregion

    fun deleteChild(child: ChildParticipant, user: AuthenticatedUser) {
        if (!canManageFamily(user)) {
            _uiState.value = _uiState.value.copy(feedback = FamilyFeedback.PermissionDenied)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            runCatching { repository.deleteChild(child.id, user.uid) }
                .onSuccess {
                    val nextChildren = _uiState.value.children.filterNot { it.id == child.id }
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        children = nextChildren,
                        listState = listStateFor(nextChildren),
                        feedback = FamilyFeedback.Removed,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        feedback = FamilyFeedback.Failure(error.friendlyMessage()),
                    )
                }
        }
    }

    fun dismissFeedback() {
        _uiState.value = _uiState.value.copy(feedback = null)
    }

    private fun findLocalDuplicate(editor: ChildEditorState): FamilyParticipantDuplicateMatch? {
        val candidateName = editor.form.displayName.trim().lowercase()
        val candidateAge = editor.form.ageOrNull ?: return null
        if (candidateName.isEmpty()) return null

        val existing = _uiState.value.children.firstOrNull { child ->
            child.id != editor.existingChildId &&
                child.displayName.trim().lowercase() == candidateName &&
                child.age == candidateAge
        } ?: return null

        return FamilyParticipantDuplicateMatch(existing = existing, guardianDisplayName = "")
    }

    private suspend fun findCrossGuardianDuplicate(
        editor: ChildEditorState,
        userId: String,
    ): FamilyParticipantDuplicateMatch? {
        val age = editor.form.ageOrNull ?: return null
        return runCatching {
            repository.findSimilarParticipant(
                displayName = editor.form.displayName.trim(),
                age = age,
                excludingGuardianId = userId,
            )
        }.getOrNull()
    }

    private fun publishChildren(children: List<ChildParticipant>) {
        val sorted = children.sortedBy { it.displayName.lowercase() }
        _uiState.value = _uiState.value.copy(
            children = sorted,
            listState = listStateFor(sorted),
        )
    }

    private fun listStateFor(children: List<ChildParticipant>): FamilyListState =
        if (children.isEmpty()) FamilyListState.Empty else FamilyListState.Loaded(children)

    private fun canManageFamily(user: AuthenticatedUser): Boolean =
        permissions.hasPermission(
            user = PermissionUser(role = user.role, church = user.church),
            permission = AppPermission.ManageFamilyRegistrations,
        )

    private fun updateEditor(update: ChildEditorState.() -> ChildEditorState) {
        val editor = _uiState.value.editor ?: return
        _uiState.value = _uiState.value.copy(editor = editor.update())
    }

    private fun Throwable.friendlyMessage(): String =
        message?.takeUnless { it.isBlank() } ?: "Something went wrong. Please try again."
}

internal fun ChildFormState(child: ChildParticipant): ChildFormState =
    ChildFormState(
        displayName = child.displayName,
        ageText = child.age.toString(),
        gender = child.gender,
        church = child.church,
        preferredLanguage = PreferredLanguage.fromWire(child.preferredLanguage)
            ?: PreferredLanguage.French,
        emergencyContactName = child.emergencyContactName,
        emergencyContactPhone = child.emergencyContactPhone,
        medicalNotes = child.medicalNotes,
        relationship = child.relationship,
        customRelationshipLabel = child.customRelationshipLabel,
        hasGuardianConsent = child.guardianConsentAt != null,
        photoUrl = child.photoUrl,
        photoPublicId = child.photoPublicId,
    )

internal fun ChildFormState.toChild(id: String, guardianId: String): ChildParticipant =
    ChildParticipant(
        id = id,
        guardianId = guardianId,
        displayName = displayName.trim(),
        age = ageOrNull ?: 0,
        gender = gender,
        church = church.trim(),
        preferredLanguage = preferredLanguage.wireValue,
        emergencyContactName = emergencyContactName.trim(),
        emergencyContactPhone = emergencyContactPhone.trim(),
        medicalNotes = medicalNotes.trim(),
        relationship = relationship,
        customRelationshipLabel = if (relationship.requiresCustomLabel) customRelationshipLabel.trim() else "",
        guardianConsentAt = if (hasGuardianConsent) Date() else null,
        photoUrl = photoUrl,
        photoPublicId = photoPublicId?.trim(),
        updatedAt = Date(),
    )

internal fun ChildFormState.validationErrors(): List<ChildValidationError> {
    val errors = mutableListOf<ChildValidationError>()

    if (displayName.trim().isEmpty()) errors += ChildValidationError.DisplayNameRequired

    val age = ageOrNull
    if (age == null) {
        errors += ChildValidationError.AgeRequired
        return errors
    }
    if (age !in 0..17) errors += ChildValidationError.AgeOutOfRange

    if (church.trim().isEmpty()) errors += ChildValidationError.ChurchRequired
    if (emergencyContactName.trim().isEmpty()) errors += ChildValidationError.EmergencyContactRequired
    if (emergencyContactPhone.trim().isEmpty()) errors += ChildValidationError.EmergencyPhoneRequired
    if (relationship.requiresCustomLabel && customRelationshipLabel.trim().isEmpty()) {
        errors += ChildValidationError.RelationshipLabelRequired
    }
    if (!hasGuardianConsent) errors += ChildValidationError.GuardianConsentRequired

    return errors
}
