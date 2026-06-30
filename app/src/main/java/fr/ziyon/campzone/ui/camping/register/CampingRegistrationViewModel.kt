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
import fr.ziyon.campzone.data.model.CampingVehicle
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipant
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.RegistrationSubmission
import fr.ziyon.campzone.data.model.TransportationChoice
import fr.ziyon.campzone.data.model.TransportationMode
import fr.ziyon.campzone.data.model.UserVehicle
import fr.ziyon.campzone.data.model.VehicleStatus
import fr.ziyon.campzone.data.model.VehicleTokenFactory
import fr.ziyon.campzone.data.notifications.FakeNotificationSettingsService
import fr.ziyon.campzone.data.notifications.NotificationSettingsService
import fr.ziyon.campzone.data.vehicle.UserVehicleService
import fr.ziyon.campzone.data.vehicle.VehicleService
import java.util.Locale
import fr.ziyon.campzone.data.notifications.RegistrationNotificationDispatcher
import fr.ziyon.campzone.data.notifications.RegistrationNotificationRequest
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RegistrationStep {
    Who,
    Transport,
    Review,
}

data class InlineVehicleDraft(
    val enabled: Boolean = false,
    val selectedSavedVehicleId: String? = null,
    val plateNumber: String = "",
    val brand: String = "",
    val model: String = "",
    val color: String = "",
    val totalSeats: Int = 5,
    val peopleInCar: Int = 1,
    val hasAvailableSeats: Boolean = true,
    val notes: String = "",
) {
    val normalizedPlate: String
        get() = plateNumber.trim().uppercase(Locale.ROOT)

    val plateIsBlank: Boolean
        get() = normalizedPlate.isBlank()
}

data class CampingRegistrationUiState(
    val isLoading: Boolean = true,
    val camping: Camping? = null,
    val participants: List<RegistrationParticipant> = emptyList(),
    val selectedParticipantIds: Set<String> = emptySet(),
    val transportationOptionIds: Map<String, String?> = emptyMap(),
    val step: RegistrationStep = RegistrationStep.Who,
    val savedVehicles: List<UserVehicle> = emptyList(),
    val inlineVehicle: InlineVehicleDraft = InlineVehicleDraft(),
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
        get() = selectedParticipants.isNotEmpty() && !isSubmitting && !inlineVehicleNeedsPlate

    val canProceed: Boolean
        get() = when (step) {
            RegistrationStep.Who -> selectedParticipants.isNotEmpty()
            RegistrationStep.Transport -> !inlineVehicleNeedsPlate
            RegistrationStep.Review -> canSubmit
        }

    val canGoBack: Boolean
        get() = step != RegistrationStep.Who && !isSubmitting

    val selfParticipantForInlineVehicle: RegistrationParticipant?
        get() = selectedParticipants.firstOrNull {
            it.kind == RegistrationParticipantKind.SelfParticipant
        }

    val shouldOfferInlineVehicle: Boolean
        get() {
            val camping = camping ?: return false
            val self = selfParticipantForInlineVehicle ?: return false
            if (!camping.usesTransportationOptions) return true
            val option = camping.transportationOption(transportationOptionIds[self.id])
            return option == null || !option.issuesTicket
        }

    val wantsInlineVehicle: Boolean
        get() = shouldOfferInlineVehicle && inlineVehicle.enabled

    val inlineVehicleNeedsPlate: Boolean
        get() = wantsInlineVehicle && inlineVehicle.plateIsBlank

    fun existingRegistration(participant: RegistrationParticipant) =
        camping?.attendees?.firstOrNull { it.id == participant.id }
}

