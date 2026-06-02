package fr.ziyon.campzone.ui.camping.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.core.permissions.AppPermission
import fr.ziyon.campzone.core.permissions.AppPermissionEvaluator
import fr.ziyon.campzone.core.permissions.PermissionUser
import fr.ziyon.campzone.data.analytics.AnalyticsService
import fr.ziyon.campzone.data.analytics.NoOpAnalyticsService
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.family.FamilyRepository
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingTransportationOption
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipant
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.RegistrationSubmission
import fr.ziyon.campzone.data.model.TransportationChoice
import fr.ziyon.campzone.data.notifications.RegistrationNotificationDispatcher
import fr.ziyon.campzone.data.notifications.RegistrationNotificationRequest
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CampingRegistrationUiState(
    val isLoading: Boolean = true,
    val camping: Camping? = null,
    val participants: List<RegistrationParticipant> = emptyList(),
    val selectedParticipantIds: Set<String> = emptySet(),
    val transportationOptionIds: Map<String, String?> = emptyMap(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val selectedParticipants: List<RegistrationParticipant>
        get() {
            val existingIds = camping?.attendees.orEmpty().map { it.id }.toSet()
            return participants.filter { it.id in selectedParticipantIds && it.id !in existingIds }
        }

    val canSubmit: Boolean
        get() = selectedParticipants.isNotEmpty() && !isSubmitting

    fun existingRegistration(participant: RegistrationParticipant) =
        camping?.attendees?.firstOrNull { it.id == participant.id }
}

@HiltViewModel
class CampingRegistrationViewModel @Inject constructor(
    private val campingService: CampingService,
    private val familyRepository: FamilyRepository,
    private val notificationDispatcher: RegistrationNotificationDispatcher,
    private val analyticsService: AnalyticsService = NoOpAnalyticsService,
) : ViewModel() {

    private val permissions = AppPermissionEvaluator()
    private val _uiState = MutableStateFlow(CampingRegistrationUiState())
    val uiState: StateFlow<CampingRegistrationUiState> = _uiState.asStateFlow()

    private var loadedKey: LoadedKey? = null

    fun load(campingId: String, user: AuthenticatedUser) {
        val key = LoadedKey(campingId, user.uid, user.role.rawValue, user.church)
        if (loadedKey == key && !_uiState.value.isLoading) return
        loadedKey = key

        viewModelScope.launch {
            _uiState.value = CampingRegistrationUiState(isLoading = true)
            runCatching {
                val camping = campingService.fetchCamping(campingId)
                val participants = participantOptions(user)
                camping to participants
            }.onSuccess { (camping, participants) ->
                val selected = seedSelection(
                    participants = participants,
                    camping = camping,
                    user = user,
                )
                _uiState.value = CampingRegistrationUiState(
                    isLoading = false,
                    camping = camping,
                    participants = participants,
                    selectedParticipantIds = selected,
                )
            }.onFailure { error ->
                loadedKey = null
                _uiState.value = CampingRegistrationUiState(
                    isLoading = false,
                    errorMessage = error.message ?: "Camping could not be loaded.",
                )
            }
        }
    }

    fun toggleParticipant(participantId: String) {
        _uiState.update { state ->
            val participant = state.participants.firstOrNull { it.id == participantId }
                ?: return@update state
            if (state.existingRegistration(participant) != null) return@update state
            val selected = state.selectedParticipantIds.toMutableSet()
            if (!selected.add(participantId)) {
                selected.remove(participantId)
            }
            state.copy(selectedParticipantIds = selected)
        }
    }

    fun selectTransportationOption(participantId: String, optionId: String?) {
        _uiState.update {
            it.copy(transportationOptionIds = it.transportationOptionIds + (participantId to optionId))
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun submit(
        user: AuthenticatedUser,
        onFinished: (requiresPayment: Boolean) -> Unit,
    ) {
        val state = _uiState.value
        val camping = state.camping ?: return
        val selected = state.selectedParticipants
        if (selected.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Select at least one participant.") }
            return
        }
        if (selected.any { it.kind == RegistrationParticipantKind.Child && it.guardianConsentAt == null }) {
            _uiState.update { it.copy(errorMessage = "Every child registration needs guardian consent.") }
            return
        }

        val submissions = selected.map { participant ->
            submission(
                participant = participant,
                camping = camping,
                state = state,
            )
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, successMessage = null) }
            runCatching {
                campingService.submitRegistrations(
                    submissions = submissions,
                    campingId = camping.id,
                    user = user,
                )
            }.onSuccess { updatedCamping ->
                analyticsService.registerForCamping(updatedCamping.id)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        camping = updatedCamping,
                        successMessage = if (submissions.size == 1) {
                            "Registration sent for approval."
                        } else {
                            "Registrations sent for approval."
                        },
                    )
                }
                notifyLeadership(
                    camping = updatedCamping,
                    submissions = submissions,
                    requestedBy = user,
                )
                onFinished(updatedCamping.requiresPendingRegistrationPayment(selected))
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = error.message ?: "Could not submit registration.",
                    )
                }
            }
        }
    }

    private suspend fun participantOptions(user: AuthenticatedUser): List<RegistrationParticipant> {
        val self = RegistrationParticipant.from(user)
        val permissionUser = PermissionUser(role = user.role, userId = user.uid, church = user.church)
        if (!permissions.can(permissionUser, AppPermission.ManageFamilyRegistrations)) {
            return listOf(self)
        }
        return listOf(self) + familyRepository.loadChildren(user.uid).map(RegistrationParticipant::from)
    }

    private fun seedSelection(
        participants: List<RegistrationParticipant>,
        camping: Camping,
        user: AuthenticatedUser,
    ): Set<String> {
        val self = participants.firstOrNull { it.id == user.uid } ?: return emptySet()
        return if (camping.attendees.none { it.id == self.id }) setOf(self.id) else emptySet()
    }

    private fun submission(
        participant: RegistrationParticipant,
        camping: Camping,
        state: CampingRegistrationUiState,
    ): RegistrationSubmission {
        if (camping.usesTransportationOptions) {
            val option = camping.transportationOption(state.transportationOptionIds[participant.id])
            return RegistrationSubmission(
                participant = participant,
                transportationChoice = if (option?.issuesTicket == true) {
                    TransportationChoice.ProvidedBus
                } else {
                    TransportationChoice.OwnCar
                },
                transportationOptionId = option?.id,
                transportationOptionName = option?.resolvedName,
            )
        }
        return RegistrationSubmission(
            participant = participant,
            transportationChoice = TransportationChoice.OwnCar,
        )
    }

    private fun notifyLeadership(
        camping: Camping,
        submissions: List<RegistrationSubmission>,
        requestedBy: AuthenticatedUser,
    ) {
        val participantName = submissions.firstOrNull()?.participant?.displayName
            ?: requestedBy.preferredDisplayName
        // Self-only registration shouldn't echo the user's own name as the
        // participant; family registrations still name the participant(s).
        val isSelfOnly = submissions.size == 1 &&
            submissions.first().participant.kind == RegistrationParticipantKind.SelfParticipant
        val request = RegistrationNotificationRequest(
            campingId = camping.id,
            campingTitle = camping.title,
            participantName = participantName,
            requestedByName = requestedBy.preferredDisplayName,
            participantCount = submissions.size,
            selfRegistration = isSelfOnly,
        )
        viewModelScope.launch {
            runCatching { notificationDispatcher.dispatchRegistrationRequest(request) }
        }
    }

    private fun Camping.requiresPendingRegistrationPayment(
        participants: List<RegistrationParticipant>,
    ): Boolean =
        participants.any { participant ->
            requiresRegistrationPayment(participant) &&
                attendees.firstOrNull { it.id == participant.id }?.registrationStatus ==
                RegistrationApprovalStatus.Pending
        }

    private data class LoadedKey(
        val campingId: String,
        val userId: String,
        val role: String,
        val church: String,
    )
}
