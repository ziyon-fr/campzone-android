package fr.ziyon.campzone.ui.camping.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.analytics.AnalyticsService
import fr.ziyon.campzone.data.analytics.NoOpAnalyticsService
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.media.ImageUploader
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingPublicationStatus
import fr.ziyon.campzone.data.model.OrganizerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CampingAdminUiState(
    val isLoadingCamping: Boolean = false,
    val existingCamping: Camping? = null,
    val form: CampingEditorForm = CampingEditorForm(),
    val validationErrors: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val isCancelling: Boolean = false,
    val isDeleting: Boolean = false,
    val isUploadingLogo: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class CampingAdminViewModel @Inject constructor(
    private val campingService: CampingService,
    private val imageUploader: ImageUploader,
    private val analyticsService: AnalyticsService = NoOpAnalyticsService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CampingAdminUiState())
    val uiState: StateFlow<CampingAdminUiState> = _uiState.asStateFlow()

    fun prepareEditor(campingId: String?, defaultChurch: String? = null) {
        _uiState.update { it.copy(validationErrors = emptyList(), errorMessage = null) }
        if (campingId == null) {
            val base = CampingEditorForm()
            val form = if (!defaultChurch.isNullOrBlank()) {
                base.copy(organizerType = OrganizerType.Church, organizerName = defaultChurch)
            } else base
            _uiState.update { it.copy(form = form, existingCamping = null, isLoadingCamping = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCamping = true) }
            runCatching { campingService.fetchCamping(campingId) }
                .onSuccess { camping ->
                    _uiState.update {
                        it.copy(
                            isLoadingCamping = false,
                            existingCamping = camping,
                            form = CampingEditorForm.from(camping),
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoadingCamping = false, errorMessage = "Could not load camping.")
                    }
                }
        }
    }

    fun updateForm(form: CampingEditorForm) {
        _uiState.update { it.copy(form = form) }
    }

    fun saveEditorForm(campingId: String?, onSaved: (String) -> Unit) {
        val errors = validate(_uiState.value.form)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(validationErrors = errors) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, validationErrors = emptyList(), errorMessage = null) }
            runCatching {
                val camping = buildCamping(
                    campingId = campingId,
                    form = _uiState.value.form,
                    existing = _uiState.value.existingCamping,
                )
                campingService.saveCamping(camping)
            }
                .onSuccess { saved ->
                    _uiState.update { it.copy(isSaving = false, successMessage = "Camping saved.") }
                    onSaved(saved.id)
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = "Could not save camping. Please try again.")
                    }
                }
        }
    }

    fun cancelCamping(id: String, onCancelled: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCancelling = true, errorMessage = null) }
            runCatching { campingService.cancelCamping(id) }
                .onSuccess {
                    analyticsService.cancelCamping(id)
                    _uiState.update { it.copy(isCancelling = false, successMessage = "Camping cancelled.") }
                    onCancelled()
                }
                .onFailure {
                    _uiState.update { it.copy(isCancelling = false, errorMessage = "Could not cancel camping.") }
                }
        }
    }

    fun deleteCamping(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
            runCatching { campingService.deleteCamping(id) }
                .onSuccess {
                    _uiState.update { it.copy(isDeleting = false) }
                    onDeleted()
                }
                .onFailure {
                    _uiState.update { it.copy(isDeleting = false, errorMessage = "Could not delete camping.") }
                }
        }
    }

    fun uploadLogo(bytes: ByteArray, mimeType: String, ext: String, campingId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingLogo = true, errorMessage = null) }
            val resolvedId = campingId ?: "draft-${UUID.randomUUID()}"
            runCatching {
                imageUploader.uploadImage(
                    assetIdPrefix = "campzone/campings/$resolvedId",
                    folder = "campzone/campings",
                    tags = listOf("campzone", "campings", "camping_$resolvedId"),
                    bytes = bytes,
                    mimeType = mimeType,
                    fileExtension = ext,
                )
            }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isUploadingLogo = false,
                            form = it.form.copy(logoUrl = result.secureUrl, logoPublicId = result.publicId),
                            successMessage = "Logo updated.",
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isUploadingLogo = false, errorMessage = "Logo upload failed.") }
                }
        }
    }

    fun removeLogo() {
        _uiState.update { it.copy(form = it.form.copy(logoUrl = null, logoPublicId = null)) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }

    private fun validate(form: CampingEditorForm): List<String> = buildList {
        if (form.title.isBlank()) add("Title is required.")
        if (form.description.isBlank()) add("Description is required.")
        if (form.location.isBlank()) add("Location is required.")
        if (form.organizerType == OrganizerType.Church && form.organizerName.isBlank()) {
            add("Church name is required.")
        }
        if (form.endDate.before(form.startDate)) add("End date must be after start date.")
        val cap = form.participantCapacityText.trim()
        if (cap.isNotEmpty() && (cap.toIntOrNull() ?: 0) <= 0) add("Capacity must be a positive number.")
    }

    private fun buildCamping(campingId: String?, form: CampingEditorForm, existing: Camping?): Camping {
        val id = campingId ?: existing?.id ?: UUID.randomUUID().toString()
        val capacity = form.participantCapacityText.trim().let { t ->
            if (t.isEmpty()) null else t.toIntOrNull()?.takeIf { it > 0 }
        }
        return Camping(
            id = id,
            title = form.title.trim(),
            description = form.description.trim(),
            startDate = form.startDate,
            endDate = form.endDate,
            organizerLevel = form.organizerLevel,
            location = form.location.trim(),
            locationLatitude = form.locationLatitude,
            locationLongitude = form.locationLongitude,
            registrationStatus = form.registrationStatus,
            publicationStatus = existing?.publicationStatus ?: CampingPublicationStatus.Draft,
            participantCapacity = capacity,
            attendees = existing?.attendees.orEmpty(),
            winnerRevealPolicy = existing?.winnerRevealPolicy,
            logoUrl = form.logoUrl?.trim()?.takeUnless { it.isBlank() },
            logoPublicId = form.logoPublicId?.trim()?.takeUnless { it.isBlank() },
            guidelines = existing?.guidelines.orEmpty(),
            registrationFeeCents = CampingEditorForm.feeCents(form.registrationFeeText),
            feeCurrency = form.feeCurrency.trim().takeUnless { it.isBlank() }?.uppercase() ?: "EUR",
            priceItems = form.priceItems,
            agePrices = form.agePrices,
            transportationOptions = form.transportationOptions,
            createdByUid = existing?.createdByUid,
            createdByName = existing?.createdByName,
            createdAt = existing?.createdAt,
            registrationDeadline = form.registrationDueDate,
        )
    }
}
