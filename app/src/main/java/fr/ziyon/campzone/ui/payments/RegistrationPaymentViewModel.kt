package fr.ziyon.campzone.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.PaymentKind
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.TransportationPaymentStatus
import fr.ziyon.campzone.data.payments.PaymentRequest
import fr.ziyon.campzone.data.payments.PaymentService
import fr.ziyon.campzone.data.payments.PaymentSheetIntent
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegistrationPaymentItem(
    val participantId: String,
    val participantName: String,
    val request: PaymentRequest,
)

data class PreparedRegistrationPayment(
    val item: RegistrationPaymentItem,
    val sheetIntent: PaymentSheetIntent,
    val hasBeenPresented: Boolean = false,
)

data class RegistrationPaymentUiState(
    val isLoading: Boolean = true,
    val campingTitle: String = "",
    val items: List<RegistrationPaymentItem> = emptyList(),
    val preparedPayment: PreparedRegistrationPayment? = null,
    val isPreparingPayment: Boolean = false,
    val isConfirmingPayment: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val currentItem: RegistrationPaymentItem?
        get() = items.firstOrNull()

    val isComplete: Boolean
        get() = !isLoading && items.isEmpty() && !isPreparingPayment && !isConfirmingPayment
}

@HiltViewModel
class RegistrationPaymentViewModel @Inject constructor(
    private val campingService: CampingService,
    private val paymentService: PaymentService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationPaymentUiState())
    val uiState: StateFlow<RegistrationPaymentUiState> = _uiState.asStateFlow()

    private var loadedKey: LoadedKey? = null
    private val completedReferenceIds = mutableSetOf<String>()

    fun load(campingId: String, user: AuthenticatedUser) {
        val key = LoadedKey(campingId, user.uid)
        if (loadedKey == key && !_uiState.value.isLoading) return
        loadedKey = key
        completedReferenceIds.clear()

        viewModelScope.launch {
            _uiState.value = RegistrationPaymentUiState(isLoading = true)
            runCatching { campingService.fetchCamping(campingId) }
                .onSuccess { camping ->
                    _uiState.value = RegistrationPaymentUiState(
                        isLoading = false,
                        campingTitle = camping.title,
                        items = paymentItems(camping, user),
                    )
                }
                .onFailure { error ->
                    loadedKey = null
                    _uiState.value = RegistrationPaymentUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "Payment details could not be loaded.",
                    )
                }
        }
    }

    fun prepareCurrentPayment() {
        val item = _uiState.value.currentItem ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPreparingPayment = true,
                    errorMessage = null,
                    successMessage = null,
                    preparedPayment = null,
                )
            }
            runCatching { paymentService.createPaymentIntent(item.request) }
                .onSuccess { sheetIntent ->
                    _uiState.update {
                        it.copy(
                            isPreparingPayment = false,
                            preparedPayment = PreparedRegistrationPayment(
                                item = item,
                                sheetIntent = sheetIntent,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isPreparingPayment = false,
                            errorMessage = error.message ?: "Payment could not be prepared.",
                        )
                    }
                }
        }
    }

    fun markPaymentSheetPresented() {
        _uiState.update { state ->
            state.copy(
                preparedPayment = state.preparedPayment?.copy(hasBeenPresented = true),
            )
        }
    }

    fun confirmPreparedPayment(user: AuthenticatedUser) {
        val prepared = _uiState.value.preparedPayment ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isConfirmingPayment = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            runCatching {
                val request = prepared.item.request
                val confirmation = paymentService.confirmPayment(
                    paymentIntentId = prepared.sheetIntent.paymentIntentId,
                    kind = request.kind,
                    campingId = request.campingId,
                    referenceId = request.referenceId,
                )
                check(confirmation.paid) {
                    "Payment was not completed. Current status: ${confirmation.status}."
                }
                request
            }.onSuccess { request ->
                completedReferenceIds += request.referenceId
                val camping = campingService.fetchCamping(request.campingId.orEmpty())
                val remainingItems = paymentItems(camping, user)
                _uiState.update {
                    it.copy(
                        isConfirmingPayment = false,
                        campingTitle = camping.title,
                        items = remainingItems,
                        preparedPayment = null,
                        successMessage = if (remainingItems.isEmpty()) {
                            "Payment completed."
                        } else {
                            "Payment completed. Continue with the next participant."
                        },
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isConfirmingPayment = false,
                        errorMessage = error.message ?: "Payment could not be confirmed.",
                    )
                }
            }
        }
    }

    fun cancelPreparedPayment() {
        _uiState.update {
            it.copy(
                preparedPayment = null,
                isPreparingPayment = false,
                isConfirmingPayment = false,
                errorMessage = "Payment was canceled.",
            )
        }
    }

    fun failPreparedPayment(message: String?) {
        _uiState.update {
            it.copy(
                preparedPayment = null,
                isPreparingPayment = false,
                isConfirmingPayment = false,
                errorMessage = message ?: "Payment failed.",
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private fun paymentItems(
        camping: Camping,
        user: AuthenticatedUser,
    ): List<RegistrationPaymentItem> {
        val currency = camping.feeCurrency
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeUnless { it.isBlank() }
            ?: "eur"

        return camping.attendees
            .asSequence()
            .filter { it.belongsTo(user) }
            .filter { it.registrationStatus == RegistrationApprovalStatus.Pending }
            .filter { it.paymentStatus != TransportationPaymentStatus.Paid }
            .filter { it.id !in completedReferenceIds }
            .mapNotNull { attendee ->
                val amountCents = camping.resolvedRegistrationFeeCents(attendee.age)
                if (amountCents <= 0) return@mapNotNull null
                RegistrationPaymentItem(
                    participantId = attendee.id,
                    participantName = attendee.displayName,
                    request = PaymentRequest(
                        kind = PaymentKind.Registration,
                        campingId = camping.id,
                        referenceId = attendee.id,
                        amountCents = amountCents,
                        currency = currency,
                    ),
                )
            }
            .toList()
    }

    private fun CampingAttendee.belongsTo(user: AuthenticatedUser): Boolean =
        id == user.uid || userId == user.uid || guardianId == user.uid

    private data class LoadedKey(
        val campingId: String,
        val userId: String,
    )
}