@HiltViewModel
class CampingRegistrationViewModel @Inject constructor(
    private val campingService: CampingService,
    private val familyRepository: FamilyRepository,
    private val vehicleService: VehicleService,
    private val userVehicleService: UserVehicleService,
    private val notificationDispatcher: RegistrationNotificationDispatcher,
    private val notificationSettingsService: NotificationSettingsService = FakeNotificationSettingsService(),
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
                val savedVehicles = runCatching { userVehicleService.loadVehicles(user.uid) }
                    .getOrElse { emptyList() }
                Triple(camping, participants, savedVehicles)
            }.onSuccess { (camping, participants, savedVehicles) ->
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
                    savedVehicles = savedVehicles,
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
            state.copy(selectedParticipantIds = selected).sanitizeInlineVehicle()
        }
    }

    fun selectTransportationOption(participantId: String, optionId: String?) {
        _uiState.update {
            it.copy(transportationOptionIds = it.transportationOptionIds + (participantId to optionId))
                .sanitizeInlineVehicle()
        }
    }

    fun goBack() {
        _uiState.update { state ->
            if (!state.canGoBack) return@update state
            val previous = when (state.step) {
                RegistrationStep.Who -> RegistrationStep.Who
                RegistrationStep.Transport -> RegistrationStep.Who
                RegistrationStep.Review -> RegistrationStep.Transport
            }
            state.copy(step = previous, errorMessage = null)
        }
    }

    fun goNext() {
        _uiState.update { state ->
            when (state.step) {
                RegistrationStep.Who -> {
                    if (state.selectedParticipants.isEmpty()) {
                        state.copy(errorMessage = "Select at least one participant.")
                    } else {
                        state.copy(step = RegistrationStep.Transport, errorMessage = null)
                            .sanitizeInlineVehicle()
                    }
                }

                RegistrationStep.Transport -> {
                    if (state.inlineVehicleNeedsPlate) {
                        state.copy(errorMessage = "Enter your car's plate, or turn off \"I'm driving my own car\".")
                    } else {
                        state.copy(step = RegistrationStep.Review, errorMessage = null)
                            .sanitizeInlineVehicle()
                    }
                }

                RegistrationStep.Review -> state
            }
        }
    }

    fun toggleInlineVehicle(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(inlineVehicle = state.inlineVehicle.copy(enabled = enabled))
                .sanitizeInlineVehicle()
        }
    }

    fun updateInlineVehiclePlate(value: String) {
        _uiState.update {
            it.copy(inlineVehicle = it.inlineVehicle.copy(plateNumber = value.uppercase(Locale.ROOT)))
        }
    }

    fun updateInlineVehicleBrand(value: String) {
        _uiState.update { it.copy(inlineVehicle = it.inlineVehicle.copy(brand = value)) }
    }

    fun updateInlineVehicleModel(value: String) {
        _uiState.update { it.copy(inlineVehicle = it.inlineVehicle.copy(model = value)) }
    }

    fun updateInlineVehicleColor(value: String) {
        _uiState.update { it.copy(inlineVehicle = it.inlineVehicle.copy(color = value)) }
    }

    fun updateInlineVehicleTotalSeats(value: Int) {
        _uiState.update { state ->
            val seats = value.coerceIn(CampingVehicle.MinSeats, CampingVehicle.MaxSeats)
            state.copy(
                inlineVehicle = state.inlineVehicle.copy(
                    totalSeats = seats,
                    peopleInCar = state.inlineVehicle.peopleInCar.coerceIn(1, seats),
                ),
            )
        }
    }

    fun updateInlineVehiclePeopleInCar(value: Int) {
        _uiState.update { state ->
            state.copy(
                inlineVehicle = state.inlineVehicle.copy(
                    peopleInCar = value.coerceIn(1, state.inlineVehicle.totalSeats),
                ),
            )
        }
    }

    fun updateInlineVehicleHasSeats(value: Boolean) {
        _uiState.update { it.copy(inlineVehicle = it.inlineVehicle.copy(hasAvailableSeats = value)) }
    }

    fun updateInlineVehicleNotes(value: String) {
        _uiState.update { it.copy(inlineVehicle = it.inlineVehicle.copy(notes = value)) }
    }

    fun applySavedVehicle(vehicle: UserVehicle) {
        _uiState.update { state ->
            state.copy(
                inlineVehicle = state.inlineVehicle.copy(
                    enabled = true,
                    selectedSavedVehicleId = vehicle.id,
                    plateNumber = vehicle.plateNumber.uppercase(Locale.ROOT),
                    brand = vehicle.brand.orEmpty(),
                    model = vehicle.model.orEmpty(),
                    color = vehicle.color.orEmpty(),
                    totalSeats = vehicle.clampedTotalSeats,
                    peopleInCar = state.inlineVehicle.peopleInCar.coerceIn(1, vehicle.clampedTotalSeats),
                ),
            )
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
        if (state.inlineVehicleNeedsPlate) {
            _uiState.update { it.copy(errorMessage = "Enter your car's plate, or turn off \"I'm driving my own car\".") }
            return
        }
        val inlineVehicle = state.inlineVehicle
        val shouldCreateVehicle = state.wantsInlineVehicle

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
                val vehicleCreationResult = if (shouldCreateVehicle) {
                    runCatching {
                        createDriverVehicle(
                            camping = updatedCamping,
                            user = user,
                            draft = inlineVehicle,
                        )
                    }
                } else {
                    null
                }
                analyticsService.registerForCamping(updatedCamping.id)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        camping = updatedCamping,
                        inlineVehicle = if (vehicleCreationResult?.isSuccess == true) InlineVehicleDraft() else it.inlineVehicle,
                        successMessage = if (submissions.size == 1) {
                            "Registration sent for approval."
                        } else {
                            "Registrations sent for approval."
                        },
                        errorMessage = vehicleCreationResult?.exceptionOrNull()?.message,
                    )
                }
                notifyLeadership(
                    camping = updatedCamping,
                    submissions = submissions,
                    requestedBy = user,
                )
                syncRegistrationNotificationChannel(updatedCamping.id, user)
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

    private suspend fun syncRegistrationNotificationChannel(
        campingId: String,
        user: AuthenticatedUser,
    ) {
        runCatching {
            val settings = notificationSettingsService.load(user.uid, user.role)
            val subscribed = (settings.subscribedCampingIds + campingId)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
            notificationSettingsService.save(
                settings.copy(subscribedCampingIds = subscribed),
                uid = user.uid,
                role = user.role,
            )
        }
    }

    private suspend fun createDriverVehicle(
        camping: Camping,
        user: AuthenticatedUser,
        draft: InlineVehicleDraft,
    ): CampingVehicle {
        val totalSeats = draft.totalSeats.coerceIn(CampingVehicle.MinSeats, CampingVehicle.MaxSeats)
        val occupiedSeats = draft.peopleInCar.coerceIn(1, totalSeats)
        val vehicle = CampingVehicle(
            campingId = camping.id,
            ownerUserId = user.uid,
            userVehicleId = draft.selectedSavedVehicleId,
            driverUserId = user.uid,
            driverRegistrationId = user.uid,
            driverName = user.preferredDisplayName,
            driverPhotoUrl = user.photoUrl,
            plateNumber = draft.normalizedPlate,
            brand = draft.brand.clean(),
            model = draft.model.clean(),
            color = draft.color.clean(),
            totalSeats = totalSeats,
            occupiedSeats = occupiedSeats,
            hasAvailableSeats = draft.hasAvailableSeats && occupiedSeats < totalSeats,
            passengerRegistrationIds = emptyList(),
            passengerNames = emptyList(),
            pendingPassengerRegistrationIds = emptyList(),
            pendingPassengerNames = emptyList(),
            qrToken = VehicleTokenFactory.makeToken(),
            invitationCode = VehicleTokenFactory.makeInvitationCode(),
            status = VehicleStatus.Pending,
            notes = draft.notes.clean(),
        )
        val created = vehicleService.createVehicle(vehicle)
        campingService.updateRegistrationTransport(
            campingId = camping.id,
            attendeeId = user.uid,
            transportationMode = TransportationMode.OwnCar,
            vehicleId = created.id,
            isDriver = true,
            needsTransportHelp = false,
            notes = draft.notes.clean(),
        )
        if (draft.selectedSavedVehicleId == null) {
            val saved = UserVehicle(
                ownerUserId = user.uid,
                plateNumber = draft.normalizedPlate,
                brand = draft.brand.clean(),
                model = draft.model.clean(),
                color = draft.color.clean(),
                defaultTotalSeats = totalSeats,
            )
            runCatching { userVehicleService.saveVehicle(saved) }
        }
        return created
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

private fun CampingRegistrationUiState.sanitizeInlineVehicle(): CampingRegistrationUiState =
    if (shouldOfferInlineVehicle) {
        this
    } else {
        copy(inlineVehicle = inlineVehicle.copy(enabled = false))
    }

private fun String.clean(): String? =
    trim().takeUnless { it.isBlank() }
