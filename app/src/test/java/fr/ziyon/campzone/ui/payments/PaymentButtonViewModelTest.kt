package fr.ziyon.campzone.ui.payments

import fr.ziyon.campzone.data.model.PaymentKind
import fr.ziyon.campzone.data.payments.PaymentConfirmation
import fr.ziyon.campzone.data.payments.PaymentRequest
import fr.ziyon.campzone.data.payments.PaymentService
import fr.ziyon.campzone.data.payments.PaymentSheetIntent
import fr.ziyon.campzone.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PaymentButtonViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val request = PaymentRequest(
        kind = PaymentKind.Transportation,
        campingId = "camp-1",
        referenceId = "booking-1",
        amountCents = 2500,
        currency = "eur",
    )

    @Test
    fun payMintsIntentAndIdlesReadyToPresent() {
        val service = FakePaymentService()
        val viewModel = PaymentButtonViewModel(service)

        viewModel.pay(request)

        val state = viewModel.uiState.value
        assertEquals(PaymentButtonPhase.Idle, state.phase)
        assertEquals("pi_booking-1", state.preparedIntent?.paymentIntentId)
        assertTrue(state.isBusy)
        assertEquals(listOf("booking-1"), service.created.map { it.referenceId })
        assertNull(state.errorMessage)
    }

    @Test
    fun confirmAfterSheetCompletionSetsPaid() {
        val service = FakePaymentService()
        val viewModel = PaymentButtonViewModel(service)

        viewModel.pay(request)
        viewModel.confirm(request)

        val state = viewModel.uiState.value
        assertTrue(state.paid)
        assertEquals(PaymentButtonPhase.Idle, state.phase)
        assertFalse(state.isBusy)
        assertEquals(listOf("booking-1"), service.confirmed)

        viewModel.consumePaid()
        assertFalse(viewModel.uiState.value.paid)
    }

    @Test
    fun prepareFailureSurfacesError() {
        val service = FakePaymentService(failPrepare = true)
        val viewModel = PaymentButtonViewModel(service)

        viewModel.pay(request)

        val state = viewModel.uiState.value
        assertEquals(PaymentButtonPhase.Idle, state.phase)
        assertNull(state.preparedIntent)
        assertEquals("intent failed", state.errorMessage)
        assertFalse(state.isBusy)
    }

    @Test
    fun unpaidConfirmationSurfacesErrorAndDoesNotMarkPaid() {
        val service = FakePaymentService(confirmPaid = false)
        val viewModel = PaymentButtonViewModel(service)

        viewModel.pay(request)
        viewModel.confirm(request)

        val state = viewModel.uiState.value
        assertFalse(state.paid)
        assertEquals(PaymentButtonPhase.Idle, state.phase)
        assertTrue(state.errorMessage!!.contains("requires_action"))
    }

    @Test
    fun cancelClearsPreparedIntentWithoutError() {
        val service = FakePaymentService()
        val viewModel = PaymentButtonViewModel(service)

        viewModel.pay(request)
        viewModel.cancel()

        val state = viewModel.uiState.value
        assertNull(state.preparedIntent)
        assertFalse(state.isBusy)
        assertNull(state.errorMessage)
        assertFalse(state.paid)
    }

    @Test
    fun payIsIgnoredWhileBusy() {
        val service = FakePaymentService()
        val viewModel = PaymentButtonViewModel(service)

        viewModel.pay(request)
        // An intent is prepared (busy) - a second tap must not mint another.
        viewModel.pay(request)

        assertEquals(1, service.created.size)
    }

    private class FakePaymentService(
        private val failPrepare: Boolean = false,
        private val confirmPaid: Boolean = true,
    ) : PaymentService {
        val created = mutableListOf<PaymentRequest>()
        val confirmed = mutableListOf<String>()

        override suspend fun createPaymentIntent(request: PaymentRequest): PaymentSheetIntent {
            if (failPrepare) throw IllegalStateException("intent failed")
            created += request
            return PaymentSheetIntent(
                paymentIntentId = "pi_${request.referenceId}",
                paymentIntentClientSecret = "secret_${request.referenceId}",
                ephemeralKeySecret = "ek_${request.referenceId}",
                customerId = "cus_123",
                publishableKey = "pk_test_123",
                amountCents = request.amountCents,
                currency = request.currency,
            )
        }

        override suspend fun confirmPayment(
            paymentIntentId: String,
            kind: PaymentKind,
            campingId: String?,
            referenceId: String,
        ): PaymentConfirmation {
            confirmed += referenceId
            return PaymentConfirmation(
                paid = confirmPaid,
                status = if (confirmPaid) "succeeded" else "requires_action",
                kind = kind,
                campingId = campingId,
                referenceId = referenceId,
            )
        }
    }
}
