package fr.ziyon.campzone.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.i18n.StringProvider
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.PaymentKind
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.TransportationBooking
import fr.ziyon.campzone.data.model.TransportationPaymentStatus
import fr.ziyon.campzone.data.payments.PaymentLineItem
import fr.ziyon.campzone.data.payments.PaymentProofService
import fr.ziyon.campzone.data.payments.PaymentRequest
import fr.ziyon.campzone.data.payments.PaymentService
import fr.ziyon.campzone.data.payments.PaymentSheetIntent
import fr.ziyon.campzone.data.transportation.TransportationService
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
    private val transportationService: TransportationService,
    private val proofService: PaymentProofService,
    private val stringProvider: StringProvider,
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
            runCatching {
                val camping = campingService.fetchCamping(campingId)
                camping to buildItems(camping, user)
            }
                .onSuccess { (camping, items) ->
                    _uiState.value = RegistrationPaymentUiState(
                        isLoading = false,
                        campingTitle = camping.title,
                        items = items,
                    )
                }
                .onFailure { error ->
                    loadedKey = null
                    _uiState.value = RegistrationPaymentUiState(
                        isLoading = false,
                        errorMessage = error.message ?: stringProvider.get(R.string.payment_details_load_error),
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
                            errorMessage = error.message ?: stringProvider.get(R.string.payment_prepare_error),
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
                val paymentIntentId = prepared.sheetIntent.paymentIntentId
                val confirmation = paymentService.confirmPayment(
                    paymentIntentId = paymentIntentId,
                    request = request,
                )
                check(confirmation.paid) {
                    stringProvider.get(R.string.payment_not_completed_status, confirmation.status)
                }
                // Bundle: flip the transport booking (and any other extra kind)
                // off the same charge, then persist the receipt — best-effort.
                request.kindsInLineItems
                    .filter { it != request.kind }
                    .forEach { extraKind ->
                        request.subrequest(extraKind)?.let { sub ->
                            runCatching { paymentService.confirmPayment(paymentIntentId, sub) }
                        }
                    }
                runCatching { proofService.recordInvoice(paymentIntentId, request) }
                request
            }.onSuccess { request ->
                completedReferenceIds += request.referenceId
                val camping = campingService.fetchCamping(request.campingId.orEmpty())
                val remainingItems = buildItems(camping, user)
                _uiState.update {
                    it.copy(
                        isConfirmingPayment = false,
                        campingTitle = camping.title,
                        items = remainingItems,
                        preparedPayment = null,
                        successMessage = if (remainingItems.isEmpty()) {
                            stringProvider.get(R.string.payment_completed)
                        } else {
                            stringProvider.get(R.string.payment_completed_next)
                        },
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isConfirmingPayment = false,
                        errorMessage = error.message ?: stringProvider.get(R.string.payment_confirm_error),
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
                errorMessage = stringProvider.get(R.string.payment_canceled),
            )
        }
    }

    fun failPreparedPayment(message: String?) {
        _uiState.update {
            it.copy(
                preparedPayment = null,
                isPreparingPayment = false,
                isConfirmingPayment = false,
                errorMessage = message ?: stringProvider.get(R.string.payment_failed),
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private suspend fun buildItems(
        camping: Camping,
        user: AuthenticatedUser,
    ): List<RegistrationPaymentItem> {
        val bookings = runCatching {
            transportationService.loadUserBookings(camping.id, user.uid)
        }.getOrDefault(emptyList())
        return paymentItems(camping, user, bookings)
    }

    private fun paymentItems(
        camping: Camping,
        user: AuthenticatedUser,
        bookings: List<TransportationBooking>,
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
                val registrationFee = camping.resolvedRegistrationFeeCents(attendee.age)
                if (registrationFee <= 0) return@mapNotNull null

                val lineItems = mutableListOf(
                    PaymentLineItem(
                        referenceId = attendee.id,
                        title = stringProvider.get(R.string.payment_registration_line_item, attendee.displayName),
                        amountCents = registrationFee,
                        kind = PaymentKind.Registration,
                    ),
                )
                var total = registrationFee

                // Mixed bundle: fold in an unpaid bus fare for this participant
                // so registration + transport settle in one Stripe charge.
                val booking = bookings.firstOrNull {
                    it.participantId == attendee.id &&
                        it.paymentStatus == TransportationPaymentStatus.Unpaid
                }
                val fareCents = booking
                    ?.let { camping.transportationOption(it.transportationOptionId)?.feeCents }
                    ?: 0
                if (booking != null && fareCents > 0) {
                    lineItems += PaymentLineItem(
                        referenceId = booking.id,
                        title = stringProvider.get(R.string.payment_bus_fare_line_item, attendee.displayName),
                        amountCents = fareCents,
                        kind = PaymentKind.Transportation,
                    )
                    total += fareCents
                }

                RegistrationPaymentItem(
                    participantId = attendee.id,
                    participantName = attendee.displayName,
                    request = PaymentRequest(
                        kind = PaymentKind.Registration,
                        campingId = camping.id,
                        referenceId = attendee.id,
                        amountCents = total,
                        currency = currency,
                        summary = "${attendee.displayName} · ${camping.title}",
                        lineItems = lineItems,
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
