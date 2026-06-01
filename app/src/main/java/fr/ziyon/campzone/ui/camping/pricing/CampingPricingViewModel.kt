package fr.ziyon.campzone.ui.camping.pricing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ziyon.campzone.data.auth.AuthenticatedUser
import fr.ziyon.campzone.data.camping.CampingService
import fr.ziyon.campzone.data.model.Camping
import fr.ziyon.campzone.data.model.CampingAttendee
import fr.ziyon.campzone.data.model.CampingPriceItem
import fr.ziyon.campzone.data.model.RegistrationApprovalStatus
import fr.ziyon.campzone.data.model.RegistrationParticipantKind
import fr.ziyon.campzone.data.model.TransportationPaymentStatus
import fr.ziyon.campzone.data.payments.PaymentProof
import fr.ziyon.campzone.data.payments.PaymentProofService
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One unpaid registration fee the signed-in user still owes. */
data class FeesRegistrationRow(
    val attendeeId: String,
    val participantName: String,
    val isSelf: Boolean,
    val amountCents: Int,
    val currency: String,
)

sealed interface CampingPricingUiState {
    data object Loading : CampingPricingUiState

    data class Error(val message: String) : CampingPricingUiState

    data class Loaded(
        val campingId: String,
        val campingTitle: String,
        val registrationFees: List<FeesRegistrationRow>,
        val priceItems: List<CampingPriceItem>,
        val proofs: List<PaymentProof> = emptyList(),
    ) : CampingPricingUiState {
        val isEmpty: Boolean
            get() = registrationFees.isEmpty() && priceItems.isEmpty() && proofs.isEmpty()
    }
}

/**
 * Backs the participant-facing "Fees & Payments" hub. Mirrors the iOS
 * `CampingPricingView` (organizer price items, paid via Stripe / IBAN) and
 * folds in any still-unpaid registration fee the user owes so a single screen
 * covers every charge for the camp. Each payable row settles through
 * [fr.ziyon.campzone.ui.payments.CzPaymentButton]; the backend flips the
 * matching Firestore record, so we just re-fetch after a payment.
 */
@HiltViewModel
class CampingPricingViewModel @Inject constructor(
    private val campingService: CampingService,
    private val proofService: PaymentProofService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CampingPricingUiState>(CampingPricingUiState.Loading)
    val uiState: StateFlow<CampingPricingUiState> = _uiState.asStateFlow()

    private var loadedKey: Pair<String, String>? = null
    private var currentUser: AuthenticatedUser? = null

    fun load(campingId: String, user: AuthenticatedUser) {
        val key = campingId to user.uid
        if (loadedKey == key && _uiState.value !is CampingPricingUiState.Error) return
        loadedKey = key
        currentUser = user
        _uiState.value = CampingPricingUiState.Loading
        fetch(campingId, user, showLoading = false)
    }

    fun retry(campingId: String, user: AuthenticatedUser) {
        loadedKey = null
        load(campingId, user)
    }

    /** Silent re-fetch after a fee/price-item settles so statuses flip. */
    fun reload() {
        val (campingId, _) = loadedKey ?: return
        val user = currentUser ?: return
        fetch(campingId, user, showLoading = false)
    }

    private fun fetch(campingId: String, user: AuthenticatedUser, showLoading: Boolean) {
        if (showLoading) _uiState.value = CampingPricingUiState.Loading
        viewModelScope.launch {
            runCatching {
                val camping = campingService.fetchCamping(campingId)
                val proofs = runCatching { proofService.loadProofs(campingId, user.uid) }
                    .getOrDefault(emptyList())
                camping to proofs
            }
                .onSuccess { (camping, proofs) -> _uiState.value = build(camping, user, proofs) }
                .onFailure { error ->
                    loadedKey = null
                    _uiState.value = CampingPricingUiState.Error(
                        error.message?.takeUnless { it.isBlank() } ?: DEFAULT_ERROR,
                    )
                }
        }
    }

    private fun build(
        camping: Camping,
        user: AuthenticatedUser,
        proofs: List<PaymentProof>,
    ): CampingPricingUiState.Loaded {
        val currency = camping.feeCurrency
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeUnless { it.isBlank() }
            ?: "eur"

        val fees = camping.attendees
            .asSequence()
            .filter { it.belongsTo(user) }
            .filter { it.registrationStatus == RegistrationApprovalStatus.Pending }
            .filter { it.paymentStatus != TransportationPaymentStatus.Paid }
            .mapNotNull { attendee ->
                val amount = camping.resolvedRegistrationFeeCents(attendee.age)
                if (amount <= 0) return@mapNotNull null
                FeesRegistrationRow(
                    attendeeId = attendee.id,
                    participantName = attendee.displayName,
                    isSelf = attendee.participantKind == RegistrationParticipantKind.SelfParticipant,
                    amountCents = amount,
                    currency = currency,
                )
            }
            .toList()

        return CampingPricingUiState.Loaded(
            campingId = camping.id,
            campingTitle = camping.title,
            registrationFees = fees,
            priceItems = camping.priceItems,
            proofs = proofs,
        )
    }

    private fun CampingAttendee.belongsTo(user: AuthenticatedUser): Boolean =
        id == user.uid || userId == user.uid || guardianId == user.uid

    private companion object {
        const val DEFAULT_ERROR = "Fees could not be loaded."
    }
}
