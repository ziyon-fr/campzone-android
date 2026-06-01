package fr.ziyon.campzone.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.payments.PaymentProofService
import fr.ziyon.campzone.data.payments.PaymentRequest
import fr.ziyon.campzone.data.payments.PaymentService
import fr.ziyon.campzone.data.payments.PaymentSheetIntent
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the single-charge PaymentSheet flow currently sits. */
enum class PaymentButtonPhase {
    /** Nothing in flight - the CTA is tappable. */
    Idle,

    /** `POST /payments/intent` is in flight (minting PaymentSheet params). */
    Preparing,

    /** `POST /payments/confirm` is in flight (verifying the charge). */
    Confirming,
}

data class PaymentButtonUiState(
    val phase: PaymentButtonPhase = PaymentButtonPhase.Idle,
    /** Non-null once an intent is minted - the composable presents the sheet. */
    val preparedIntent: PaymentSheetIntent? = null,
    val errorMessage: String? = null,
    /** One-shot flag: set after the backend confirms a paid charge. */
    val paid: Boolean = false,
) {
    /** True for the whole prepare -> present -> confirm flow (CTA disabled). */
    val isBusy: Boolean
        get() = phase != PaymentButtonPhase.Idle || preparedIntent != null
}

/**
 * Drives one reusable Stripe PaymentSheet CTA (see [CzPaymentButton]). Mirrors
 * the iOS `PaymentObserver`: mint intent -> the view presents the native sheet
 * -> verify with the backend, which settles the matching Firestore record
 * (auto-approving paid registrations / flipping booking + price-item status).
 *
 * One instance per payable item: composables scope it with
 * `hiltViewModel(key = ...)` so each row owns an independent flow.
 */
@HiltViewModel
class PaymentButtonViewModel @Inject constructor(
    private val paymentService: PaymentService,
    private val proofService: PaymentProofService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentButtonUiState())
    val uiState: StateFlow<PaymentButtonUiState> = _uiState.asStateFlow()

    /** Step 1: mint the PaymentSheet params. The view presents on success. */
    fun pay(request: PaymentRequest) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(phase = PaymentButtonPhase.Preparing, errorMessage = null, paid = false)
            }
            runCatching { paymentService.createPaymentIntent(request) }
                .onSuccess { intent ->
                    _uiState.update {
                        it.copy(phase = PaymentButtonPhase.Idle, preparedIntent = intent)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            phase = PaymentButtonPhase.Idle,
                            preparedIntent = null,
                            errorMessage = error.message?.takeUnless { msg -> msg.isBlank() }
                                ?: DEFAULT_PREPARE_ERROR,
                        )
                    }
                }
        }
    }

    /** Step 3 (sheet completed): verify with the backend and settle Firestore. */
    fun confirm(request: PaymentRequest) {
        val intent = _uiState.value.preparedIntent ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(phase = PaymentButtonPhase.Confirming, preparedIntent = null) }
            runCatching {
                val confirmation = paymentService.confirmPayment(
                    paymentIntentId = intent.paymentIntentId,
                    request = request,
                )
                check(confirmation.paid) {
                    "Payment was not completed. Current status: ${confirmation.status}."
                }
                // Mixed-kind bundles need a follow-up confirm per extra kind so
                // the backend flips each Firestore sub-collection off the same
                // charge (best-effort — never re-charges, never rolls back).
                request.kindsInLineItems
                    .filter { it != request.kind }
                    .forEach { extraKind ->
                        request.subrequest(extraKind)?.let { sub ->
                            runCatching { paymentService.confirmPayment(intent.paymentIntentId, sub) }
                        }
                    }
                // Persist the receipt (full line-item set) for the PDF/history.
                runCatching { proofService.recordInvoice(intent.paymentIntentId, request) }
            }.onSuccess {
                _uiState.update { it.copy(phase = PaymentButtonPhase.Idle, paid = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        phase = PaymentButtonPhase.Idle,
                        errorMessage = error.message?.takeUnless { msg -> msg.isBlank() }
                            ?: DEFAULT_CONFIRM_ERROR,
                    )
                }
            }
        }
    }

    /** Sheet dismissed without paying - no error, no charge. */
    fun cancel() {
        _uiState.update { it.copy(phase = PaymentButtonPhase.Idle, preparedIntent = null) }
    }

    /** Sheet reported a failure (e.g. declined card). */
    fun fail(message: String?) {
        _uiState.update {
            it.copy(
                phase = PaymentButtonPhase.Idle,
                preparedIntent = null,
                errorMessage = message?.takeUnless { msg -> msg.isBlank() } ?: DEFAULT_CONFIRM_ERROR,
            )
        }
    }

    /** Consume the one-shot paid flag after the view has run `onPaid`. */
    fun consumePaid() {
        _uiState.update { it.copy(paid = false) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private companion object {
        const val DEFAULT_PREPARE_ERROR = "Payment could not be started. Please try again."
        const val DEFAULT_CONFIRM_ERROR = "Payment did not complete. You were not charged."
    }
}
